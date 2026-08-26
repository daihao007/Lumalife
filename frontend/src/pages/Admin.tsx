import { useEffect, useState } from "react";
import {
  PieChart, Pie, Cell, Tooltip as RTooltip, Legend,
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer,
} from "recharts";
import { api } from "../api";
import { money, statusLabel } from "../utils";

// ---------- 类型 ----------
type Account = { id: number; username: string; nickname: string; role: string; merchantId?: number };
type ActiveOrder = {
  id: number; merchantName: string; type: string; status: string;
  totalCent: number; createdAt: string; elapsedMinutes: number;
  statusTimeline: Record<string, string>;
};

// ---------- 颜色 ----------
const STATUS_COLORS: Record<string, string> = {
  PENDING_PAYMENT: "#bd8b2f",
  PAID: "#3b82f6",
  ACCEPTED: "#8b5cf6",
  DELIVERING: "#f97316",
  RECEIVED: "#06b6d4",
  COMPLETED: "#22c55e",
  USED: "#14b8a6",
  EXPIRED: "#94a3b8",
  CANCELLED: "#ef4444",
};
const TYPE_COLORS = ["#0f766e", "#df5b45"];
const PIE_COLORS = ["#0f766e", "#3b82f6", "#8b5cf6", "#f97316", "#22c55e", "#ef4444", "#bd8b2f", "#06b6d4", "#94a3b8"];

