import { useEffect, useState } from "react";
import { Pencil, Trash2, Utensils } from "lucide-react";
import { api } from "../api";
import type { Deal, Product } from "../types";
import { money } from "../utils";

export default function MerchantProducts({ user, setMessage }: { user: any; setMessage: (msg: string) => void }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [deals, setDeals] = useState<Deal[]>([]);
  const emptyProduct = { id: undefined as number | undefined, name: "", description: "", priceYuan: "28.00", stock: "", listed: true };
  const emptyDeal = { id: undefined as number | undefined, title: "", description: "", priceYuan: "59.90", stock: "", active: true };
  const [product, setProduct] = useState(emptyProduct);
  const [deal, setDeal] = useState(emptyDeal);

  function toCent(value: string) { return Math.round(Number(value) * 100); }
  function toYuan(cent: number) { return (cent / 100).toFixed(2); }

  async function loadAll() {
    setProducts(await api<Product[]>("/api/v1/merchant-admin/products"));
    setDeals(await api<Deal[]>("/api/v1/merchant-admin/group-deals"));
  }

  useEffect(() => { loadAll().catch(() => {}); }, [user.id]);

  async function saveProduct() {
    const priceCent = toCent(product.priceYuan);
    const stock = Number(product.stock);
    if (!Number.isFinite(priceCent) || priceCent <= 0) { setMessage("请输入合法的商品单价"); return; }
    if (!product.stock.trim() || !Number.isInteger(stock) || stock < 0) { setMessage("请输入合法的商品库存"); return; }
    await api("/api/v1/merchant-admin/products", { method: "POST", body: JSON.stringify({ id: product.id, name: product.name, description: product.description, stock, listed: product.listed, priceCent }) });
    setProduct(emptyProduct);
    setMessage(product.id ? "商品已更新" : "商品已保存");
    loadAll();
  }

  async function saveDeal() {
    const priceCent = toCent(deal.priceYuan);
    const stock = Number(deal.stock);
    if (!Number.isFinite(priceCent) || priceCent <= 0) { setMessage("请输入合法的团购单价"); return; }
    if (!deal.stock.trim() || !Number.isInteger(stock) || stock < 0) { setMessage("请输入合法的团购库存"); return; }
    await api("/api/v1/merchant-admin/group-deals", { method: "POST", body: JSON.stringify({ id: deal.id, title: deal.title, description: deal.description, stock, active: deal.active, priceCent }) });
    setDeal(emptyDeal);
    setMessage(deal.id ? "团购套餐已更新" : "团购套餐已保存");
    loadAll();
  }

  async function toggleProduct(id: number) { await api(`/api/v1/merchant-admin/products/${id}/toggle`, { method: "POST" }); loadAll(); }
  async function toggleDeal(id: number) { await api(`/api/v1/merchant-admin/group-deals/${id}/toggle`, { method: "POST" }); loadAll(); }

  function editProduct(item: Product) { setProduct({ id: item.id, name: item.name, description: item.description, priceYuan: toYuan(item.priceCent), stock: String(item.stock), listed: item.listed }); }
  function editDeal(item: Deal) { setDeal({ id: item.id, title: item.title, description: item.description, priceYuan: toYuan(item.priceCent), stock: String(item.stock), active: item.active }); }

  async function deleteProduct(id: number) {
    if (!window.confirm("确认删除这个商品吗？")) return;
    await api(`/api/v1/merchant-admin/products/${id}/delete`, { method: "POST" });
    if (product.id === id) setProduct(emptyProduct);
    setProducts(c => c.filter(p => p.id !== id));
    setMessage("商品已删除");
    await loadAll();
  }

  async function deleteDeal(id: number) {
    if (!window.confirm("确认删除这个团购套餐吗？")) return;
    await api(`/api/v1/merchant-admin/group-deals/${id}/delete`, { method: "POST" });
    if (deal.id === id) setDeal(emptyDeal);
    setDeals(c => c.filter(d => d.id !== id));
    setMessage("团购套餐已删除");
    await loadAll();
  }

  return <>
    <section>
      <h3>商品维护</h3>
      <div className="form-grid">
        <input value={product.name} onChange={e => setProduct({ ...product, name: e.target.value })} placeholder="商品名" />
        <input value={product.description} onChange={e => setProduct({ ...product, description: e.target.value })} placeholder="描述" />
        <input type="number" min="0.01" step="0.01" value={product.priceYuan} onChange={e => setProduct({ ...product, priceYuan: e.target.value })} placeholder="单价（元）" />
        <input type="number" min="0" value={product.stock} onChange={e => setProduct({ ...product, stock: e.target.value })} placeholder="库存量" />
      </div>
      <div className="actions manage-actions">
        <button className="primary" onClick={saveProduct}><Utensils /> {product.id ? "更新商品" : "新增商品"}</button>
        {product.id && <button onClick={() => setProduct(emptyProduct)}>取消编辑</button>}
      </div>
      {products.map(p => <div className="line manage-line" key={p.id}>
        <span><b>{p.name}</b><small>价格 {money(p.priceCent)} · 库存 {p.stock} 份</small></span>
        <strong>{p.listed ? "上架" : "下架"}</strong>
        <button onClick={() => editProduct(p)}><Pencil /> 编辑</button>
        <button onClick={() => toggleProduct(p.id)}>{p.listed ? "下架" : "上架"}</button>
        <button onClick={() => deleteProduct(p.id)}><Trash2 /> 删除</button>
      </div>)}
    </section>

    <section>
      <h3>团购维护</h3>
      <div className="form-grid">
        <input value={deal.title} onChange={e => setDeal({ ...deal, title: e.target.value })} placeholder="套餐名" />
        <input value={deal.description} onChange={e => setDeal({ ...deal, description: e.target.value })} placeholder="描述" />
        <input type="number" min="0.01" step="0.01" value={deal.priceYuan} onChange={e => setDeal({ ...deal, priceYuan: e.target.value })} placeholder="单价（元）" />
        <input type="number" min="0" value={deal.stock} onChange={e => setDeal({ ...deal, stock: e.target.value })} placeholder="库存量" />
      </div>
      <div className="actions manage-actions">
        <button className="primary" onClick={saveDeal}>{deal.id ? "更新套餐" : "新增套餐"}</button>
        {deal.id && <button onClick={() => setDeal(emptyDeal)}>取消编辑</button>}
      </div>
      {deals.map(d => <div className="line manage-line" key={d.id}>
        <span><b>{d.title}</b><small>价格 {money(d.priceCent)} · 库存 {d.stock} 份</small></span>
        <strong>{d.active ? "上架" : "下架"}</strong>
        <button onClick={() => editDeal(d)}><Pencil /> 编辑</button>
        <button onClick={() => toggleDeal(d.id)}>{d.active ? "下架" : "上架"}</button>
        <button onClick={() => deleteDeal(d.id)}><Trash2 /> 删除</button>
      </div>)}
    </section>
  </>;
}
