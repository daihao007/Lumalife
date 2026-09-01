package com.lumalife.merchant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** Merchant-owned catalog slice. Product writes are kept behind this service boundary. */
@Service
public class MerchantStore {
  public record Merchant(long id, String name, long categoryId, String categoryName, String cover, double avgScore,
                         int avgPrice, int monthlySales, double distanceKm, String status, String address, String reason) {}
  public record Product(long id, long merchantId, String name, String description, long priceCent, int stock, boolean listed) {}
  public record GroupDeal(long id, long merchantId, String title, String description, long priceCent, int stock, boolean active) {}

  private final AtomicLong ids = new AtomicLong(3000);
  private final Map<Long, Merchant> merchants = new LinkedHashMap<>();
  private final Map<Long, List<Product>> products = new LinkedHashMap<>();
  private final Map<Long, GroupDeal> deals = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;

  @Autowired
  public MerchantStore(ObjectProvider<JdbcTemplate> jdbcProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
    seedMerchants();
    products.put(1L, new ArrayList<>(List.of(
      new Product(1001, 1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680, 88, true),
      new Product(1002, 1, "毛血旺小锅", "课程演示热门搜索菜", 4280, 120, true))));
    deals.put(1L, new GroupDeal(1, 1, "双人川味套餐", "含招牌菜和饮品", 4880, 50, true));
  }

  public MerchantStore() {
    this.jdbc = null;
    seedMerchants();
    products.put(1L, new ArrayList<>(List.of(
      new Product(1001, 1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680, 88, true),
      new Product(1002, 1, "毛血旺小锅", "课程演示热门搜索菜", 4280, 120, true))));
    deals.put(1L, new GroupDeal(1, 1, "双人川味套餐", "含招牌菜和饮品", 4880, 50, true));
  }

  public synchronized List<Merchant> search(String keyword) {
    return search(keyword, null, "recommend", null, null, null);
  }

  public synchronized List<Merchant> search(String keyword, Long categoryId, String sort,
                                             Integer minPrice, Integer maxPrice, Double minScore) {
    String normalized = keyword == null ? "" : keyword.trim();
    if (jdbc != null) {
      Map<Long, Merchant> merged = new LinkedHashMap<>(merchants);
      String like = "%" + normalized + "%";
      String sql = "SELECT DISTINCT m.id,m.name,m.category_id,c.name,m.cover_url,m.avg_score,m.avg_price_cent,m.monthly_sales,m.distance_km,m.status,m.address,m.recommend_reason "
        + "FROM merchant m JOIN category c ON c.id=m.category_id LEFT JOIN merchant_catalog p ON p.merchant_id=m.id "
        + "WHERE m.is_deleted=0 AND (?='' OR m.name LIKE ? OR p.name LIKE ?)";
      jdbc.query(sql, this::mapMerchant, normalized, like, like)
        .forEach(item -> merged.put(item.id(), item));
      return filterAndSort(merged.values().stream(), normalized, categoryId, sort, minPrice, maxPrice, minScore);
    }
    return filterAndSort(merchants.values().stream(), normalized, categoryId, sort, minPrice, maxPrice, minScore);
  }

  private List<Merchant> filterAndSort(java.util.stream.Stream<Merchant> source, String keyword, Long categoryId,
                                       String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return source
      .filter(item -> categoryId == null || item.categoryId() == categoryId)
      .filter(item -> keyword.isBlank() || item.name().contains(keyword) || products(item.id()).stream().anyMatch(p -> p.name().contains(keyword)))
      .filter(item -> minPrice == null || item.avgPrice() >= minPrice)
      .filter(item -> maxPrice == null || item.avgPrice() <= maxPrice)
      .filter(item -> minScore == null || item.avgScore() >= minScore)
      .sorted(merchantComparator(sort))
      .toList();
  }

