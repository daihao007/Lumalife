import { Heart, Star } from "lucide-react";
import type { Merchant } from "../types";

export default function Favorites(props: {
  merchants: Merchant[];
  favoriteIds: number[];
  toggleFavorite: (id: number) => void;
  openMerchant: (id: number) => void;
  loadFavorites: () => void;
}) {
  const favorites = props.merchants.filter(m => props.favoriteIds.includes(m.id));

  return <div className="favorites-page">
    <h2>我的收藏 ({favorites.length})</h2>
    {favorites.length === 0 && <div className="empty-state">
      <Heart size={48} />
      <p>还没有收藏的商家</p>
      <p>在"发现"页面点击 ❤️ 即可收藏</p>
    </div>}
    <div className="merchant-grid">{favorites.map(m => <article className="merchant-card" key={m.id}>
      <div className="card-img-wrap">
        <img src={m.cover} onClick={() => props.openMerchant(m.id)} />
        <button className="fav-btn active" onClick={() => props.toggleFavorite(m.id)}>
          <Heart size={18} fill="var(--coral)" />
        </button>
      </div>
      <div onClick={() => props.openMerchant(m.id)}>
        <b>{m.name}</b>
        <span>{m.categoryName} · {m.distanceKm}km · 月售 {m.monthlySales}</span>
        <p><Star size={15} /> {m.avgScore} · 人均 ¥{m.avgPrice}</p>
      </div>
    </article>)}</div>
  </div>;
}
