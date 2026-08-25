import { CreditCard, Minus, Plus, ShoppingCart, Trash2 } from "lucide-react";
import { api } from "../api";
import type { Address, CartGroup, CartLine } from "../types";
import { money } from "../utils";

export default function Cart({ cart, addresses, selectedAddressId, setSelectedAddressId, reload, createDeliveryOrder, setMessage }: any) {
  const total = cart.reduce((sum: number, item: CartLine) => sum + item.subtotalCent, 0);
  const groups: CartGroup[] = Object.values(cart.reduce((map: Record<number, CartGroup>, item: CartLine) => {
    map[item.merchantId] ||= { merchantId: item.merchantId, merchantName: item.merchantName || "未知商家", items: [], subtotalCent: 0 };
    map[item.merchantId].items.push(item);
    map[item.merchantId].subtotalCent += item.subtotalCent;
    return map;
  }, {}));

  async function update(productId: number, quantity: number) {
    await api(`/api/v1/cart/items/${productId}`, { method: "POST", body: JSON.stringify({ quantity }) });
    setMessage(quantity > 0 ? "购物车数量已更新" : "商品已移出购物车");
    reload();
  }

  async function remove(productId: number) {
    await api(`/api/v1/cart/items/${productId}/delete`, { method: "POST" });
    setMessage("商品已移出购物车");
    reload();
  }

  async function clear() {
    await api("/api/v1/cart/clear", { method: "POST" });
    setMessage("购物车已清空");
    reload();
  }

  if (!cart.length) return <div className="panel empty-state"><ShoppingCart /><h2>购物车是空的</h2><p>从商家详情页加购菜品后，可以在这里调整数量、清空或创建待支付订单。</p></div>;
  return <div className="cart-view">
    <section>
      <h3>购物车</h3>
      {groups.map(group => <div className="cart-group" key={group.merchantId}>
        <div className="cart-group-title"><b>{group.merchantName}</b><span>{money(group.subtotalCent)}</span></div>
        {group.items.map((item: CartLine) => <div className="line cart-line" key={item.productId}>
          <span><b>{item.name}</b><small>{money(item.priceCent)} / 份</small></span>
          <div className="stepper">
            <button title="减少" onClick={() => update(item.productId, item.quantity - 1)}><Minus size={16} /></button>
            <strong>{item.quantity}</strong>
            <button title="增加" onClick={() => update(item.productId, item.quantity + 1)}><Plus size={16} /></button>
          </div>
          <strong>{money(item.subtotalCent)}</strong>
          <button title="删除" onClick={() => remove(item.productId)}><Trash2 size={16} /></button>
        </div>)}
      </div>)}
    </section>
    <section className="checkout">
      <h3>结算</h3>
      <div className="address-picker">
        <b>收货地址<span>下单将记录当前地址快照</span></b>
        {addresses.length ? addresses.map((address: Address) => <label key={address.id} className={selectedAddressId === address.id ? "address-option active" : "address-option"}>
          <input type="radio" checked={selectedAddressId === address.id} onChange={() => setSelectedAddressId(address.id)} />
          <span>{address.contactName} · {address.phone}<small>{address.detail}</small></span>
        </label>) : <p className="hint">请先在地址页新增收货地址。</p>}
      </div>
      <b>{money(total)}<span>商品合计</span></b>
      <button className="primary" disabled={!addresses.length} onClick={createDeliveryOrder}><CreditCard /> 创建待支付订单</button>
      <button onClick={clear}><Trash2 size={16} /> 清空购物车</button>
    </section>
  </div>;
}
