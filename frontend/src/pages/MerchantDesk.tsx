import { useEffect, useRef, useState } from "react";
import { Pencil, Store, TicketCheck, Trash2, Utensils } from "lucide-react";
import { api } from "../api";
import type { ChatMessage, ConversationSummary, Deal, Merchant, Order, Product, Review, User } from "../types";
import { money, statusLabel } from "../utils";

type MerchantProfilePayload = { user: User; merchant: Merchant };

export default function MerchantDesk({ user, onProfileUpdated, orders, reload, setMessage, supportRequest }: { user: User; onProfileUpdated: (profile: MerchantProfilePayload) => Promise<void>; orders: Order[]; reload: () => Promise<void>; setMessage: (message: string) => void; supportRequest: number }) {
  const [code, setCode] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [deals, setDeals] = useState<Deal[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [activeUserId, setActiveUserId] = useState<number | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [reply, setReply] = useState("");
  const [aiQuestion, setAiQuestion] = useState("");
  const [aiAnswer, setAiAnswer] = useState("");
  const [aiError, setAiError] = useState("");
  const [askingAi, setAskingAi] = useState(false);
  const [nickname, setNickname] = useState(user.nickname);
  const [profileError, setProfileError] = useState("");
  const [savingNickname, setSavingNickname] = useState(false);
  const emptyProduct = { id: undefined as number | undefined, name: "", description: "", priceYuan: "28.00", stock: "", listed: true };
  const emptyDeal = { id: undefined as number | undefined, title: "", description: "", priceYuan: "59.90", stock: "", active: true };
  const [product, setProduct] = useState(emptyProduct);
  const [deal, setDeal] = useState(emptyDeal);
  const supportRef = useRef<HTMLElement | null>(null);
  const aiInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setNickname(merchant?.name || user.nickname);
  }, [merchant?.name, user.nickname]);

  function toCent(value: string) {
    return Math.round(Number(value) * 100);
  }

  function toYuan(cent: number) {
    return (cent / 100).toFixed(2);
  }

  async function loadMerchantProfile() {
    try {
      const profile = await api<MerchantProfilePayload>("/api/v1/merchant-admin/profile");
      setMerchant(profile.merchant);
      setNickname(profile.merchant.name || profile.user.nickname);
      setProfileError("");
      return profile;
    } catch (error) {
      if (!user.merchantId) throw error;
      const detail = await api<{ merchant: Merchant }>(`/api/v1/merchants/${user.merchantId}`);
      setMerchant(detail.merchant);
      setNickname(detail.merchant.name || user.nickname);
      setProfileError(error instanceof Error ? error.message : "商家资料接口暂不可用");
      return { user, merchant: detail.merchant };
    }
  }

  async function loadAssets() {
    await loadMerchantProfile();
    setProducts(await api<Product[]>("/api/v1/merchant-admin/products"));
    setDeals(await api<Deal[]>("/api/v1/merchant-admin/group-deals"));
    setReviews(await api<Review[]>("/api/v1/merchant-admin/reviews"));
    await loadConversations();
  }

  async function loadConversations() {
    const data = await api<ConversationSummary[]>("/api/v1/merchant-admin/conversations");
    setConversations(data);
    if (!activeUserId && data[0]) setActiveUserId(data[0].userId);
  }

  async function loadConversation(userId = activeUserId) {
    if (!userId) return;
    setChatMessages(await api<ChatMessage[]>(`/api/v1/merchant-admin/conversations/${userId}`));
  }

  useEffect(() => {
    loadAssets().catch(error => {
      setProfileError(error instanceof Error ? error.message : "商家信息加载失败");
      setMessage(error instanceof Error ? error.message : "商家信息加载失败");
    });
  }, [user.id]);

  useEffect(() => {
    loadConversation().catch(() => {});
  }, [activeUserId]);

  useEffect(() => {
    if (!supportRequest) return;
    supportRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    window.setTimeout(() => aiInputRef.current?.focus(), 260);
  }, [supportRequest]);

  async function next(order: Order) {
    const map: Record<string, string> = { PAID: "ACCEPTED", ACCEPTED: "DELIVERING" };
    await api(`/api/v1/merchant-admin/orders/${order.id}/transition`, { method: "POST", body: JSON.stringify({ next: map[order.status] }) });
    setMessage("订单状态已更新");
    await reload();
    await loadAssets();
  }

  async function verify() {
    await api("/api/v1/merchant-admin/coupons/verify", { method: "POST", body: JSON.stringify({ code }) });
    setMessage("券码核销成功");
    await reload();
    await loadAssets();
  }

  async function saveNickname() {
    if (!nickname.trim()) {
      setMessage("商家昵称不能为空");
      return;
    }
    setSavingNickname(true);
    try {
      let profile: MerchantProfilePayload;
      try {
        profile = await api<MerchantProfilePayload>("/api/v1/merchant-admin/profile", {
          method: "PUT",
          body: JSON.stringify({ nickname })
        });
      } catch {
        const updated = await api<User>("/api/v1/user/profile", {
          method: "POST",
          body: JSON.stringify({ nickname, avatarUrl: user.avatarUrl || "" })
        });
        const detail = await api<{ merchant: Merchant }>(`/api/v1/merchants/${updated.merchantId}`);
        profile = { user: updated, merchant: detail.merchant };
      }
      setMerchant(profile.merchant);
      setNickname(profile.user.nickname);
      setProfileError("");
      await onProfileUpdated(profile);
      setMessage("商家昵称已更新");
      await reload();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "商家昵称保存失败");
    } finally {
      setSavingNickname(false);
    }
  }

  async function saveProduct() {
    const priceCent = toCent(product.priceYuan);
    const stock = Number(product.stock);
    if (!Number.isFinite(priceCent) || priceCent <= 0) {
      setMessage("请输入合法的商品单价");
      return;
    }
    if (!product.stock.trim() || !Number.isInteger(stock) || stock < 0) {
      setMessage("请输入合法的商品库存");
      return;
    }
    await api("/api/v1/merchant-admin/products", {
      method: "POST",
      body: JSON.stringify({ id: product.id, name: product.name, description: product.description, stock, listed: product.listed, priceCent })
    });
    setProduct(emptyProduct);
    setMessage(product.id ? "商品已更新" : "商品已保存");
    loadAssets();
  }

  async function saveDeal() {
    const priceCent = toCent(deal.priceYuan);
    const stock = Number(deal.stock);
    if (!Number.isFinite(priceCent) || priceCent <= 0) {
      setMessage("请输入合法的团购单价");
      return;
    }
    if (!deal.stock.trim() || !Number.isInteger(stock) || stock < 0) {
      setMessage("请输入合法的团购库存");
      return;
    }
    await api("/api/v1/merchant-admin/group-deals", {
      method: "POST",
      body: JSON.stringify({ id: deal.id, title: deal.title, description: deal.description, stock, active: deal.active, priceCent })
    });
    setDeal(emptyDeal);
    setMessage(deal.id ? "团购套餐已更新" : "团购套餐已保存");
    loadAssets();
  }

  async function toggleProduct(id: number) {
    await api(`/api/v1/merchant-admin/products/${id}/toggle`, { method: "POST" });
    loadAssets();
  }

  async function toggleDeal(id: number) {
    await api(`/api/v1/merchant-admin/group-deals/${id}/toggle`, { method: "POST" });
    loadAssets();
  }

  function editProduct(item: Product) {
    setProduct({ id: item.id, name: item.name, description: item.description, priceYuan: toYuan(item.priceCent), stock: String(item.stock), listed: item.listed });
  }

  function editDeal(item: Deal) {
    setDeal({ id: item.id, title: item.title, description: item.description, priceYuan: toYuan(item.priceCent), stock: String(item.stock), active: item.active });
  }

  async function deleteProduct(id: number) {
    if (!window.confirm("确认删除这个商品吗？")) return;
    await api(`/api/v1/merchant-admin/products/${id}/delete`, { method: "POST" });
    if (product.id === id) setProduct(emptyProduct);
    setProducts(current => current.filter(item => item.id !== id));
    setMessage("商品已删除");
    await loadAssets();
  }

  async function deleteDeal(id: number) {
    if (!window.confirm("确认删除这个团购套餐吗？")) return;
    await api(`/api/v1/merchant-admin/group-deals/${id}/delete`, { method: "POST" });
    if (deal.id === id) setDeal(emptyDeal);
    setDeals(current => current.filter(item => item.id !== id));
    setMessage("团购套餐已删除");
    await loadAssets();
  }

  function reviewsFor(orderId: number) {
    return reviews.filter(review => review.orderId === orderId);
  }

  async function sendReply() {
    if (!activeUserId || !reply.trim()) return;
    const next = await api<ChatMessage[]>(`/api/v1/merchant-admin/conversations/${activeUserId}/messages`, { method: "POST", body: JSON.stringify({ content: reply }) });
    setChatMessages(next);
    setReply("");
    await loadConversations();
  }

  async function askMerchantAi() {
    if (!aiQuestion.trim()) {
      setAiError("请输入要咨询 AI 客服的问题");
      aiInputRef.current?.focus();
      return;
    }
    setAskingAi(true);
    setAiError("");
    setAiAnswer("");
    try {
      const data = await api<{ answer: string }>("/api/v1/merchant-admin/assistant/ask", {
        method: "POST",
        body: JSON.stringify({ question: aiQuestion })
      });
      const answer = data.answer?.trim();
      if (!answer) throw new Error("AI 客服没有返回内容");
      setAiAnswer(answer);
      setMessage("AI 客服已生成回复建议");
    } catch (error) {
      const message = error instanceof Error ? error.message : "AI 客服暂时不可用";
      setAiError(message);
      setMessage(`AI 客服生成失败：${message}`);
    } finally {
      setAskingAi(false);
    }
  }

  function useAiAnswerAsReply() {
    if (!aiAnswer) return;
    setReply(aiAnswer);
    setMessage("已填入回复框");
  }

  return <div className="merchant-workbench">
    <section className="merchant-profile"><h3>商家信息</h3><div className="form-grid"><input value={nickname} onChange={e => setNickname(e.target.value)} placeholder="商家昵称" /><input value={merchant?.address || ""} disabled placeholder="店铺地址" /></div><p className="hint">{merchant ? `${merchant.categoryName} · ${merchant.status} · 评分 ${merchant.avgScore} · 月售 ${merchant.monthlySales}` : "正在加载商家信息"}</p>{profileError && <p className="form-error">{profileError}</p>}<button className="primary" disabled={savingNickname} onClick={saveNickname}><Store /> {savingNickname ? "保存中" : "保存商家昵称"}</button></section>
    <section className="merchant-chat" ref={supportRef}><h3>店家客服</h3><div className="merchant-ai-box"><div className="chat-input"><input ref={aiInputRef} value={aiQuestion} onChange={e => { setAiQuestion(e.target.value); if (aiError) setAiError(""); }} onKeyDown={e => { if (e.key === "Enter") askMerchantAi(); }} placeholder="问 AI 客服，例如：顾客问能不能少辣怎么回复" /><button className="primary" onClick={askMerchantAi} disabled={askingAi}>{askingAi ? "生成中" : "提问"}</button></div>{aiError && <p className="form-error" role="alert">{aiError}</p>}{aiAnswer && <div className="answer ai-answer" role="status"><p>{aiAnswer}</p><button onClick={useAiAnswerAsReply}>填入回复框</button></div>}</div><div className="chat-layout compact-chat"><div className="chat-thread-list">{conversations.map(item => <button className={item.userId === activeUserId ? "chat-thread active" : "chat-thread"} key={item.userId} onClick={() => setActiveUserId(item.userId)}><b>{item.userName}</b><span>{item.lastMessage}</span></button>)}{!conversations.length && <p className="hint">暂无用户咨询。左侧客服入口现在会定位到这里，也可以直接用上方 AI 客服生成回复话术。</p>}</div><div className="chat-panel"><div className="chat-messages">{chatMessages.map(message => <div className={message.senderRole === "USER" ? "chat-bubble" : "chat-bubble mine"} key={message.id}><small>{message.senderName}</small><p>{message.content}</p></div>)}{!chatMessages.length && <p className="hint">选择用户会话后回复咨询，或先用上方 AI 客服生成回复建议</p>}</div><div className="chat-input"><input value={reply} onChange={e => setReply(e.target.value)} placeholder="回复用户" /><button className="primary" onClick={sendReply} disabled={!activeUserId}>发送</button></div></div></div></section>
    <section className="fulfillment-section"><h3>履约订单</h3>{orders.map((o: Order) => {
      const orderReviews = reviewsFor(o.id);
      return <div className="fulfillment-order" key={o.id}>
        <div className="line"><span>#{o.id} {statusLabel(o.status)}<small>{money(o.totalCent)} · {o.lines.map(line => `${line.name} x${line.quantity}`).join("，")}</small></span>{["PAID","ACCEPTED"].includes(o.status) && <button onClick={() => next(o)}>下一步</button>}</div>
        <div className={orderReviews.length ? "merchant-review filled" : "merchant-review"}>
          {orderReviews.length ? orderReviews.map(review => <div key={review.id}>
            <b>{review.userName} · {review.score} 分</b>
            <small>口味 {review.tasteScore} · 服务 {review.serviceScore} · {new Date(review.createdAt).toLocaleString()}</small>
            <p>{review.content}</p>
          </div>) : <span>{o.reviewed ? "评价加载中" : "用户暂未评价"}</span>}
        </div>
      </div>;
    })}</section>
    <section><h3>团购核销</h3><input value={code} onChange={e => setCode(e.target.value)} placeholder="输入 12 位券码" /><button className="primary" onClick={verify}><TicketCheck /> 核销</button></section>
    <section><h3>商品维护</h3><div className="form-grid"><input value={product.name} onChange={e => setProduct({ ...product, name: e.target.value })} placeholder="商品名" /><input value={product.description} onChange={e => setProduct({ ...product, description: e.target.value })} placeholder="描述" /><input type="number" min="0.01" step="0.01" value={product.priceYuan} onChange={e => setProduct({ ...product, priceYuan: e.target.value })} placeholder="单价（元）" /><input type="number" min="0" value={product.stock} onChange={e => setProduct({ ...product, stock: e.target.value })} placeholder="库存量" /></div><div className="actions manage-actions"><button className="primary" onClick={saveProduct}><Utensils /> {product.id ? "更新商品" : "新增商品"}</button>{product.id && <button onClick={() => setProduct(emptyProduct)}>取消编辑</button>}</div>{products.map(p => <div className="line manage-line" key={p.id}><span><b>{p.name}</b><small>价格 {money(p.priceCent)} · 库存 {p.stock} 份</small></span><strong>{p.listed ? "上架" : "下架"}</strong><button onClick={() => editProduct(p)}><Pencil /> 编辑</button><button onClick={() => toggleProduct(p.id)}>{p.listed ? "下架" : "上架"}</button><button onClick={() => deleteProduct(p.id)}><Trash2 /> 删除</button></div>)}</section>
    <section><h3>团购维护</h3><div className="form-grid"><input value={deal.title} onChange={e => setDeal({ ...deal, title: e.target.value })} placeholder="套餐名" /><input value={deal.description} onChange={e => setDeal({ ...deal, description: e.target.value })} placeholder="描述" /><input type="number" min="0.01" step="0.01" value={deal.priceYuan} onChange={e => setDeal({ ...deal, priceYuan: e.target.value })} placeholder="单价（元）" /><input type="number" min="0" value={deal.stock} onChange={e => setDeal({ ...deal, stock: e.target.value })} placeholder="库存量" /></div><div className="actions manage-actions"><button className="primary" onClick={saveDeal}>{deal.id ? "更新套餐" : "新增套餐"}</button>{deal.id && <button onClick={() => setDeal(emptyDeal)}>取消编辑</button>}</div>{deals.map(d => <div className="line manage-line" key={d.id}><span><b>{d.title}</b><small>价格 {money(d.priceCent)} · 库存 {d.stock} 份</small></span><strong>{d.active ? "上架" : "下架"}</strong><button onClick={() => editDeal(d)}><Pencil /> 编辑</button><button onClick={() => toggleDeal(d.id)}>{d.active ? "下架" : "上架"}</button><button onClick={() => deleteDeal(d.id)}><Trash2 /> 删除</button></div>)}</section>
  </div>;
}
