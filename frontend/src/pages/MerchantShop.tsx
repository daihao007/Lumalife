import { useEffect, useState } from "react";
import { Store } from "lucide-react";
import { api } from "../api";
import type { Merchant, User } from "../types";

type MerchantProfilePayload = { user: User; merchant: Merchant };

export default function MerchantShop({ user, onProfileUpdated, setMessage }: { user: User; onProfileUpdated: (profile: MerchantProfilePayload) => Promise<void>; setMessage: (msg: string) => void }) {
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [nickname, setNickname] = useState(user.nickname);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const profile = await api<MerchantProfilePayload>("/api/v1/merchant-admin/profile");
        setMerchant(profile.merchant);
        setNickname(profile.merchant.name || profile.user.nickname);
      } catch {
        if (user.merchantId) {
          const detail = await api<{ merchant: Merchant }>(`/api/v1/merchants/${user.merchantId}`);
          setMerchant(detail.merchant);
          setNickname(detail.merchant.name || user.nickname);
        }
      }
    })();
  }, [user.id]);

  useEffect(() => { setNickname(merchant?.name || user.nickname); }, [merchant?.name, user.nickname]);

  async function saveNickname() {
    if (!nickname.trim()) { setMessage("商家昵称不能为空"); return; }
    setSaving(true);
    try {
      let profile: MerchantProfilePayload;
      try {
        profile = await api<MerchantProfilePayload>("/api/v1/merchant-admin/profile", { method: "PUT", body: JSON.stringify({ nickname }) });
      } catch {
        const updated = await api<User>("/api/v1/user/profile", { method: "POST", body: JSON.stringify({ nickname, avatarUrl: user.avatarUrl || "" }) });
        const detail = await api<{ merchant: Merchant }>(`/api/v1/merchants/${updated.merchantId}`);
        profile = { user: updated, merchant: detail.merchant };
      }
      setMerchant(profile.merchant);
      setNickname(profile.user.nickname);
      setError("");
      await onProfileUpdated(profile);
      setMessage("商家昵称已更新");
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "商家昵称保存失败");
    } finally { setSaving(false); }
  }

  return <section className="merchant-profile">
    <h3>店铺信息</h3>
    <div className="form-grid">
      <input value={nickname} onChange={e => setNickname(e.target.value)} placeholder="商家昵称" />
      <input value={merchant?.address || ""} disabled placeholder="店铺地址" />
    </div>
    <p className="hint">{merchant ? `${merchant.categoryName} · ${merchant.status} · 评分 ${merchant.avgScore} · 月售 ${merchant.monthlySales}` : "正在加载商家信息"}</p>
    {error && <p className="form-error">{error}</p>}
    <button className="primary" disabled={saving} onClick={saveNickname}><Store /> {saving ? "保存中" : "保存商家昵称"}</button>
  </section>;
}
