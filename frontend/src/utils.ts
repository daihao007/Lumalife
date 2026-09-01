export const money = (cent: number) => `¥${(cent / 100).toFixed(2)}`;

type RequestIdCrypto = {
  randomUUID?: () => string;
  getRandomValues?: (array: Uint8Array) => Uint8Array;
};

let requestIdSequence = 0;

/**
 * Payment request IDs only need to be unique and stable for retries; they are
 * not authentication secrets. Public HTTP deployments may not expose Web
 * Crypto at all, so keep a non-crypto fallback instead of failing before the
 * payment request reaches the API.
 */
export function createPaymentRequestId(
  cryptoApi: RequestIdCrypto | null = typeof globalThis.crypto === "undefined" ? null : globalThis.crypto
) {
  if (typeof cryptoApi?.randomUUID === "function") return cryptoApi.randomUUID();

  if (typeof cryptoApi?.getRandomValues === "function") {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  requestIdSequence = (requestIdSequence + 1) % Number.MAX_SAFE_INTEGER;
  return `pay-${Date.now().toString(36)}-${requestIdSequence.toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

export const statusLabel = (status: string) => ({
  PENDING_PAYMENT: "待支付",
  PAID: "已支付",
  ACCEPTED: "商家已接单",
  DELIVERING: "配送中",
  RECEIVED: "已收货",
  COMPLETED: "已完成",
  USED: "已使用",
  EXPIRED: "已过期",
  CANCELLED: "已取消"
}[status] || status);
