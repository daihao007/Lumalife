import { useEffect, useState } from "react";
import { Upload, UserRound } from "lucide-react";
import { api } from "../api";
import type { Address, User } from "../types";

export default function Profile({ user, setUser, setMessage }: { user: User; setUser: (value: User) => void; setMessage: (value: string) => void }) {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const emptyForm = { id: null as number | null, contactName: user.nickname, phone: user.phone, detail: "", defaultAddress: false };
  const [form, setForm] = useState(emptyForm);
  const [profile, setProfile] = useState({ nickname: user.nickname, avatarUrl: user.avatarUrl || "" });

  async function load() {
    setAddresses(await api<Address[]>("/api/v1/user/addresses"));
  }

  useEffect(() => { load(); }, []);

  async function save() {
    await api("/api/v1/user/addresses", { method: "POST", body: JSON.stringify(form) });
    setForm(emptyForm);
    setMessage(form.id ? "地址已更新" : "地址已保存");
    load();
  }

  async function saveProfile() {
    const next = await api<User>("/api/v1/user/profile", { method: "POST", body: JSON.stringify(profile) });
    setUser(next);
    setMessage("个人资料已更新");
  }

  async function makeDefault(id: number) {
    await api(`/api/v1/user/addresses/${id}/default`, { method: "POST" });
    setMessage("默认地址已更新");
    load();
  }

  async function remove(id: number) {
    await api(`/api/v1/user/addresses/${id}/delete`, { method: "POST" });
    setMessage("地址已删除");
    load();
  }

  function edit(address: Address) {
    setForm({ id: address.id, contactName: address.contactName, phone: address.phone, detail: address.detail, defaultAddress: address.defaultAddress });
  }

  function uploadAvatar(file?: File) {
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setMessage("请选择图片格式的头像");
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      setMessage("头像文件不能超过 2MB");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setProfile(current => ({ ...current, avatarUrl: String(reader.result) }));
    reader.onerror = () => setMessage("头像读取失败，请重试");
    reader.readAsDataURL(file);
  }

  return <div className="split">
    <section>
      <h3>个人资料</h3>
      <div className="profile-editor">
        <div className="avatar-preview">{profile.avatarUrl ? <img src={profile.avatarUrl} /> : <UserRound />}</div>
        <div className="form-grid">
          <input value={profile.nickname} onChange={e => setProfile({ ...profile, nickname: e.target.value })} placeholder="昵称" />
          <label className="upload-button"><Upload size={16} /> 选择头像<input type="file" accept="image/*" onChange={e => uploadAvatar(e.target.files?.[0])} /></label>
        </div>
      <button data-testid="profile-save" className="primary" onClick={saveProfile}>保存资料</button>
      </div>
      <h3>收货地址</h3>
      {addresses.map(a => <div className="line" key={a.id}><span><b>{a.contactName} · {a.phone}</b><small>{a.detail}</small></span><strong>{a.defaultAddress ? "默认" : ""}</strong><button onClick={() => edit(a)}>修改</button><button onClick={() => makeDefault(a.id)}>设默认</button><button onClick={() => remove(a.id)}>删除</button></div>)}
    </section>
    <section>
      <h3>{form.id ? "修改地址" : "新增地址"}</h3>
      <div className="form-grid">
        <input value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} placeholder="联系人" />
        <input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="手机号" />
        <input data-testid="address-detail" value={form.detail} onChange={e => setForm({ ...form, detail: e.target.value })} placeholder="详细地址" />
        <label className="check"><input type="checkbox" checked={form.defaultAddress} onChange={e => setForm({ ...form, defaultAddress: e.target.checked })} /> 默认地址</label>
      </div>
      <button data-testid="address-save" className="primary" onClick={save}>{form.id ? "更新地址" : "保存地址"}</button>
      {form.id && <button onClick={() => setForm(emptyForm)}>取消修改</button>}
    </section>
  </div>;
}