// ---------- 组件 ----------
export default function Admin() {
  const [metrics, setMetrics] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function loadMetrics() {
    setLoading(true);
    setError("");
    try {
      setMetrics(await api("/api/v1/admin/metrics"));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "管理员看板加载失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadMetrics(); }, []);

  if (loading) return <div className="panel empty-state" role="status"><h2>正在加载管理员看板</h2><p>正在汇总用户、商家、订单和系统健康数据。</p></div>;
  if (error) return <div className="panel empty-state"><h2>管理员看板加载失败</h2><p className="form-error" role="alert">{error}</p><button className="primary" data-testid="admin-retry" onClick={loadMetrics}>重新加载</button></div>;
  if (!metrics) return null;

  const { overview, orderStatusDistribution, orderTypeDistribution, revenueTrend,
    merchantRanking, deliveryMetrics, activeOrders, health, userAccounts, merchantAccounts, logs } = metrics;

  // 环形图数据
  const statusData = Object.entries(orderStatusDistribution || {}).map(([k, v]) => ({ name: statusLabel(k), value: v as number, key: k }));
  const typeData = Object.entries(orderTypeDistribution || {}).map(([k, v]) => ({ name: k === "DELIVERY" ? "外卖" : "团购", value: v as number }));

  return <div className="admin">
    {/* ===== KPI 卡片 ===== */}
    <div className="kpis">
      <KpiCard value={overview.users} label="用户" />
      <KpiCard value={overview.merchants} label="商家" />
      <KpiCard value={overview.todayOrders} label="今日订单" />
      <KpiCard value={money(overview.todayAmountCent)} label="今日营收" />
      <KpiCard value={`${Math.round((deliveryMetrics?.completionRate ?? 0) * 100)}%`} label="完成率" />
    </div>

    {/* ===== 图表区 ===== */}
    <div className="admin-charts">
      <div className="admin-chart-panel">
        <h3>订单状态分布</h3>
        <ResponsiveContainer width="100%" height={260}>
          <PieChart>
            <Pie data={statusData} dataKey="value" nameKey="name" cx="50%" cy="50%"
              innerRadius={55} outerRadius={90} paddingAngle={2} label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
              {statusData.map((d, i) => <Cell key={i} fill={STATUS_COLORS[d.key] || PIE_COLORS[i % PIE_COLORS.length]} />)}
            </Pie>
            <RTooltip formatter={(v: number) => `${v} 单`} />
          </PieChart>
        </ResponsiveContainer>
      </div>

      <div className="admin-chart-panel">
        <h3>近 7 天营收趋势</h3>
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={revenueTrend}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
            <XAxis dataKey="date" tick={{ fontSize: 12 }} tickFormatter={d => d.slice(5)} />
            <YAxis tick={{ fontSize: 12 }} tickFormatter={v => `¥${(v / 100).toFixed(0)}`} />
            <RTooltip formatter={(v: number) => money(v)} labelFormatter={l => `日期: ${l}`} />
            <Line type="monotone" dataKey="amountCent" stroke="var(--teal)" strokeWidth={2} dot={{ r: 4 }} name="营收" />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="admin-chart-panel">
        <h3>订单类型</h3>
        <ResponsiveContainer width="100%" height={260}>
          <PieChart>
            <Pie data={typeData} dataKey="value" nameKey="name" cx="50%" cy="50%"
              innerRadius={55} outerRadius={90} paddingAngle={4} label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
              {typeData.map((_, i) => <Cell key={i} fill={TYPE_COLORS[i]} />)}
            </Pie>
            <RTooltip formatter={(v: number) => `${v} 单`} />
          </PieChart>
        </ResponsiveContainer>
      </div>

      <div className="admin-chart-panel">
        <h3>配送时效</h3>
        <div className="delivery-metrics">
          <MetricGauge label="平均接单" value={deliveryMetrics?.avgAcceptMinutes ?? 0} unit="分钟" color="var(--teal)" max={30} />
          <MetricGauge label="平均送达" value={deliveryMetrics?.avgDeliveryMinutes ?? 0} unit="分钟" color="var(--coral)" max={60} />
          <MetricGauge label="完成率" value={Math.round((deliveryMetrics?.completionRate ?? 0) * 100)} unit="%" color="var(--gold)" max={100} />
        </div>
      </div>
    </div>

    {/* ===== 商户排行榜 ===== */}
    <div className="admin-section">
      <h3>商户排行榜</h3>
      <div className="admin-table">
        <div className="admin-table-head">
          <span>排名</span><span>商户</span><span>订单数</span><span>营收</span><span>评分</span>
        </div>
        {(merchantRanking || []).map((r: any, i: number) => (
          <div className="admin-table-row" key={r.merchantId}>
            <span className={`rank-badge ${i < 3 ? "top" : ""}`}>{i + 1}</span>
            <span>{r.name}</span>
            <span>{r.orderCount}</span>
            <span>{money(r.revenueCent)}</span>
            <span>⭐ {r.avgScore}</span>
          </div>
        ))}
        {!(merchantRanking?.length) && <p className="hint">暂无数据</p>}
      </div>
    </div>

    {/* ===== 活跃订单流 ===== */}
    <div className="admin-section">
      <h3>活跃订单流 ({(activeOrders || []).length})</h3>
      <div className="active-orders">
        {(activeOrders || []).map((o: ActiveOrder) => (
          <ActiveOrderCard key={o.id} order={o} />
        ))}
        {!(activeOrders?.length) && <p className="hint">当前无活跃订单 🎉</p>}
      </div>
    </div>

    {/* ===== 账户 + 日志 ===== */}
    <div className="admin-accounts">
      <AccountTable title="用户账号" accounts={userAccounts || []} />
      <AccountTable title="商家账号" accounts={merchantAccounts || []} showMerchantId />
    </div>

    <div className="admin-section">
      <h3>操作日志</h3>
      <div className="admin-logs">
        {(logs || []).slice(0, 12).map((l: any) => (
          <div className="log-entry" key={l.id}>
            <span className="log-time">{l.createdAt?.slice(11, 16)}</span>
            <span className="log-actor">{l.actor}</span>
            <span>{l.action}</span>
          </div>
        ))}
        {!(logs?.length) && <p className="hint">暂无日志</p>}
      </div>
    </div>
  </div>;
}

// ---------- 子组件 ----------

function KpiCard({ value, label }: { value: string | number; label: string }) {
  return <div className="kpi-card">
    <b>{value}</b>
    <span>{label}</span>
  </div>;
}

function MetricGauge({ label, value, unit, color, max }: { label: string; value: number; unit: string; color: string; max: number }) {
  const pct = Math.min(100, (value / max) * 100);
  return <div className="gauge-item">
    <div className="gauge-label">{label}</div>
    <div className="gauge-bar-bg">
      <div className="gauge-bar" style={{ width: `${pct}%`, background: color }} />
    </div>
    <div className="gauge-value">{value} {unit}</div>
  </div>;
}

function ActiveOrderCard({ order }: { order: ActiveOrder }) {
  const steps = order.type === "GROUP_BUY"
    ? ["PENDING_PAYMENT", "PAID", "USED"]
    : ["PENDING_PAYMENT", "PAID", "ACCEPTED", "DELIVERING", "RECEIVED"];
  const currentIdx = steps.indexOf(order.status);
  const isOverdue = order.elapsedMinutes > 30 && (order.status === "DELIVERING" || order.status === "ACCEPTED");

  return <div className={`active-order-card ${isOverdue ? "overdue" : ""}`}>
    <div className="ao-header">
      <span className="ao-id">#{order.id}</span>
      <span className="ao-merchant">{order.merchantName}</span>
      <span className="ao-type">{order.type === "GROUP_BUY" ? "团购" : "外卖"}</span>
      <span className="ao-status" style={{ color: STATUS_COLORS[order.status] }}>{statusLabel(order.status)}</span>
      <span className="ao-time">{order.elapsedMinutes}min{isOverdue ? " ⚠️" : ""}</span>
    </div>
    <div className="ao-progress">
      {steps.map((s, i) => (
        <div key={s} className={`ao-step ${i <= currentIdx ? "done" : ""} ${i === currentIdx ? "current" : ""}`}>
          <div className="ao-dot" />
          {i < steps.length - 1 && <div className="ao-line" />}
        </div>
      ))}
    </div>
    <div className="ao-step-labels">
      {steps.map(s => <span key={s}>{statusLabel(s)}</span>)}
    </div>
  </div>;
}

function AccountTable({ title, accounts, showMerchantId = false }: { title: string; accounts: Account[]; showMerchantId?: boolean }) {
  return <section className="account-panel">
    <h3>{title}</h3>
    <div className="account-table">
      <div className={`account-row account-head ${showMerchantId ? "" : "compact"}`}>
        <span>用户名</span><span>昵称</span>{showMerchantId && <span>店铺ID</span>}
      </div>
      {accounts.map(account => <div className={`account-row ${showMerchantId ? "" : "compact"}`} key={account.id}>
        <span>{account.username}</span>
        <span>{account.nickname}</span>
        {showMerchantId && <span>{account.merchantId ?? "-"}</span>}
      </div>)}
      {!accounts.length && <p className="hint">暂无账号</p>}
    </div>
  </section>;
}
