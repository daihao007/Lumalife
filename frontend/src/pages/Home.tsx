import { Heart, RotateCcw, Search, SlidersHorizontal, Star } from "lucide-react";
import type { Category, Merchant } from "../types";

export default function Home(props: any) {
  return <>
    <div className="searchbar"><Search /><input value={props.keyword} onChange={(e) => props.setKeyword(e.target.value)} placeholder="请输入商品名称或商家名称" /><button onClick={() => props.searchMerchants()}>搜索</button></div>
    <div className="discover-controls">
      <label><span>排序方式</span><select value={props.sort} onChange={(e) => props.setSort(e.target.value)}>
        <option value="recommend">智能推荐</option>
        <option value="priceAsc">按照价格升序</option>
        <option value="priceDesc">按照价格降序</option>
        <option value="scoreAsc">按照评分升序</option>
        <option value="scoreDesc">按照评分降序</option>
        <option value="salesAsc">按照销量升序</option>
        <option value="salesDesc">按照销量降序</option>
        <option value="distanceAsc">按照距离升序</option>
        <option value="distanceDesc">按照距离降序</option>
      </select></label>
      <label><span>最低评分</span><select value={props.minScore} onChange={(e) => props.setMinScore(e.target.value)}>
        <option value="">不限</option>
        <option value="4.5">4.5 分以上</option>
        <option value="4.7">4.7 分以上</option>
      </select></label>
      <label><span>最低人均</span><input type="number" min="0" value={props.minPrice} onChange={(e) => props.setMinPrice(e.target.value)} placeholder="不限" /></label>
      <label><span>最高人均</span><input type="number" min="0" value={props.maxPrice} onChange={(e) => props.setMaxPrice(e.target.value)} placeholder="不限" /></label>
      <button className="primary" onClick={props.applyDiscoverFilters}><SlidersHorizontal size={16} /> 应用筛选</button>
    </div>
    <div className="chips">
      {props.activeCategoryId && <button className="reset-chip" onClick={props.clearMerchantFilter}><RotateCcw size={16} /> 返回全部商铺</button>}
      {props.categories.map((c: Category) => <button className={props.activeCategoryId === c.id ? "active" : ""} key={c.id} onClick={() => props.filterByCategory(c.id)}>{c.name}</button>)}
    </div>
    <div className="merchant-grid">{props.merchants.map((m: Merchant) => <article className="merchant-card" key={m.id}>
      <div className="card-img-wrap">
        <img src={m.cover} onClick={() => props.openMerchant(m.id)} />
        <button className={`fav-btn ${props.favoriteIds?.includes(m.id) ? "active" : ""}`}
          onClick={(e) => { e.stopPropagation(); props.toggleFavorite(m.id); }}>
          <Heart size={18} fill={props.favoriteIds?.includes(m.id) ? "var(--coral)" : "none"} />
        </button>
      </div>
      <div onClick={() => props.openMerchant(m.id)}><b>{m.name}</b><span>{m.categoryName} · {m.distanceKm}km · 月售 {m.monthlySales}</span><p><Star size={15} /> {m.avgScore} · 人均 ¥{m.avgPrice}</p><small>{m.reason}</small></div>
    </article>)}</div>
  </>;
}
