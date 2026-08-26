import { useEffect, useRef, useState } from "react";
import { TicketCheck } from "lucide-react";
import { api } from "../api";
import type { Order, Review } from "../types";
import { money, statusLabel } from "../utils";

export default function MerchantOrders({ user, orders, reload, setMessage }: { user: any; orders: Order[]; reload: () => Promise<void>; setMessage: (msg: string) => void }) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [code, setCode] = useState("");
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const actionLock = useRef(false);

  useEffect(() => {
    api<Review[]>("/api/v1/merchant-admin/reviews").then(setReviews).catch(error => {
      setMessage(error instanceof Error ? error.message : "评价加载失败");
    });
  }, [user.id, setMessage]);

  function reviewsFor(orderId: number) {
    return reviews.filter(r => r.orderId === orderId);
  }

  async function next(order: Order) {
    if (actionLock.current) return;
    const map: Record<string, string> = { PAID: "ACCEPTED", ACCEPTED: "DELIVERING", DELIVERING: "COMPLETED" };
    const nextStatus = map[order.status];
    if (!nextStatus) return;
    actionLock.current = true;
    setPendingAction(`transition-${order.id}`);
    try {
      await api(`/api/v1/merchant-admin/orders/${order.id}/transition`, { method: "POST", body: JSON.stringify({ next: nextStatus }) });
      setMessage("订单状态已更新");
      await reload();
      setReviews(await api<Review[]>("/api/v1/merchant-admin/reviews"));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "订单状态更新失败，请重试");
    } finally {
      actionLock.current = false;
      setPendingAction(null);
    }
  }

  async function verify() {
    const normalizedCode = code.trim();
    if (!/^\d{12}$/.test(normalizedCode)) {
      setMessage("请输入 12 位数字券码");
      return;
    }
    if (actionLock.current) return;
    actionLock.current = true;
    setPendingAction("verify");
    try {
      await api("/api/v1/merchant-admin/coupons/verify", { method: "POST", body: JSON.stringify({ code: normalizedCode }) });
      setMessage("券码核销成功");
      setCode("");
      await reload();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "券码核销失败，请重试");
    } finally {
      actionLock.current = false;
      setPendingAction(null);
    }
  }

  return <>
    <section className="fulfillment-section">
      <h3>履约订单</h3>
      {orders.map((o: Order) => {
        const orderReviews = reviewsFor(o.id);
        return <div className="fulfillment-order" key={o.id}>
          <div className="line">
            <span>#{o.id} {statusLabel(o.status)}<small>{money(o.totalCent)} · {o.lines.map(l => `${l.name} x${l.quantity}`).join("，")}</small></span>
            {o.type === "DELIVERY" && ["PAID", "ACCEPTED", "DELIVERING"].includes(o.status) && <button data-testid={`transition-order-${o.id}`} disabled={pendingAction !== null} onClick={() => next(o)}>{pendingAction === `transition-${o.id}` ? "处理中…" : o.status === "PAID" ? "接单" : o.status === "ACCEPTED" ? "开始配送" : "完成订单"}</button>}
          </div>
          <div className={orderReviews.length ? "merchant-review filled" : "merchant-review"}>
            {orderReviews.length ? orderReviews.map(review => <div key={review.id}>
              <b>{review.userName} · {review.score} 分</b>
              <small>口味 {review.tasteScore} · 服务 {review.serviceScore} · {new Date(review.createdAt).toLocaleString()}</small>
              <p>{review.content}</p>
            </div>) : <span>{o.reviewed ? "评价加载中" : "用户暂未评价"}</span>}
          </div>
        </div>;
      })}
      {!orders.length && <p className="hint">暂无订单</p>}
    </section>

    <section>
      <h3>团购核销</h3>
      <div className="chat-input">
        <input aria-label="团购券码" inputMode="numeric" maxLength={12} value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ""))} placeholder="输入 12 位券码" />
        <button className="primary" data-testid="verify-coupon" disabled={pendingAction !== null || code.length !== 12} onClick={verify}><TicketCheck /> {pendingAction === "verify" ? "核销中…" : "核销"}</button>
      </div>
    </section>
  </>;
}
