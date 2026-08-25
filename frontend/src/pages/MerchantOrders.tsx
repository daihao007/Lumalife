import { useEffect, useState } from "react";
import { TicketCheck } from "lucide-react";
import { api } from "../api";
import type { Order, Review } from "../types";
import { money, statusLabel } from "../utils";

export default function MerchantOrders({ user, orders, reload, setMessage }: { user: any; orders: Order[]; reload: () => Promise<void>; setMessage: (msg: string) => void }) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [code, setCode] = useState("");

  useEffect(() => {
    api<Review[]>("/api/v1/merchant-admin/reviews").then(setReviews).catch(() => {});
  }, [user.id]);

  function reviewsFor(orderId: number) {
    return reviews.filter(r => r.orderId === orderId);
  }

  async function next(order: Order) {
    const map: Record<string, string> = { PAID: "ACCEPTED", ACCEPTED: "DELIVERING" };
    await api(`/api/v1/merchant-admin/orders/${order.id}/transition`, { method: "POST", body: JSON.stringify({ next: map[order.status] }) });
    setMessage("订单状态已更新");
    await reload();
    setReviews(await api<Review[]>("/api/v1/merchant-admin/reviews"));
  }

  async function verify() {
    await api("/api/v1/merchant-admin/coupons/verify", { method: "POST", body: JSON.stringify({ code }) });
    setMessage("券码核销成功");
    setCode("");
    await reload();
  }

  return <>
    <section className="fulfillment-section">
      <h3>履约订单</h3>
      {orders.map((o: Order) => {
        const orderReviews = reviewsFor(o.id);
        return <div className="fulfillment-order" key={o.id}>
          <div className="line">
            <span>#{o.id} {statusLabel(o.status)}<small>{money(o.totalCent)} · {o.lines.map(l => `${l.name} x${l.quantity}`).join("，")}</small></span>
            {["PAID", "ACCEPTED"].includes(o.status) && <button onClick={() => next(o)}>下一步</button>}
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
        <input value={code} onChange={e => setCode(e.target.value)} placeholder="输入 12 位券码" />
        <button className="primary" onClick={verify}><TicketCheck /> 核销</button>
      </div>
    </section>
  </>;
}