  private Comparator<Merchant> merchantComparator(String sort) {
    return switch (sort == null ? "recommend" : sort) {
      case "priceAsc" -> Comparator.comparingInt(Merchant::avgPrice).thenComparingLong(Merchant::id);
      case "priceDesc" -> Comparator.comparingInt(Merchant::avgPrice).reversed().thenComparingLong(Merchant::id);
      case "scoreAsc" -> Comparator.comparingDouble(Merchant::avgScore).thenComparingLong(Merchant::id);
      case "scoreDesc" -> Comparator.comparingDouble(Merchant::avgScore).reversed().thenComparingLong(Merchant::id);
      case "salesAsc" -> Comparator.comparingInt(Merchant::monthlySales).thenComparingLong(Merchant::id);
      case "salesDesc" -> Comparator.comparingInt(Merchant::monthlySales).reversed().thenComparingLong(Merchant::id);
      case "distanceAsc", "distance" -> Comparator.comparingDouble(Merchant::distanceKm).thenComparingLong(Merchant::id);
      case "distanceDesc" -> Comparator.comparingDouble(Merchant::distanceKm).reversed().thenComparingLong(Merchant::id);
      default -> Comparator.comparingDouble((Merchant m) -> -(0.6 * (m.avgScore() / 5.0) + 0.4 * Math.max(0, 1 - m.distanceKm() / 5.0)))
        .thenComparingLong(Merchant::id);
    };
  }

