export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("lumalife-token");
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });
  const responseText = await response.text();
  let json: { code?: number; message?: string; data?: T };
  try {
    json = responseText ? JSON.parse(responseText) : {};
  } catch {
    const message = response.status === 413
      ? "请求内容过大，请压缩头像后重试"
      : `服务返回了非 JSON 响应（HTTP ${response.status}）`;
    throw new Error(message);
  }
  if (!response.ok) throw new Error(json.message || `请求失败（HTTP ${response.status}）`);
  if (json.code !== 200) throw new Error(json.message);
  return json.data as T;
}
