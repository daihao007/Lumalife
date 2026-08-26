import { useRef, useState } from "react";
import { CreditCard, Minus, Plus, ShoppingCart, Trash2 } from "lucide-react";
import { api } from "../api";
import type { Address, CartGroup, CartLine } from "../types";
import { money } from "../utils";

export default function Cart({ cart, addresses, selectedAddressId, setSelectedAddressId, reload, createDeliveryOrder, setMessage }: any) {
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const actionLock = useRef(false);
  const total = cart.reduce((sum: number, item: CartLine) => sum + item.subtotalCent, 0);
  const groups: CartGroup[] = Object.values(cart.reduce((map: Record<number, CartGroup>, item: CartLine) => {
    map[item.merchantId] ||= { merchantId: item.merchantId, merchantName: item.merchantName || "未知商家", items: [], subtotalCent: 0 };
    map[item.merchantId].items.push(item);
    map[item.merchantId].subtotalCent += item.subtotalCent;
    return map;
  }, {}));

  async function runAction(key: string, action: () => Promise<void>) {
    if (actionLock.current) return;
    actionLock.current = true;
    setPendingAction(key);
    try {
      await action();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "购物车操作失败，请重试");
    } finally {
      actionLock.current = false;
      setPendingAction(null);
    }
  }

  async function update(productId: number, quantity: number) {
    await runAction(`quantity-${productId}`, async () => {
      await api(`/api/v1/cart/items/${productId}`, { method: "POST", body: JSON.stringify({ quantity }) });
      setMessage(quantity > 0 ? "购物车数量已更新" : "商品已移出购物车");
      await reload();
    });
  }

  async function remove(productId: number) {
    await runAction(`remove-${productId}`, async () => {
      await api(`/api/v1/cart/items/${productId}/delete`, { method: "POST" });
      setMessage("商品已移出购物车");
      await reload();
    });
  }

  async function clear() {
    await runAction("clear", async () => {
      await api("/api/v1/cart/clear", { method: "POST" });
      setMessage("购物车已清空");
      await reload();
    });
  }

  async function submitOrder() {
    if (!selectedAddressId) {
      setMessage("请选择收货地址后再下单");
      return;
    }
    await runAction("checkout", createDeliveryOrder);
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
            <button title="减少" aria-label={`减少 ${item.name} 数量`} disabled={pendingAction !== null} onClick={() => update(item.productId, item.quantity - 1)}><Minus size={16} /></button>
            <strong>{item.quantity}</strong>
            <button title="增加" aria-label={`增加 ${item.name} 数量`} disabled={pendingAction !== null} onClick={() => update(item.productId, item.quantity + 1)}><Plus size={16} /></button>
          </div>
          <strong>{money(item.subtotalCent)}</strong>
          <button title="删除" aria-label={`删除 ${item.name}`} disabled={pendingAction !== null} onClick={() => remove(item.productId)}><Trash2 size={16} /></button>
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
      <button className="primary" data-testid="checkout-submit" disabled={!selectedAddressId || pendingAction !== null} onClick={submitOrder}><CreditCard /> {pendingAction === "checkout" ? "订单创建中…" : "创建待支付订单"}</button>
      <button data-testid="cart-clear" disabled={pendingAction !== null} onClick={clear}><Trash2 size={16} /> {pendingAction === "clear" ? "清空中…" : "清空购物车"}</button>
    </section>
  </div>;
}