  public synchronized Merchant merchant(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT m.id,m.name,m.category_id,c.name,m.cover_url,m.avg_score,m.avg_price_cent,m.monthly_sales,m.distance_km,m.status,m.address,m.recommend_reason FROM merchant m JOIN category c ON c.id=m.category_id WHERE m.id=? AND m.is_deleted=0", this::mapMerchant, id);
      if (!rows.isEmpty()) return rows.get(0);
    }
    Merchant merchant = merchants.get(id);
    if (merchant == null) throw new IllegalArgumentException("商家不存在");
    return merchant;
  }

  public synchronized Merchant renameMerchant(long id, String nickname) {
    Merchant current = merchant(id);
    if (nickname == null || nickname.isBlank()) throw new IllegalArgumentException("商家昵称不能为空");
    String nextName = nickname.trim();
    if (nextName.length() > 64) throw new IllegalArgumentException("商家昵称不能超过 64 个字符");
    if (jdbc != null) {
      int updated = jdbc.update("UPDATE merchant SET name=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND is_deleted=0", nextName, id);
      if (updated != 1) throw new IllegalArgumentException("商家不存在");
    }
    Merchant renamed = new Merchant(current.id(), nextName, current.categoryId(), current.categoryName(),
      current.cover(), current.avgScore(), current.avgPrice(), current.monthlySales(), current.distanceKm(),
      current.status(), current.address(), current.reason());
    merchants.put(id, renamed);
    return renamed;
  }

  public synchronized List<Product> products(long merchantId) {
    merchant(merchantId);
    if (jdbc != null) return jdbc.query("SELECT id,merchant_id,name,description,price_cent,stock,listed FROM merchant_catalog WHERE merchant_id=? ORDER BY id", (rs,n) -> new Product(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6),rs.getBoolean(7)), merchantId);
    return new ArrayList<>(products.getOrDefault(merchantId, List.of()));
  }

  public synchronized Product saveProduct(long merchantId, ProductRequest request) {
    merchant(merchantId);
    if (request.name() == null || request.name().isBlank() || request.priceCent() <= 0 || request.stock() < 0) {
      throw new IllegalArgumentException("商品名称、价格和库存必须合法");
    }
    List<Product> owned = products.computeIfAbsent(merchantId, ignored -> new ArrayList<>());
    long id = request.id() == null ? ids.incrementAndGet() : request.id();
    Product product = new Product(id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    if (jdbc != null) jdbc.update("INSERT INTO merchant_catalog(id,merchant_id,name,description,price_cent,stock,listed) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),price_cent=VALUES(price_cent),stock=VALUES(stock),listed=VALUES(listed)", id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    owned.removeIf(item -> item.id() == id);
    owned.add(product);
    return product;
  }

  public synchronized GroupDeal deal(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT id,merchant_id,title,description,price_cent,stock,is_active FROM group_deal WHERE id=? AND is_deleted=0", (rs,n) -> new GroupDeal(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7)), id);
      if (!rows.isEmpty()) return rows.get(0);
    }
    GroupDeal deal = deals.get(id);
    if (deal == null) throw new IllegalArgumentException("团购套餐不存在");
    return deal;
  }

  public synchronized Product product(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT id,merchant_id,name,description,price_cent,stock,listed FROM merchant_catalog WHERE id=?", (rs,n) -> new Product(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6),rs.getBoolean(7)), id);
      if (!rows.isEmpty()) return rows.get(0);
    }
    return products.values().stream().flatMap(List::stream).filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
  }

  public synchronized List<GroupDeal> deals(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT id,merchant_id,title,description,price_cent,stock,is_active FROM group_deal WHERE merchant_id=? AND is_deleted=0 ORDER BY id", (rs,n) -> new GroupDeal(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7)), merchantId);
    return deals.values().stream().filter(item -> item.merchantId() == merchantId).toList();
  }

  public synchronized GroupDeal saveDeal(long merchantId, DealRequest request) {
    merchant(merchantId);
    if (request.title() == null || request.title().isBlank() || request.priceCent() <= 0 || request.stock() < 0) {
      throw new IllegalArgumentException("套餐名称、价格和库存必须合法");
    }
    long id = request.id() == null ? ids.incrementAndGet() : request.id();
    GroupDeal deal = new GroupDeal(id, merchantId, request.title(), request.description(), request.priceCent(), request.stock(), request.active());
    if (jdbc != null) jdbc.update("INSERT INTO group_deal(id,merchant_id,title,description,price_cent,stock,is_active,is_deleted) VALUES (?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE merchant_id=VALUES(merchant_id),title=VALUES(title),description=VALUES(description),price_cent=VALUES(price_cent),stock=VALUES(stock),is_active=VALUES(is_active),is_deleted=0",
      id, merchantId, request.title(), request.description(), request.priceCent(), request.stock(), request.active());
    deals.put(id, deal); return deal;
  }

  public synchronized GroupDeal toggleDeal(long merchantId, long id) {
    GroupDeal old = deal(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该套餐");
    GroupDeal updated = new GroupDeal(old.id(), old.merchantId(), old.title(), old.description(), old.priceCent(), old.stock(), !old.active());
    if (jdbc != null) jdbc.update("UPDATE group_deal SET is_active=? WHERE id=? AND merchant_id=? AND is_deleted=0", updated.active(), id, merchantId);
    deals.put(id, updated); return updated;
  }

  public synchronized void deleteDeal(long merchantId, long id) {
    GroupDeal old = deal(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该套餐");
    if (jdbc != null) jdbc.update("UPDATE group_deal SET is_deleted=1 WHERE id=? AND merchant_id=?", id, merchantId);
    deals.remove(id);
  }

  public synchronized Product toggleProduct(long merchantId, long id) {
    Product old = product(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该商品");
    return saveProduct(merchantId, new ProductRequest(id, old.name(), old.description(), old.priceCent(), old.stock(), !old.listed()));
  }

  public synchronized void deleteProduct(long merchantId, long id) {
    Product old = product(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该商品");
    products.getOrDefault(merchantId, List.of()).removeIf(item -> item.id() == id);
    if (jdbc != null) jdbc.update("DELETE FROM merchant_catalog WHERE id=? AND merchant_id=?", id, merchantId);
  }

  record ProductRequest(Long id, String name, String description, long priceCent, int stock, boolean listed) {}
  record DealRequest(Long id, String title, String description, long priceCent, int stock, boolean active) {}

  private void seedMerchants() {
    merchants.put(1L, new Merchant(1, "巷口川味研究所", 1, "川湘菜", "https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=1000&q=80", 4.8, 38, 386, 1.2, "营业中", "梧桐路 18 号", "评分高、距离近、近期销量较好"));
    merchants.put(2L, new Merchant(2, "晨雾咖啡局", 2, "咖啡茶饮", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=1000&q=80", 4.7, 32, 268, 0.7, "营业中", "湖畔街 3 号", "距离近、评价稳定"));
    merchants.put(3L, new Merchant(3, "绿盒轻食", 3, "轻食简餐", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1000&q=80", 4.5, 29, 189, 2.4, "营业中", "学院路 66 号", "热量标注清晰、复购高"));
    merchants.put(4L, new Merchant(4, "栗香烘焙室", 4, "甜品烘焙", "https://images.unsplash.com/photo-1486427944299-d1955d23e34d?auto=format&fit=crop&w=1000&q=80", 4.6, 26, 142, 3.1, "营业中", "银杏街 9 号", "甜品评分高"));
  }

  private Merchant mapMerchant(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Merchant(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getInt(7) / 100, rs.getInt(8), rs.getDouble(9), rs.getString(10), rs.getString(11), rs.getString(12));
  }
}
