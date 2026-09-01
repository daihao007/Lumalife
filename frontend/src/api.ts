type GatewayEnvelope<T> = {
  code?: number;
  message?: string;
  data?: T;
  requestId?: string;
  reason?: string;
  details?: unknown;
};

export class GatewayApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: number,
    readonly requestId?: string,
    readonly reason?: string,
    readonly details?: unknown
  ) {
    super(message);
    this.name = "GatewayApiError";
  }
}

const gatewayBaseUrl = (import.meta.env.VITE_GATEWAY_BASE_URL || "").replace(/\/$/, "");

function requestId() {
  return crypto.randomUUID?.() || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function gatewayUrl(path: string) {
  if (!path.startsWith("/api/v1/")) {
    throw new Error(`冻结契约仅允许前端访问网关 /api/v1/**：${path}`);
  }
  return `${gatewayBaseUrl}${path}`;
}

async function readEnvelope<T>(response: Response): Promise<GatewayEnvelope<T> | null> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as GatewayEnvelope<T>;
  } catch {
    return null;
  }
}

/** Requests only the externally compatible gateway surface; internal service routes are intentionally unreachable from the UI. */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = localStorage.getItem("lumalife-token");
  if (token && !headers.has("Authorization")) headers.set("Authorization", `Bearer ${token}`);
  if (!headers.has("X-Request-Id")) headers.set("X-Request-Id", requestId());
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  let response: Response;
  try {
    response = await fetch(gatewayUrl(path), { ...options, headers });
  } catch (error) {
    throw new GatewayApiError(error instanceof Error ? `网关连接失败：${error.message}` : "网关连接失败", 0);
  }

  const envelope = await readEnvelope<T>(response);
  const responseRequestId = envelope?.requestId || response.headers.get("X-Request-Id") || undefined;
  const successfulEnvelope = envelope && (envelope.code === 200 || envelope.code === response.status);
  if (response.ok && successfulEnvelope) return envelope.data as T;

  const message = envelope?.message
    || (response.status === 413 ? "请求内容过大，请压缩头像后重试" : `网关请求失败（HTTP ${response.status}）`);
  throw new GatewayApiError(message, response.status, envelope?.code, responseRequestId, envelope?.reason, envelope?.details);
}
