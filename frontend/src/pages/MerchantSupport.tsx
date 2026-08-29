import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import type { ChatMessage, ConversationSummary } from "../types";

export default function MerchantSupport({ user, setMessage }: { user: any; setMessage: (msg: string) => void }) {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [activeUserId, setActiveUserId] = useState<number | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [reply, setReply] = useState("");
  const [aiQuestion, setAiQuestion] = useState("");
  const [aiAnswer, setAiAnswer] = useState("");
  const [aiError, setAiError] = useState("");
  const [askingAi, setAskingAi] = useState(false);
  const aiInputRef = useRef<HTMLInputElement | null>(null);

  async function loadConversations() {
    const data = await api<ConversationSummary[]>("/api/v1/merchant-admin/conversations");
    setConversations(data);
    if (!activeUserId && data[0]) setActiveUserId(data[0].userId);
  }

  async function loadConversation(userId = activeUserId) {
    if (!userId) return;
    setChatMessages(await api<ChatMessage[]>(`/api/v1/merchant-admin/conversations/${userId}`));
  }

  useEffect(() => { loadConversations().catch(() => {}); }, [user.id]);
  useEffect(() => { loadConversation().catch(() => {}); }, [activeUserId]);

  async function sendReply() {
    if (!activeUserId || !reply.trim()) return;
    const next = await api<ChatMessage[]>(`/api/v1/merchant-admin/conversations/${activeUserId}/messages`, { method: "POST", body: JSON.stringify({ content: reply }) });
    setChatMessages(next);
    setReply("");
    await loadConversations();
  }

  async function askMerchantAi() {
    if (!aiQuestion.trim()) { setAiError("请输入要咨询 AI 客服的问题"); aiInputRef.current?.focus(); return; }
    setAskingAi(true); setAiError(""); setAiAnswer("");
    try {
      const data = await api<{ answer: string }>("/api/v1/merchant-admin/assistant/ask", { method: "POST", body: JSON.stringify({ question: aiQuestion }) });
      const answer = data.answer?.trim();
      if (!answer) throw new Error("AI 客服没有返回内容");
      setAiAnswer(answer);
      setMessage("AI 客服已生成回复建议");
    } catch (error) {
      const msg = error instanceof Error ? error.message : "AI 客服暂时不可用";
      setAiError(msg);
      setMessage(`AI 客服生成失败：${msg}`);
    } finally { setAskingAi(false); }
  }

  function useAiAnswerAsReply() {
    if (!aiAnswer) return;
    setReply(aiAnswer);
    setMessage("已填入回复框");
  }

  return <section className="merchant-chat">
    <h3>店家客服</h3>
    <div className="merchant-ai-box">
      <div className="chat-input">
        <input ref={aiInputRef} value={aiQuestion} onChange={e => { setAiQuestion(e.target.value); if (aiError) setAiError(""); }} onKeyDown={e => { if (e.key === "Enter") askMerchantAi(); }} placeholder="问 AI 客服，例如：顾客问能不能少辣怎么回复" />
        <button className="primary" onClick={askMerchantAi} disabled={askingAi}>{askingAi ? "生成中" : "提问"}</button>
      </div>
      {aiError && <p className="form-error" role="alert">{aiError}</p>}
      {aiAnswer && <div className="answer ai-answer" role="status"><p>{aiAnswer}</p><button onClick={useAiAnswerAsReply}>填入回复框</button></div>}
    </div>
    <div className="chat-layout compact-chat">
      <div className="chat-thread-list">
        {conversations.map(item => <button className={item.userId === activeUserId ? "chat-thread active" : "chat-thread"} key={item.userId} onClick={() => setActiveUserId(item.userId)}><b>{item.userName}</b><span>{item.lastMessage}</span></button>)}
        {!conversations.length && <p className="hint">暂无用户咨询</p>}
      </div>
      <div className="chat-panel">
        <div className="chat-messages">
          {chatMessages.map(message => <div className={message.senderRole === "USER" ? "chat-bubble" : "chat-bubble mine"} key={message.id}><small>{message.senderName}</small><p>{message.content}</p></div>)}
          {!chatMessages.length && <p className="hint">选择用户会话后回复咨询，或先用上方 AI 客服生成回复建议</p>}
        </div>
        <div className="chat-input">
          <input data-testid="merchant-chat-input" value={reply} onChange={e => setReply(e.target.value)} onKeyDown={e => { if (e.key === "Enter") sendReply(); }} placeholder="回复用户" />
          <button data-testid="merchant-chat-send" className="primary" onClick={sendReply} disabled={!activeUserId}>发送</button>
        </div>
      </div>
    </div>
  </section>;
}
