export const money = (cent: number) => `¥${(cent / 100).toFixed(2)}`;

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
