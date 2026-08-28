package com.lumalife.merchant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;

/** Merchant-owned catalog slice. Product writes are kept behind this service boundary. */
@Service
public class MerchantStore {
  public record Merchant(long id, String name, long categoryId, String categoryName, String status) {}
  public record Product(long id, long merchantId, String name, String description, long priceCent, int stock, boolean listed) {}

  private final AtomicLong ids = new AtomicLong(3000);
  private final Map<Long, Merchant> merchants = new LinkedHashMap<>();
  private final Map<Long, List<Product>> products = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;

  public MerchantStore(ObjectProvider<JdbcTemplate> jdbcProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
    merchants.put(1L, new Merchant(1, "巷口川味研究所", 1, "川湘菜", "OPEN"));
    merchants.put(2L, new Merchant(2, "晨雾咖啡局", 2, "咖啡茶饮", "OPEN"));
    products.put(1L, new ArrayList<>(List.of(
      new Product(1001, 1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680, 88, true),
      new Product(1002, 1, "毛血旺小锅", "课程演示热门搜索菜", 4280, 120, true))));
  }

  public MerchantStore() { this.jdbc = null; merchants.put(1L, new Merchant(1, "巷口川味研究所", 1, "川湘菜", "OPEN")); merchants.put(2L, new Merchant(2, "晨雾咖啡局", 2, "咖啡茶饮", "OPEN")); products.put(1L, new ArrayList<>(List.of(new Product(1001,1,"藤椒鸡饭","麻香鲜亮，适合午餐",2680,88,true), new Product(1002,1,"毛血旺小锅","课程演示热门搜索菜",4280,120,true))); }

  public synchronized List<Merchant> search(String keyword) {
    String normalized = keyword == null ? "" : keyword.trim();
    return merchants.values().stream().filter(item -> normalized.isBlank() || item.name().contains(normalized)).toList();
  }

  public synchronized Merchant merchant(long id) {
    Merchant merchant = merchants.get(id);
    if (merchant == null) throw new IllegalArgumentException("商家不存在");
    return merchant;
  }

  public synchronized List<Product> products(long merchantId) {
    merchant(merchantId);
    if (jdbc != null) return jdbc.query("SELECT id,merchant_id,name,description,price_cent,stock,listed FROM merchant_catalog WHERE merchant_id=? ORDER BY id", (rs,n) -> new Product(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6),rs.getBoolean(7)), merchantId);
    return new ArrayList<>(products.getOrDefault(merchantId, List.of()));
  }

  public synchronized Product saveProduct(long merchantId, ProductRequest request) {
    merchant(merchantId);
    List<Product> owned = products.computeIfAbsent(merchantId, ignored -> new ArrayList<>());
    long id = request.id() == null ? ids.incrementAndGet() : request.id();
    Product product = new Product(id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    if (jdbc != null) jdbc.update("INSERT INTO merchant_catalog(id,merchant_id,name,description,price_cent,stock,listed) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),price_cent=VALUES(price_cent),stock=VALUES(stock),listed=VALUES(listed)", id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    owned.removeIf(item -> item.id() == id);
    owned.add(product);
    return product;
  }

  record ProductRequest(Long id, String name, String description, long priceCent, int stock, boolean listed) {}
}
