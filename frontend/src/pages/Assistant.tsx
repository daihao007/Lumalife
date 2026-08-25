import { useEffect, useState } from "react";
import { Coffee, Send } from "lucide-react";
import { api } from "../api";
import type { ChatMessage, ConversationSummary, User } from "../types";

export default function Assistant({ user, initialMerchantId }: { user: User | null; initialMerchantId?: number | null }) {
  if (user?.role === "USER") return <MerchantChat user={user} initialMerchantId={initialMerchantId || null} />;
  return <PlatformAssistant />;
}

function PlatformAssistant() {
  const [question, setQuestion] = useState("为什么不能评价订单？");
  const [answer, setAnswer] = useState("");

  async function ask() {
    const data = await api<{ answer: string }>("/api/v1/assistant/ask", {
      method: "POST",
      body: JSON.stringify({ question })
    });
    setAnswer(data.answer);
  }

  return <div className="panel">
    <Coffee />
    <h2>AI 客服</h2>
    <input value={question} onChange={e => setQuestion(e.target.value)} onKeyDown={e => { if (e.key === "Enter") ask(); }} />
    <button className="primary" onClick={ask}>提问</button>
    {answer && <p className="answer">{answer}</p>}
  </div>;
}

function MerchantChat({ user, initialMerchantId }: { user: User; initialMerchantId: number | null }) {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [merchantId, setMerchantId] = useState<number | null>(initialMerchantId);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [content, setContent] = useState("");
  const [sending, setSending] = useState(false);
  const active = conversations.find(item => item.merchantId === merchantId);

  async function loadConversations() {
    const data = await api<ConversationSummary[]>("/api/v1/conversations");
    setConversations(data);
  }

  async function loadMessages(nextMerchantId = merchantId) {
    if (!nextMerchantId) return;
    setMessages(await api<ChatMessage[]>(`/api/v1/conversations/${nextMerchantId}`));
  }

  useEffect(() => {
    loadConversations().catch(() => {});
  }, []);

  useEffect(() => {
    if (initialMerchantId) setMerchantId(initialMerchantId);
  }, [initialMerchantId]);

  useEffect(() => {
    loadMessages().catch(() => {});
  }, [merchantId]);

  async function send() {
    const text = content.trim();
    if (!merchantId || !text || sending) return;

    const tempId = -Date.now();
    const optimistic: ChatMessage = {
      id: tempId,
      userId: user.id,
      merchantId,
      senderRole: "USER",
      senderName: user.nickname || "我",
      content: text,
      createdAt: new Date().toISOString()
    };
    const pending: ChatMessage = {
      id: tempId - 1,
      userId: user.id,
      merchantId,
      senderRole: "MERCHANT_AI",
      senderName: active?.merchantName || "店家客服",
      content: "正在生成回复...",
      createdAt: new Date().toISOString()
    };

    setMessages(current => [...current, optimistic, pending]);
    setContent("");
    setSending(true);

    try {
      const next = await api<ChatMessage[]>(`/api/v1/conversations/${merchantId}/messages`, {
        method: "POST",
        body: JSON.stringify({ content: text })
      });
      setMessages(next);
      await loadConversations();
    } catch (error) {
      const message = error instanceof Error ? error.message : "发送失败，请稍后重试";
      setMessages(current => current.map(item => item.id === pending.id ? { ...item, content: message } : item));
    } finally {
      setSending(false);
    }
  }

  return <div className="chat-layout">
    <section className="account-panel">
      <h3>店家客服</h3>
      {conversations.map(item => <button className={item.merchantId === merchantId ? "chat-thread active" : "chat-thread"} key={item.merchantId} onClick={() => setMerchantId(item.merchantId)}>
        <b>{item.merchantName}</b><span>{item.lastMessage}</span>
      </button>)}
      {!conversations.length && <p className="hint">{merchantId ? "发送第一条消息后会出现在会话列表中" : "请先从店铺详情页联系店家客服"}</p>}
    </section>
    <section className="chat-panel">
      <h3>{active?.merchantName || (merchantId ? "店家客服" : "请选择会话")}</h3>
      <div className="chat-messages">
        {messages.map(message => <div className={message.senderRole === "USER" ? "chat-bubble mine" : "chat-bubble"} key={message.id}>
          <small>{message.senderName}</small><p>{message.content}</p>
        </div>)}
        {!messages.length && <p className="hint">可以询问营业时间、菜品库存、配送进度或订单问题。</p>}
      </div>
      <div className="chat-input">
        <input value={content} onChange={e => setContent(e.target.value)} onKeyDown={e => { if (e.key === "Enter") send(); }} placeholder="输入要咨询店家的问题" />
        <button className="primary" onClick={send} disabled={!merchantId || sending}><Send size={16} /> {sending ? "发送中" : "发送"}</button>
      </div>
    </section>
  </div>;
}
