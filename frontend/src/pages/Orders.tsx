import { useState } from "react";
import { CreditCard } from "lucide-react";
import { api } from "../api";
import type { Order } from "../types";
import { money, statusLabel } from "../utils";
import StatusTimeline from "../components/StatusTimeline";

export default function Orders({ user, orders, reload, pay, cancelOrder, receiveOrder, setMessage }: any) {
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const emptyReviewForm = { score: "", tasteScore: "", serviceScore: "", content: "" };
  const [reviewForm, setReviewForm] = useState(emptyReviewForm);
  const canReview = (order: Order) => order.status === "RECEIVED" || order.status === "COMPLETED" || order.status === "USED";

  function startReview(id: number) {
    setReviewingId(id);
    setReviewForm(emptyReviewForm);
  }

  async function submitReview(id: number) {
    const payload = {
      orderId: id,
      score: Number(reviewForm.score),
      tasteScore: Number(reviewForm.tasteScore),
      serviceScore: Number(reviewForm.serviceScore),
      content: reviewForm.content.trim()
    };
    if (!payload.content) {
      setMessage("请先填写评价内容");
      return;
    }
    if (![payload.score, payload.tasteScore, payload.serviceScore].every(value => Number.isInteger(value) && value >= 1 && value <= 5)) {
      setMessage("请填写 1 到 5 分的评分信息");
      return;
    }
    await api("/api/v1/reviews", { method: "POST", body: JSON.stringify(payload) });
    setMessage("评价提交成功");
    setReviewingId(null);
    await reload();
  }
  if (!orders.length) return <div className="panel empty-state"><CreditCard /><h2>暂无订单</h2><p>创建外卖或团购订单后，会在这里显示支付、取消、券码和履约状态。</p></div>;
  return <div className="list">{orders.map((o: Order) => <article className="order" key={o.id}>
    <b>#{o.id} {o.type}</b><span>{statusLabel(o.status)}</span>
    <p>{o.lines.map(l => `${l.name} x${l.quantity}`).join("，")}</p><strong>{money(o.totalCent)}</strong>
    <StatusTimeline timeline={o.statusTimeline} />
    {o.couponCode && <code>{o.couponCode}</code>}
    <button className="detail-toggle" onClick={() => setExpandedId(expandedId === o.id ? null : o.id)}>{expandedId === o.id ? "收起详情" : "查看详情"}</button>
    {expandedId === o.id && <div className="order-detail">
      <p><b>店铺/商家</b><span>{o.merchantName || "未知商家"}</span></p>
      {o.addressSnapshot && <p><b>配送地址</b><span>{o.addressSnapshot}</span></p>}
      {o.lines.map((line, index) => <p key={`${line.name}-${index}`}><b>{line.name} x{line.quantity}</b><span>{money(line.priceCent)} / 份 · 小计 {money(line.priceCent * line.quantity)}</span></p>)}
    </div>}
    {user?.role === "USER" && o.status === "PENDING_PAYMENT" && <div className="actions"><button className="primary" onClick={() => pay(o.id)}>支付</button><button onClick={() => cancelOrder(o.id)}>取消</button></div>}
    {user?.role === "USER" && o.type === "DELIVERY" && ["DELIVERING", "COMPLETED"].includes(o.status) && <div className="actions"><button className="primary" onClick={() => receiveOrder(o.id)}>确认收货</button></div>}
    {user?.role === "USER" && <div className="review-panel">
      <span>{o.reviewed ? "已评价" : canReview(o) ? "可以评价本次订单" : "订单完成后可评价"}</span>
      {!o.reviewed && canReview(o) && reviewingId !== o.id && <button onClick={() => startReview(o.id)}>填写评价</button>}
    </div>}
    {reviewingId === o.id && <div className="review-form">
      <div className="form-grid">
        <input type="number" min="1" max="5" value={reviewForm.score} onChange={e => setReviewForm({ ...reviewForm, score: e.target.value })} placeholder="此处填写综合评分（1-5）" />
        <input type="number" min="1" max="5" value={reviewForm.tasteScore} onChange={e => setReviewForm({ ...reviewForm, tasteScore: e.target.value })} placeholder="此处填写口味评分（1-5）" />
        <input type="number" min="1" max="5" value={reviewForm.serviceScore} onChange={e => setReviewForm({ ...reviewForm, serviceScore: e.target.value })} placeholder="此处填写服务评分（1-5）" />
      </div>
      <textarea value={reviewForm.content} onChange={e => setReviewForm({ ...reviewForm, content: e.target.value })} placeholder="写下这次体验" />
      <div className="actions"><button className="primary" onClick={() => submitReview(o.id)}>提交评价</button><button onClick={() => setReviewingId(null)}>取消</button></div>
    </div>}
  </article>)}</div>;
}
