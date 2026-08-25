import type { Deal, Product } from "../types";
import { money } from "../utils";

export default function Detail({ detail, addCart, openCart, buyDeal, backHome, contactMerchant }: any) {
  return <div className="detail">
    <button className="back-button" onClick={backHome}>返回发现</button>
    <div className="merchant-hero" style={{ backgroundImage: `url(${detail.merchant.cover})` }}><h2>{detail.merchant.name}</h2><p>{detail.merchant.address} · {detail.merchant.reason}</p></div>
    <div className="split">
      <section><h3>菜品</h3>{detail.products.map((p: Product) => <div className="line" key={p.id}><span><b>{p.name}</b><small>{p.description}</small></span><strong>{money(p.priceCent)}</strong><button onClick={() => addCart(p.id)}>加购</button></div>)}<div className="actions"><button className="primary" onClick={openCart}>去购物车结算</button><button onClick={() => contactMerchant(detail.merchant.id)}>联系店家客服</button></div></section>
      <section><h3>团购</h3>{detail.groupDeals.map((d: Deal) => <div className="line" key={d.id}><span><b>{d.title}</b><small>{d.description}</small></span><strong>{money(d.priceCent)}</strong><button onClick={() => buyDeal(d.id)}>购买</button></div>)}<h3>评价</h3>{detail.reviews.map((r: any) => <p className="review" key={r.id}>★ {r.score} {r.userName}：{r.content}</p>)}</section>
    </div>
  </div>;
}
