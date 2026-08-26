package com.lumalife.service;

import com.lumalife.common.BusinessException;
import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Enums.OrderType;
import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.CartLine;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.Category;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.OperationLog;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.OrderLine;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DemoStore {
  private static final String DEFAULT_CHUANXIANG_COVER = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=1000&q=80";
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path stateFile;
  private final boolean persistenceEnabled;
  private final AtomicLong ids = new AtomicLong(1000);
  private final Map<String, User> users = new LinkedHashMap<>();
  private final Map<String, Long> tokens = new HashMap<>();
  private final Map<Long, Category> categories = new LinkedHashMap<>();
  private final Map<Long, List<Address>> addresses = new HashMap<>();
  private final Map<Long, Merchant> merchants = new LinkedHashMap<>();
  private final Map<Long, Product> products = new LinkedHashMap<>();
  private final Map<Long, GroupDeal> deals = new LinkedHashMap<>();
  private final Map<Long, List<CartItem>> carts = new HashMap<>();
  private final Map<Long, Order> orders = new LinkedHashMap<>();
  private final Map<String, Long> couponIndex = new HashMap<>();
  private final Map<Long, Review> reviews = new LinkedHashMap<>();
  private final Map<String, List<ChatMessage>> conversations = new LinkedHashMap<>();
  private final Map<Long, Set<Long>> favorites = new HashMap<>();
  private final List<OperationLog> logs = new ArrayList<>();

  @Autowired
  public DemoStore(PasswordEncoder passwordEncoder, @Value("${lumalife.state-file:./data/lumalife-state.json}") String stateFile) {
    this(passwordEncoder, stateFile == null || stateFile.isBlank() ? null : Path.of(stateFile), true);
  }

  public DemoStore(PasswordEncoder passwordEncoder) {
    this(passwordEncoder, null, false);
  }

  DemoStore(PasswordEncoder passwordEncoder, Path stateFile) {
    this(passwordEncoder, stateFile, true);
  }

  private DemoStore(PasswordEncoder passwordEncoder, Path stateFile, boolean persistenceEnabled) {
    this.passwordEncoder = passwordEncoder;
    this.stateFile = stateFile;
    this.persistenceEnabled = persistenceEnabled && stateFile != null;
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    seed();
    loadPersistentState();
    syncMerchantNamesFromAccounts();
  }

  public Optional<User> userByToken(String token) {
    Long id = tokens.get(token);
    if (id == null) return Optional.empty();
    return users.values().stream().filter(u -> u.id() == id).findFirst().map(this::syncMerchantNameFromAccount);
  }

  public User userByPhone(String phone) {
    String username = phone == null ? "" : phone.trim();
    return Optional.ofNullable(users.get(username)).orElseThrow(() -> new BusinessException(40100, "用户名不存在"));
  }

  public User current(String phone) {
    return syncMerchantNameFromAccount(userByPhone(phone));
  }

  public Map<String, Object> login(String phone, String password) {
    String username = phone == null ? "" : phone.trim();
    User user = syncMerchantNameFromAccount(userByPhone(username));
    if (!passwordEncoder.matches(password, user.password())) {
      throw new BusinessException(40100, "密码错误");
    }
    String token = UUID.randomUUID().toString();
    tokens.put(token, user.id());
    log(user.nickname(), "登录系统");
    return Map.of("token", token, "user", safeUser(user));
  }

  public Map<String, Object> register(String phone, String password, String nickname) {
    return register(phone, password, nickname, UserRole.USER);
  }

  public Map<String, Object> register(String phone, String password, String nickname, UserRole role) {
    if (phone == null || phone.isBlank()) {
      throw new BusinessException(40000, "请输入用户名");
    }
    if (password == null || password.length() < 6) {
      throw new BusinessException(40000, "请输入至少6位的密码");
    }
    String username = phone.trim();
    if (users.containsKey(username)) {
      throw new BusinessException(40900, "用户名已注册");
    }
    UserRole nextRole = role == UserRole.MERCHANT_ADMIN ? UserRole.MERCHANT_ADMIN : UserRole.USER;
    String displayName = nickname == null || nickname.isBlank() ? "新用户" + username : nickname.trim();
    Long merchantId = nextRole == UserRole.MERCHANT_ADMIN ? createMerchantFor(displayName) : null;
    User user = new User(ids.incrementAndGet(), username, passwordEncoder.encode(password), displayName, "", nextRole, merchantId);
    users.put(username, user);
    persistState();
    log(displayName, nextRole == UserRole.MERCHANT_ADMIN ? "注册商家账号" : "注册普通用户账号");
    return login(username, password);
  }

  public Map<String, Object> safeUser(User user) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.id());
    data.put("phone", user.phone());
    data.put("nickname", user.nickname());
    data.put("avatarUrl", user.avatarUrl());
    data.put("role", user.role());
    data.put("merchantId", user.merchantId());
    return data;
  }

  public Map<String, Object> updateProfile(User user, String nickname, String avatarUrl) {
    String nextNickname = nickname == null || nickname.isBlank() ? user.nickname() : nickname.trim();
    String nextAvatar = avatarUrl == null ? user.avatarUrl() : avatarUrl.trim();
    User updated = new User(user.id(), user.phone(), user.password(), nextNickname, nextAvatar, user.role(), user.merchantId());
    users.put(updated.phone(), updated);
    if (updated.role() == UserRole.MERCHANT_ADMIN && updated.merchantId() != null && merchants.containsKey(updated.merchantId())) {
      renameMerchant(updated.merchantId(), nextNickname);
      persistState();
    }
    log(nextNickname, "更新个人资料");
    return safeUser(updated);
  }

  public Map<String, Object> merchantProfile(User admin) {
    ensureMerchantAdmin(admin);
    return Map.of("user", safeUser(admin), "merchant", withLiveStats(merchants.get(admin.merchantId())));
  }

  public Map<String, Object> updateMerchantNickname(User admin, String nickname) {
    ensureMerchantAdmin(admin);
    if (nickname == null || nickname.isBlank()) {
      throw new BusinessException(40000, "商家昵称不能为空");
    }
    String nextName = nickname.trim();
    if (nextName.length() > 64) {
      throw new BusinessException(40000, "商家昵称不能超过 64 个字符");
    }
    Merchant renamed = renameMerchant(admin.merchantId(), nextName);
    User updated = new User(admin.id(), admin.phone(), admin.password(), nextName, admin.avatarUrl(), admin.role(), admin.merchantId());
    users.put(updated.phone(), updated);
    persistState();
    log(nextName, "更新商家昵称");
    return Map.of("user", safeUser(updated), "merchant", withLiveStats(renamed));
  }

  public List<Address> addresses(User user) {
    return new ArrayList<>(addresses.computeIfAbsent(user.id(), ignored -> new ArrayList<>()));
  }

  public Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress) {
    List<Address> list = addresses.computeIfAbsent(user.id(), ignored -> new ArrayList<>());
    if (id == null && list.size() >= 5) {
      throw new BusinessException(40900, "每个用户最多维护 5 个收货地址");
    }
    if (defaultAddress) {
      list.replaceAll(a -> new Address(a.id(), a.userId(), a.contactName(), a.phone(), a.detail(), false));
    }
    Address address = new Address(id == null ? ids.incrementAndGet() : id, user.id(), contactName, phone, detail, defaultAddress || list.isEmpty());
    list.removeIf(a -> a.id() == address.id());
    list.add(address);
    log(user.nickname(), (id == null ? "新增" : "编辑") + "收货地址");
    return address;
  }

  public void deleteAddress(User user, long id) {
    List<Address> list = addresses.computeIfAbsent(user.id(), ignored -> new ArrayList<>());
    boolean removed = list.removeIf(a -> a.id() == id);
    if (!removed) throw new BusinessException(40400, "地址不存在");
    if (!list.isEmpty() && list.stream().noneMatch(Address::defaultAddress)) {
      Address first = list.remove(0);
      list.add(0, new Address(first.id(), first.userId(), first.contactName(), first.phone(), first.detail(), true));
    }
    log(user.nickname(), "删除收货地址");
  }

  public Address setDefaultAddress(User user, long id) {
    List<Address> list = addresses.computeIfAbsent(user.id(), ignored -> new ArrayList<>());
    if (list.stream().noneMatch(a -> a.id() == id)) throw new BusinessException(40400, "地址不存在");
    list.replaceAll(a -> new Address(a.id(), a.userId(), a.contactName(), a.phone(), a.detail(), a.id() == id));
    log(user.nickname(), "设置默认收货地址");
    return list.stream().filter(a -> a.id() == id).findFirst().orElseThrow();
  }

  public List<Category> categories() {
    return new ArrayList<>(categories.values());
  }

  public List<Merchant> merchants(String keyword, Long categoryId, String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return merchants.values().stream()
      .map(this::withLiveStats)
      .filter(m -> categoryId == null || m.categoryId() == categoryId)
      .filter(m -> keyword == null || keyword.isBlank() || m.name().contains(keyword)
        || products.values().stream().anyMatch(p -> p.merchantId() == m.id() && p.name().contains(keyword)))
      .filter(m -> minPrice == null || m.avgPrice() >= minPrice)
      .filter(m -> maxPrice == null || m.avgPrice() <= maxPrice)
      .filter(m -> minScore == null || m.avgScore() >= minScore)
      .sorted(merchantComparator(sort))
      .toList();
  }

  private Comparator<Merchant> recommendComparator() {
    return Comparator.comparingDouble((Merchant m) -> -(0.6 * (m.avgScore() / 5.0) + 0.4 * Math.max(0, 1 - m.distanceKm() / 5.0)));
  }

  private Comparator<Merchant> merchantComparator(String sort) {
    return switch (sort == null ? "recommend" : sort) {
      case "priceAsc" -> Comparator.comparingInt(Merchant::avgPrice);
      case "priceDesc" -> Comparator.comparingInt(Merchant::avgPrice).reversed();
      case "scoreAsc" -> Comparator.comparingDouble(Merchant::avgScore);
      case "scoreDesc" -> Comparator.comparingDouble(Merchant::avgScore).reversed();
      case "salesAsc" -> Comparator.comparingInt(Merchant::monthlySales);
      case "salesDesc" -> Comparator.comparingInt(Merchant::monthlySales).reversed();
      case "distanceAsc", "distance" -> Comparator.comparingDouble(Merchant::distanceKm);
      case "distanceDesc" -> Comparator.comparingDouble(Merchant::distanceKm).reversed();
      default -> recommendComparator();
    };
  }

  // ==================== 个性化推荐引擎 ====================

  /**
   * 个性化推荐：返回带动态 reason 的商户列表
   */
  public List<Map<String, Object>> merchantsForUser(long userId, String keyword, Long categoryId,
                                                     String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    boolean personalized = "recommend".equals(sort) || sort == null;
    Map<Long, Double> categoryPref = personalized ? userCategoryPreference(userId) : Map.of();
    double avgSpend = personalized ? userAvgSpend(userId) : 0;
    Map<Long, Double> merchantScoreMap = personalized ? userMerchantScoreMap(userId) : Map.of();
    Set<Long> orderedMerchantIds = personalized ? userOrderedMerchantIds(userId) : Set.of();
    boolean hasHistory = personalized && !categoryPref.isEmpty();

    return merchants.values().stream()
      .map(this::withLiveStats)
      .filter(m -> categoryId == null || m.categoryId() == categoryId)
      .filter(m -> keyword == null || keyword.isBlank() || m.name().contains(keyword)
        || products.values().stream().anyMatch(p -> p.merchantId() == m.id() && p.name().contains(keyword)))
      .filter(m -> minPrice == null || m.avgPrice() >= minPrice)
      .filter(m -> maxPrice == null || m.avgPrice() <= maxPrice)
      .filter(m -> minScore == null || m.avgScore() >= minScore)
      .map(m -> {
        Map<String, Object> item = merchantToMap(m);
        if (personalized) {
          double[] scored = scoreMerchant(m, categoryPref, avgSpend, merchantScoreMap, orderedMerchantIds, hasHistory);
          item.put("recommendScore", Math.round(scored[0] * 1000.0) / 1000.0);
          item.put("reason", generateReason(m, scored[1], scored[2], scored[3], scored[4], scored[5], hasHistory));
        } else {
          item.put("reason", m.reason());
        }
        return item;
      })
      .sorted(personalized
        ? Comparator.<Map<String, Object>, Double>comparing(d -> (Double) d.get("recommendScore")).reversed()
        : Comparator.comparingDouble((Map<String, Object> d) -> {
            Merchant m = merchants.get(((Number) d.get("id")).longValue());
            return m == null ? 0 : -(0.6 * (m.avgScore() / 5.0) + 0.4 * Math.max(0, 1 - m.distanceKm() / 5.0));
          }))
      .toList();
  }

  /**
   * 用户品类偏好：categoryId -> 偏好权重 (0~1)
   */
  private Map<Long, Double> userCategoryPreference(long userId) {
    Map<Long, Long> countMap = orders.values().stream()
      .filter(o -> o.userId == userId && o.status != OrderStatus.CANCELLED)
      .collect(Collectors.groupingBy(o -> o.merchantId, Collectors.counting()));
    if (countMap.isEmpty()) return Map.of();
    // merchant -> category
    Map<Long, Long> merchantCategory = merchants.values().stream()
      .collect(Collectors.toMap(Merchant::id, Merchant::categoryId));
    Map<Long, Long> catCount = new HashMap<>();
    countMap.forEach((mId, cnt) -> catCount.merge(merchantCategory.getOrDefault(mId, 0L), cnt, Long::sum));
    long total = catCount.values().stream().mapToLong(Long::longValue).sum();
    Map<Long, Double> result = new HashMap<>();
    catCount.forEach((catId, cnt) -> result.put(catId, (double) cnt / total));
    return result;
  }

  /**
   * 用户平均客单价（分）
   */
  private double userAvgSpend(long userId) {
    return orders.values().stream()
      .filter(o -> o.userId == userId && o.status != OrderStatus.CANCELLED)
      .mapToLong(o -> o.totalCent)
      .average().orElse(0);
  }

  /**
   * 用户对各商家的评分信号：merchantId -> 平均评分 (1~5)
   */
  private Map<Long, Double> userMerchantScoreMap(long userId) {
    // 通过 order -> review 关联
    Set<Long> userOrderIds = orders.values().stream()
      .filter(o -> o.userId == userId).map(o -> o.id).collect(Collectors.toSet());
    return reviews.values().stream()
      .filter(r -> userOrderIds.contains(r.orderId()))
      .collect(Collectors.groupingBy(Review::merchantId, Collectors.averagingDouble(Review::score)));
  }

  /**
   * 用户下过单的商家 ID 集合
   */
  private Set<Long> userOrderedMerchantIds(long userId) {
    return orders.values().stream()
      .filter(o -> o.userId == userId && o.status != OrderStatus.CANCELLED)
      .map(o -> o.merchantId).collect(Collectors.toSet());
  }

  /**
   * 计算个性化推荐分数
   * 返回 [totalScore, catScore, priceScore, behaviorScore, qualityScore, popScore]
   */
  private double[] scoreMerchant(Merchant m, Map<Long, Double> categoryPref, double avgSpend,
                                  Map<Long, Double> merchantScoreMap, Set<Long> orderedIds, boolean hasHistory) {
    // 品类匹配
    double catScore = categoryPref.getOrDefault(m.categoryId(), 0.0);
    // 价格匹配：越接近用户平均消费，分数越高
    double priceScore = 0;
    if (avgSpend > 0) {
      double diff = Math.abs(m.avgPrice() * 100 - avgSpend) / avgSpend;
      priceScore = Math.max(0, 1 - diff);
    }
    // 商家质量：评分 + 距离
    double qualityScore = 0.6 * (m.avgScore() / 5.0) + 0.4 * Math.max(0, 1 - m.distanceKm() / 5.0);
    // 行为加成：复购 or 高评
    double behaviorScore = 0;
    if (orderedIds.contains(m.id())) {
      long orderCount = orders.values().stream()
        .filter(o -> o.userId == 0 || true) // userId 不重要，这里看 merchantId
        .filter(o -> o.merchantId == m.id() && o.status != OrderStatus.CANCELLED)
        .count();
      behaviorScore += 0.5; // 复购基础分
      behaviorScore += Math.min(0.5, orderCount * 0.1); // 复购次数加成
    }
    Double userRating = merchantScoreMap.get(m.id());
    if (userRating != null && userRating >= 4.0) {
      behaviorScore += 0.3; // 高评加成
    }
    behaviorScore = Math.min(1.0, behaviorScore);
    // 热度
    double popScore = Math.min(1.0, m.monthlySales() / 400.0);
    // 加权总分
    double total;
    if (hasHistory) {
      total = 0.25 * catScore + 0.20 * priceScore + 0.20 * qualityScore + 0.25 * behaviorScore + 0.10 * popScore;
    } else {
      // 无历史时降级为全局公式
      total = qualityScore;
    }
    return new double[]{total, catScore, priceScore, behaviorScore, qualityScore, popScore};
  }

  /**
   * 根据各维度得分生成推荐理由
   */
  private String generateReason(Merchant m, double catScore, double priceScore,
                                  double behaviorScore, double qualityScore, double popScore, boolean hasHistory) {
    if (!hasHistory) {
      return "综合评分高、距离近";
    }
    // 按优先级选择理由：品类 > 行为 > 价格 > 热度 > 默认
    if (catScore > 0.3) {
      return "你常点" + m.categoryName() + "，这家评分不错";
    }
    if (behaviorScore > 0.5) {
      boolean isRepeat = orders.values().stream()
        .anyMatch(o -> o.merchantId == m.id() && o.status != OrderStatus.CANCELLED);
      if (isRepeat) return "你回购过这家，值得再来";
      return "你之前给的评价很高";
    }
    if (priceScore > 0.6) {
      return "人均价格适合你的消费习惯";
    }
    if (popScore > 0.5) {
      return "本周热销，附近人气商家";
    }
    return "综合评分高、距离近";
  }

  /**
   * Merchant record 转 Map（包含所有字段）
   */
  private Map<String, Object> merchantToMap(Merchant m) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", m.id());
    map.put("name", m.name());
    map.put("categoryId", m.categoryId());
    map.put("categoryName", m.categoryName());
    map.put("cover", m.cover());
    map.put("avgScore", m.avgScore());
    map.put("avgPrice", m.avgPrice());
    map.put("monthlySales", m.monthlySales());
    map.put("distanceKm", m.distanceKm());
    map.put("status", m.status());
    map.put("address", m.address());
    return map;
  }

  public Map<String, Object> merchantDetail(long id) {
    Merchant merchant = merchants.get(id);
    if (merchant == null) throw new BusinessException(40400, "商家不存在");
    return Map.of(
      "merchant", withLiveStats(merchant),
      "products", products.values().stream().filter(p -> p.merchantId() == id && p.listed()).toList(),
      "groupDeals", deals.values().stream().filter(d -> d.merchantId() == id && d.active()).toList(),
      "reviews", reviews.values().stream().filter(r -> r.merchantId() == id).toList());
  }

  public List<Product> merchantProducts(User admin) {
    return products.values().stream().filter(p -> p.merchantId() == admin.merchantId()).toList();
  }

  public Product saveProduct(User admin, Long id, String name, String description, long priceCent, int stock, boolean listed) {
    if (priceCent <= 0 || stock < 0) throw new BusinessException(40000, "商品价格和库存必须合法");
    if (id != null) {
      Product old = products.get(id);
      if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该商品");
    }
    long productId = id == null ? ids.incrementAndGet() : id;
    Product product = new Product(productId, admin.merchantId(), name, description, priceCent, stock, listed);
    products.put(productId, product);
    log(admin.nickname(), (id == null ? "新增" : "编辑") + "商品 " + name);
    return product;
  }

  public Product toggleProduct(User admin, long id) {
    Product old = products.get(id);
    if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该商品");
    Product product = new Product(old.id(), old.merchantId(), old.name(), old.description(), old.priceCent(), old.stock(), !old.listed());
    products.put(id, product);
    log(admin.nickname(), (product.listed() ? "上架" : "下架") + "商品 " + product.name());
    return product;
  }

  public void deleteProduct(User admin, long id) {
    Product old = products.get(id);
    if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该商品");
    products.remove(id);
    carts.values().forEach(items -> items.removeIf(item -> item.productId() == id));
    log(admin.nickname(), "删除商品 " + old.name());
  }

  public List<GroupDeal> merchantDeals(User admin) {
    return deals.values().stream().filter(d -> d.merchantId() == admin.merchantId()).toList();
  }

  public GroupDeal saveDeal(User admin, Long id, String title, String description, long priceCent, int stock, boolean active) {
    if (priceCent <= 0 || stock < 0) throw new BusinessException(40000, "套餐价格和库存必须合法");
    if (id != null) {
      GroupDeal old = deals.get(id);
      if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该套餐");
    }
    long dealId = id == null ? ids.incrementAndGet() : id;
    GroupDeal deal = new GroupDeal(dealId, admin.merchantId(), title, description, priceCent, stock, active);
    deals.put(dealId, deal);
    log(admin.nickname(), (id == null ? "新增" : "编辑") + "团购套餐 " + title);
    return deal;
  }

  public GroupDeal toggleDeal(User admin, long id) {
    GroupDeal old = deals.get(id);
    if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该套餐");
    GroupDeal deal = new GroupDeal(old.id(), old.merchantId(), old.title(), old.description(), old.priceCent(), old.stock(), !old.active());
    deals.put(id, deal);
    log(admin.nickname(), (deal.active() ? "上架" : "下架") + "团购套餐 " + deal.title());
    return deal;
  }

  public void deleteDeal(User admin, long id) {
    GroupDeal old = deals.get(id);
    if (old == null || old.merchantId() != admin.merchantId()) throw new BusinessException(40300, "无权维护该套餐");
    deals.remove(id);
    log(admin.nickname(), "删除团购套餐 " + old.title());
  }

  public List<CartItem> cart(long userId) {
    return carts.computeIfAbsent(userId, ignored -> new ArrayList<>());
  }

  public List<CartLine> cartDetail(long userId) {
    return cart(userId).stream().map(item -> {
      Product product = products.get(item.productId());
      if (product == null) return new CartLine(item.productId(), 0, "未知商家", "已失效商品", 0, item.quantity(), 0);
      Merchant merchant = merchants.get(product.merchantId());
      return new CartLine(product.id(), product.merchantId(), merchant == null ? "未知商家" : merchant.name(), product.name(), product.priceCent(), item.quantity(),
        product.priceCent() * item.quantity());
    }).toList();
  }

  public List<CartItem> addCart(long userId, long productId, int quantity) {
    if (quantity <= 0) throw new BusinessException(40000, "购物车商品数量必须大于 0");
    Product product = products.get(productId);
    if (product == null || !product.listed()) throw new BusinessException(40900, "商品不可购买");
    List<CartItem> cart = cart(userId);
    cart.removeIf(i -> i.productId() == productId);
    cart.add(new CartItem(productId, quantity));
    return cart;
  }

  public List<CartItem> updateCartItem(long userId, long productId, int quantity) {
    if (quantity <= 0) {
      return removeCartItem(userId, productId);
    }
    Product product = products.get(productId);
    if (product == null || !product.listed()) throw new BusinessException(40900, "商品不可购买");
    List<CartItem> cart = cart(userId);
    if (cart.stream().noneMatch(i -> i.productId() == productId)) throw new BusinessException(40400, "购物车商品不存在");
    cart.replaceAll(i -> i.productId() == productId ? new CartItem(productId, quantity) : i);
    return cart;
  }

  public List<CartItem> removeCartItem(long userId, long productId) {
    List<CartItem> cart = cart(userId);
    boolean removed = cart.removeIf(i -> i.productId() == productId);
    if (!removed) throw new BusinessException(40400, "购物车商品不存在");
    return cart;
  }

  public void clearCart(long userId) {
    carts.remove(userId);
  }

  public Order createDeliveryOrder(User user, Long addressId) {
    return createDeliveryOrders(user, addressId).get(0);
  }

  public List<Order> createDeliveryOrders(User user, Long addressId) {
    List<CartItem> cart = new ArrayList<>(cart(user.id()));
    if (cart.isEmpty()) throw new BusinessException(40900, "购物车为空");
    Address address = resolveOrderAddress(user, addressId);
    Map<Long, Order> grouped = new LinkedHashMap<>();
    for (CartItem item : cart) {
      Product product = products.get(item.productId());
      if (product == null || !product.listed()) throw new BusinessException(40900, "购物车包含不可购买商品");
      if (item.quantity() <= 0 || product.stock() < item.quantity()) throw new BusinessException(40900, "商品库存不足");
      Order order = grouped.computeIfAbsent(product.merchantId(), merchantId -> {
        Order next = new Order();
        next.id = ids.incrementAndGet();
        next.userId = user.id();
        next.merchantId = merchantId;
        next.merchantName = merchantName(merchantId);
        next.type = OrderType.DELIVERY;
        next.addressId = address.id();
        next.addressSnapshot = address.contactName() + " " + address.phone() + " " + address.detail();
        markStatus(next, OrderStatus.PENDING_PAYMENT);
        return next;
      });
      order.lines.add(new OrderLine(product.id(), product.name(), item.quantity(), product.priceCent()));
      order.totalCent += product.priceCent() * item.quantity();
    }
    List<Order> created = new ArrayList<>(grouped.values());
    created.forEach(order -> orders.put(order.id, order));
    clearCart(user.id());
    persistState();
    log(user.nickname(), created.size() == 1 ? "创建外卖订单 #" + created.get(0).id : "按商家拆分创建外卖订单 " + created.size() + " 个");
    return created;
  }

  private Address resolveOrderAddress(User user, Long addressId) {
    List<Address> list = addresses(user);
    if (list.isEmpty()) throw new BusinessException(40900, "请先维护收货地址");
    return list.stream()
      .filter(a -> addressId == null ? a.defaultAddress() : a.id() == addressId)
      .findFirst()
      .orElseThrow(() -> new BusinessException(40400, "收货地址不存在"));
  }

  public Order createGroupOrder(User user, long dealId, int quantity) {
    GroupDeal deal = deals.get(dealId);
    if (quantity <= 0 || deal == null || !deal.active() || deal.stock() < quantity) {
      throw new BusinessException(40900, "套餐不可购买");
    }
    Order order = new Order();
    order.id = ids.incrementAndGet();
    order.userId = user.id();
    order.merchantId = deal.merchantId();
    order.merchantName = merchantName(deal.merchantId());
    order.type = OrderType.GROUP_BUY;
    markStatus(order, OrderStatus.PENDING_PAYMENT);
    order.totalCent = deal.priceCent() * quantity;
    order.lines.add(new OrderLine(deal.id(), deal.title(), quantity, deal.priceCent()));
    orders.put(order.id, order);
    persistState();
    log(user.nickname(), "创建团购订单 #" + order.id);
    return order;
  }

  public Order pay(User user, long orderId, String clientRequestId) {
    if (clientRequestId == null || clientRequestId.isBlank()) throw new BusinessException(40000, "clientRequestId 不能为空");
    Order order = ownedOrder(user, orderId);
    if (clientRequestId.equals(order.clientRequestId)) return order;
    if (order.status != OrderStatus.PENDING_PAYMENT) throw new BusinessException(40900, "订单状态不允许支付");
    ensureStockDeducted(order);
    order.clientRequestId = clientRequestId;
    markStatus(order, OrderStatus.PAID);
    if (order.type == OrderType.GROUP_BUY) {
      order.couponCode = String.valueOf(100000000000L + order.id).substring(0, 12);
      couponIndex.put(order.couponCode, order.id);
    }
    persistState();
    log(user.nickname(), "模拟支付订单 #" + order.id);
    return order;
  }

  public Order cancel(User user, long orderId) {
    Order order = ownedOrder(user, orderId);
    if (order.status != OrderStatus.PENDING_PAYMENT) throw new BusinessException(40900, "仅待支付订单可取消");
    markStatus(order, OrderStatus.CANCELLED);
    persistState();
    log(user.nickname(), "取消订单 #" + order.id);
    return order;
  }

  public Order receive(User user, long orderId) {
    Order order = ownedOrder(user, orderId);
    if (order.type != OrderType.DELIVERY) throw new BusinessException(40900, "仅外卖订单可确认收货");
    if (!(order.status == OrderStatus.DELIVERING || order.status == OrderStatus.COMPLETED)) {
      throw new BusinessException(40900, "订单尚未配送，不能确认收货");
    }
    markStatus(order, OrderStatus.RECEIVED);
    persistState();
    log(user.nickname(), "确认收货订单 #" + order.id);
    return order;
  }

  public List<Order> userOrders(User user) {
    return orders.values().stream()
      .filter(o -> o.userId == user.id())
      .map(this::withMerchantName)
      .sorted(Comparator.comparing((Order o) -> o.createdAt).reversed())
      .toList();
  }

  public List<Order> merchantOrders(User admin) {
    return orders.values().stream()
      .filter(o -> o.merchantId == admin.merchantId())
      .map(this::withMerchantName)
      .sorted(Comparator.comparing((Order o) -> o.createdAt).reversed())
      .toList();
  }

  public List<Review> merchantReviews(User admin) {
    return reviews.values().stream()
      .filter(r -> r.merchantId() == admin.merchantId())
      .sorted(Comparator.comparing(Review::createdAt).reversed())
      .toList();
  }

  public Order transition(User admin, long orderId, OrderStatus next) {
    Order order = orders.get(orderId);
    if (order == null || order.merchantId != admin.merchantId()) throw new BusinessException(40300, "无权处理该订单");
    if (order.type != OrderType.DELIVERY) throw new BusinessException(40900, "团购订单只能通过券码核销");
    boolean ok = (order.status == OrderStatus.PAID && next == OrderStatus.ACCEPTED)
      || (order.status == OrderStatus.ACCEPTED && next == OrderStatus.DELIVERING)
      || (order.status == OrderStatus.DELIVERING && next == OrderStatus.COMPLETED);
    if (!ok) throw new BusinessException(40900, "非法订单状态流转");
    if (next == OrderStatus.ACCEPTED) {
      ensureStockDeducted(order);
    }
    markStatus(order, next);
    persistState();
    log(admin.nickname(), "订单 #" + order.id + " 流转为 " + next);
    return order;
  }

  public Order verifyCoupon(User admin, String code) {
    Long orderId = couponIndex.get(code);
    if (orderId == null) throw new BusinessException(40400, "券码不存在");
    Order order = orders.get(orderId);
    if (order.merchantId != admin.merchantId()) throw new BusinessException(40300, "不能核销其他商家的券码");
    if (order.status != OrderStatus.PAID) throw new BusinessException(40900, "券码不可重复核销");
    markStatus(order, OrderStatus.USED);
    persistState();
    log(admin.nickname(), "核销券码 " + code);
    return order;
  }

  public Review review(User user, long orderId, int score, int tasteScore, int serviceScore, String content) {
    Order order = ownedOrder(user, orderId);
    if (!(order.status == OrderStatus.RECEIVED || order.status == OrderStatus.COMPLETED || order.status == OrderStatus.USED)) {
      throw new BusinessException(40900, "订单未完成不可评价");
    }
    if (order.reviewed) throw new BusinessException(40900, "同一订单不可重复评价");
    if (score < 1 || score > 5 || tasteScore < 1 || tasteScore > 5 || serviceScore < 1 || serviceScore > 5) {
      throw new BusinessException(40000, "评分必须在 1 到 5 之间");
    }
    String reviewContent = content == null ? "" : content.trim();
    if (reviewContent.isBlank()) throw new BusinessException(40000, "评价内容不能为空");
    Review review = new Review(ids.incrementAndGet(), order.id, order.merchantId, mask(user.nickname()), score, tasteScore,
      serviceScore, reviewContent.length() > 200 ? reviewContent.substring(0, 200) : reviewContent, LocalDateTime.now());
    reviews.put(review.id(), review);
    order.reviewed = true;
    persistState();
    log(user.nickname(), "评价订单 #" + order.id);
    return review;
  }

  public Map<String, Object> adminMetrics() {
    long amount = orders.values().stream().filter(o -> o.status != OrderStatus.CANCELLED).mapToLong(o -> o.totalCent).sum();
    List<OperationLog> reversedLogs = new ArrayList<>(logs);
    java.util.Collections.reverse(reversedLogs);
    List<Map<String, Object>> userAccounts = users.values().stream()
      .filter(user -> user.role() == UserRole.USER)
      .map(this::accountSummary)
      .toList();
    List<Map<String, Object>> merchantAccounts = users.values().stream()
      .filter(user -> user.role() == UserRole.MERCHANT_ADMIN)
      .map(this::accountSummary)
      .toList();
    return Map.of(
      "users", userAccounts.size(),
      "merchants", merchants.size(),
      "orders", orders.size(),
      "amountCent", amount,
      "health", "UP",
      "userAccounts", userAccounts,
      "merchantAccounts", merchantAccounts,
      "logs", reversedLogs);
  }

  /**
   * 增强版管理仪表盘指标，包含订单分布、营收趋势、商户排行、配送时效等
   */
  public Map<String, Object> adminMetricsV2() {
    // 1. 基础概览
    LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
    List<Order> allOrders = new ArrayList<>(orders.values());
    List<Order> nonCancelled = allOrders.stream().filter(o -> o.status != OrderStatus.CANCELLED).toList();
    long totalAmount = nonCancelled.stream().mapToLong(o -> o.totalCent).sum();
    List<Order> todayOrders = allOrders.stream().filter(o -> o.createdAt.isAfter(todayStart) || o.createdAt.isEqual(todayStart)).toList();
    long todayAmount = todayOrders.stream().filter(o -> o.status != OrderStatus.CANCELLED).mapToLong(o -> o.totalCent).sum();

    Map<String, Object> overview = new LinkedHashMap<>();
    overview.put("users", (int) users.values().stream().filter(u -> u.role() == UserRole.USER).count());
    overview.put("merchants", merchants.size());
    overview.put("orders", allOrders.size());
    overview.put("amountCent", totalAmount);
    overview.put("todayOrders", todayOrders.size());
    overview.put("todayAmountCent", todayAmount);

    // 2. 订单状态分布
    Map<String, Integer> statusDist = new LinkedHashMap<>();
    for (OrderStatus s : OrderStatus.values()) {
      int count = (int) allOrders.stream().filter(o -> o.status == s).count();
      if (count > 0) statusDist.put(s.name(), count);
    }

    // 3. 订单类型分布
    Map<String, Integer> typeDist = new LinkedHashMap<>();
    for (OrderType t : OrderType.values()) {
      int count = (int) allOrders.stream().filter(o -> o.type == t).count();
      if (count > 0) typeDist.put(t.name(), count);
    }

    // 4. 营收趋势（近 7 天）
    List<Map<String, Object>> revenueTrend = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDateTime dayStart = todayStart.minusDays(i);
      LocalDateTime dayEnd = dayStart.plusDays(1);
      List<Order> dayOrders = nonCancelled.stream()
        .filter(o -> !o.createdAt.isBefore(dayStart) && o.createdAt.isBefore(dayEnd))
        .toList();
      Map<String, Object> dayData = new LinkedHashMap<>();
      dayData.put("date", dayStart.toLocalDate().toString());
      dayData.put("amountCent", dayOrders.stream().mapToLong(o -> o.totalCent).sum());
      dayData.put("orderCount", dayOrders.size());
      revenueTrend.add(dayData);
    }

    // 5. 商户排行榜
    List<Map<String, Object>> merchantRanking = merchants.values().stream().map(m -> {
      List<Order> mOrders = nonCancelled.stream().filter(o -> o.merchantId == m.id()).toList();
      long revenue = mOrders.stream().mapToLong(o -> o.totalCent).sum();
      double avgScore = reviews.values().stream().filter(r -> r.merchantId() == m.id()).mapToInt(Review::score).average().orElse(m.avgScore());
      Map<String, Object> rank = new LinkedHashMap<>();
      rank.put("merchantId", m.id());
      rank.put("name", m.name());
      rank.put("orderCount", mOrders.size());
      rank.put("revenueCent", revenue);
      rank.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
      return rank;
    }).sorted(Comparator.<Map<String, Object>, Long>comparing(r -> (Long) r.get("revenueCent")).reversed()).toList();

    // 6. 配送时效指标
    List<Order> deliveryOrders = nonCancelled.stream().filter(o -> o.type == OrderType.DELIVERY).toList();
    double avgAcceptMin = calcAvgTransitionMinutes(deliveryOrders, OrderStatus.PAID, OrderStatus.ACCEPTED);
    double avgDeliveryMin = calcAvgTransitionMinutes(deliveryOrders, OrderStatus.ACCEPTED, OrderStatus.COMPLETED);
    long completedCount = deliveryOrders.stream().filter(o ->
      o.status == OrderStatus.RECEIVED || o.status == OrderStatus.COMPLETED).count();
    double completionRate = deliveryOrders.isEmpty() ? 0 : (double) completedCount / deliveryOrders.size();

    Map<String, Object> deliveryMetrics = new LinkedHashMap<>();
    deliveryMetrics.put("avgAcceptMinutes", Math.round(avgAcceptMin * 10.0) / 10.0);
    deliveryMetrics.put("avgDeliveryMinutes", Math.round(avgDeliveryMin * 10.0) / 10.0);
    deliveryMetrics.put("completionRate", Math.round(completionRate * 100.0) / 100.0);

    // 7. 活跃订单（待接单 / 配送中）
    List<Map<String, Object>> activeOrders = allOrders.stream()
      .filter(o -> o.status == OrderStatus.PAID || o.status == OrderStatus.ACCEPTED || o.status == OrderStatus.DELIVERING)
      .sorted(Comparator.comparing((Order o) -> o.createdAt).reversed())
      .map(o -> {
        long elapsed = java.time.Duration.between(o.createdAt, LocalDateTime.now()).toMinutes();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", o.id);
        item.put("merchantName", o.merchantName);
        item.put("type", o.type.name());
        item.put("status", o.status.name());
        item.put("totalCent", o.totalCent);
        item.put("createdAt", o.createdAt.toString());
        item.put("elapsedMinutes", elapsed);
        // 构建时间线
        Map<String, String> timeline = new LinkedHashMap<>();
        o.statusTimeline.forEach((k, v) -> timeline.put(k.name(), v.toString()));
        item.put("statusTimeline", timeline);
        return item;
      }).toList();

    // 8. 健康状态
    long pendingOrders = allOrders.stream().filter(o ->
      o.status == OrderStatus.PAID || o.status == OrderStatus.ACCEPTED || o.status == OrderStatus.DELIVERING).count();
    Map<String, Object> health = new LinkedHashMap<>();
    health.put("status", "UP");
    health.put("pendingOrders", pendingOrders);

    // 9. 操作日志
    List<OperationLog> reversedLogs = new ArrayList<>(logs);
    java.util.Collections.reverse(reversedLogs);

    // 10. 账户列表
    List<Map<String, Object>> userAccounts = users.values().stream()
      .filter(user -> user.role() == UserRole.USER).map(this::accountSummary).toList();
    List<Map<String, Object>> merchantAccounts = users.values().stream()
      .filter(user -> user.role() == UserRole.MERCHANT_ADMIN).map(this::accountSummary).toList();

    // 组装结果
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("overview", overview);
    result.put("orderStatusDistribution", statusDist);
    result.put("orderTypeDistribution", typeDist);
    result.put("revenueTrend", revenueTrend);
    result.put("merchantRanking", merchantRanking);
    result.put("deliveryMetrics", deliveryMetrics);
    result.put("activeOrders", activeOrders);
    result.put("health", health);
    result.put("userAccounts", userAccounts);
    result.put("merchantAccounts", merchantAccounts);
    result.put("logs", reversedLogs);
    return result;
  }

  private double calcAvgTransitionMinutes(List<Order> orderList, OrderStatus from, OrderStatus to) {
    return orderList.stream()
      .filter(o -> o.statusTimeline.containsKey(from) && o.statusTimeline.containsKey(to))
      .mapToLong(o -> java.time.Duration.between(o.statusTimeline.get(from), o.statusTimeline.get(to)).toMinutes())
      .average().orElse(0);
  }

  // ==================== 收藏功能 ====================

  public void addFavorite(long userId, long merchantId) {
    if (!merchants.containsKey(merchantId)) throw new BusinessException(40400, "商家不存在");
    Set<Long> set = favorites.computeIfAbsent(userId, k -> new java.util.LinkedHashSet<>());
    if (set.contains(merchantId)) throw new BusinessException(40900, "已收藏该商家");
    set.add(merchantId);
    persistState();
  }

  public void removeFavorite(long userId, long merchantId) {
    Set<Long> set = favorites.get(userId);
    if (set == null || !set.remove(merchantId)) throw new BusinessException(40400, "未收藏该商家");
    if (set.isEmpty()) favorites.remove(userId);
    persistState();
  }

  public List<Long> listFavorites(long userId) {
    return new ArrayList<>(favorites.getOrDefault(userId, Set.of()));
  }

  public List<Map<String, Object>> listFavoriteMerchants(long userId) {
    Set<Long> ids = favorites.getOrDefault(userId, Set.of());
    return ids.stream()
      .map(merchants::get)
      .filter(m -> m != null)
      .map(this::withLiveStats)
      .map(this::merchantToMap)
      .toList();
  }

  public boolean isFavorite(long userId, long merchantId) {
    return favorites.getOrDefault(userId, Set.of()).contains(merchantId);
  }

  private Map<String, Object> accountSummary(User user) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.id());
    data.put("username", user.phone());
    data.put("nickname", user.nickname());
    data.put("role", user.role());
    if (user.merchantId() != null) data.put("merchantId", user.merchantId());
    return data;
  }

  public List<Map<String, Object>> userConversationSummaries(User user) {
    if (user.role() != UserRole.USER) throw new BusinessException(40300, "仅用户账号可查看用户侧客服会话");
    return conversations.values().stream()
      .filter(messages -> !messages.isEmpty() && messages.get(0).userId() == user.id())
      .map(messages -> conversationSummary(messages, true))
      .sorted(Comparator.comparing((Map<String, Object> item) -> (LocalDateTime) item.get("updatedAt")).reversed())
      .toList();
  }

  public List<Map<String, Object>> merchantConversationSummaries(User admin) {
    ensureMerchantAdmin(admin);
    return conversations.values().stream()
      .filter(messages -> !messages.isEmpty() && messages.get(0).merchantId() == admin.merchantId())
      .map(messages -> conversationSummary(messages, false))
      .sorted(Comparator.comparing((Map<String, Object> item) -> (LocalDateTime) item.get("updatedAt")).reversed())
      .toList();
  }

  public List<ChatMessage> userConversation(User user, long merchantId) {
    if (user.role() != UserRole.USER) throw new BusinessException(40300, "仅用户账号可联系客服");
    if (!merchants.containsKey(merchantId)) throw new BusinessException(40400, "商家不存在");
    return new ArrayList<>(conversations.getOrDefault(conversationKey(user.id(), merchantId), List.of()));
  }

  public List<ChatMessage> merchantConversation(User admin, long userId) {
    ensureMerchantAdmin(admin);
    User customer = userById(userId);
    if (customer.role() != UserRole.USER) throw new BusinessException(40400, "用户账号不存在");
    return new ArrayList<>(conversations.getOrDefault(conversationKey(userId, admin.merchantId()), List.of()));
  }

  public List<ChatMessage> sendUserMessage(User user, long merchantId, String content, Function<List<ChatMessage>, String> aiResponder) {
    if (user.role() != UserRole.USER) throw new BusinessException(40300, "仅用户账号可联系客服");
    Merchant merchant = merchants.get(merchantId);
    if (merchant == null) throw new BusinessException(40400, "商家不存在");
    String text = normalizeMessage(content);
    List<ChatMessage> messages = conversations.computeIfAbsent(conversationKey(user.id(), merchantId), ignored -> new ArrayList<>());
    messages.add(new ChatMessage(ids.incrementAndGet(), user.id(), merchantId, "USER", user.nickname(), text, LocalDateTime.now()));
    String answer = aiResponder.apply(new ArrayList<>(messages));
    messages.add(new ChatMessage(ids.incrementAndGet(), user.id(), merchantId, "MERCHANT_AI", merchant.name(), normalizeAiAnswer(answer), LocalDateTime.now()));
    persistState();
    log(user.nickname(), "咨询商家客服 " + merchant.name());
    return new ArrayList<>(messages);
  }

  public List<ChatMessage> sendMerchantMessage(User admin, long userId, String content) {
    ensureMerchantAdmin(admin);
    User customer = userById(userId);
    if (customer.role() != UserRole.USER) throw new BusinessException(40400, "用户账号不存在");
    List<ChatMessage> messages = conversations.computeIfAbsent(conversationKey(userId, admin.merchantId()), ignored -> new ArrayList<>());
    messages.add(new ChatMessage(ids.incrementAndGet(), userId, admin.merchantId(), "MERCHANT", admin.nickname(), normalizeMessage(content), LocalDateTime.now()));
    persistState();
    log(admin.nickname(), "回复用户客服 " + customer.nickname());
    return new ArrayList<>(messages);
  }

  private Map<String, Object> conversationSummary(List<ChatMessage> messages, boolean userSide) {
    ChatMessage first = messages.get(0);
    ChatMessage last = messages.get(messages.size() - 1);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", first.userId());
    data.put("userName", userById(first.userId()).nickname());
    data.put("merchantId", first.merchantId());
    data.put("merchantName", merchantName(first.merchantId()));
    data.put("title", userSide ? merchantName(first.merchantId()) : userById(first.userId()).nickname());
    data.put("lastMessage", last.content());
    data.put("updatedAt", last.createdAt());
    return data;
  }

  private User userById(long userId) {
    return users.values().stream()
      .filter(user -> user.id() == userId)
      .findFirst()
      .orElseThrow(() -> new BusinessException(40400, "用户账号不存在"));
  }

  private String normalizeMessage(String content) {
    if (content == null || content.isBlank()) throw new BusinessException(40000, "消息不能为空");
    String text = content.trim();
    return text.length() > 500 ? text.substring(0, 500) : text;
  }

  private String normalizeAiAnswer(String content) {
    if (content == null || content.isBlank()) return "您好，已收到您的问题，店家客服会尽快跟进。";
    String text = content.trim();
    return text.length() > 500 ? text.substring(0, 500) : text;
  }

  private String conversationKey(long userId, long merchantId) {
    return userId + ":" + merchantId;
  }

  public String askAssistant(String question) {
    if (question.contains("评价")) return "只有已完成的外卖订单或已核销的团购订单可以评价，且一单只能评价一次。";
    if (question.contains("支付")) return "当前系统为课程演示版模拟支付，支付接口使用 clientRequestId 保证幂等。";
    if (question.contains("券")) return "团购支付成功后会生成 12 位券码，商家只能核销自己店铺的券码。";
    return "我可以解答登录、下单、支付、评价、团购券核销和商家履约相关问题。";
  }

  private Order ownedOrder(User user, long orderId) {
    Order order = orders.get(orderId);
    if (order == null || order.userId != user.id()) throw new BusinessException(40400, "订单不存在");
    return order;
  }

  private Merchant withLiveStats(Merchant base) {
    if (base == null) throw new BusinessException(40400, "商家不存在");
    List<Order> paidOrders = orders.values().stream()
      .filter(o -> o.merchantId == base.id())
      .filter(o -> o.status != OrderStatus.PENDING_PAYMENT && o.status != OrderStatus.CANCELLED)
      .toList();
    List<Review> merchantReviews = reviews.values().stream().filter(r -> r.merchantId() == base.id()).toList();

    double avgScore = base.avgScore();
    if (!merchantReviews.isEmpty()) {
      double scoreTotal = merchantReviews.stream().mapToInt(Review::score).sum();
      avgScore = ((base.avgScore() * 6) + scoreTotal) / (6 + merchantReviews.size());
    }

    int avgPrice = base.avgPrice();
    if (!paidOrders.isEmpty()) {
      double liveAverageYuan = paidOrders.stream().mapToLong(o -> o.totalCent).average().orElse(base.avgPrice() * 100.0) / 100.0;
      avgPrice = (int) Math.round(((base.avgPrice() * 8.0) + (liveAverageYuan * paidOrders.size())) / (8 + paidOrders.size()));
    }

    int liveSales = paidOrders.stream()
      .flatMap(o -> o.lines.stream())
      .mapToInt(OrderLine::quantity)
      .sum();
    int monthlySales = base.monthlySales() + liveSales;

    return new Merchant(base.id(), base.name(), base.categoryId(), base.categoryName(), base.cover(),
      Math.round(avgScore * 10) / 10.0, avgPrice, monthlySales, base.distanceKm(), base.status(), base.address(), base.reason());
  }

  private void markStatus(Order order, OrderStatus status) {
    order.status = status;
    order.statusTimeline.put(status, LocalDateTime.now());
  }

  private long createMerchantFor(String displayName) {
    long id = ids.incrementAndGet();
    merchants.put(id, new Merchant(id, displayName, 1, "川湘菜",
      DEFAULT_CHUANXIANG_COVER,
      5.0, 25, 0, 1.0, "营业中", "新商家地址待维护", "新入驻商家"));
    return id;
  }

  private void ensureMerchantAdmin(User admin) {
    if (admin.role() != UserRole.MERCHANT_ADMIN || admin.merchantId() == null || !merchants.containsKey(admin.merchantId())) {
      throw new BusinessException(40300, "商家账号未绑定店铺");
    }
  }

  private Merchant renameMerchant(long merchantId, String nextName) {
    Merchant old = merchants.get(merchantId);
    Merchant renamed = new Merchant(old.id(), nextName, old.categoryId(), old.categoryName(), old.cover(), old.avgScore(),
      old.avgPrice(), old.monthlySales(), old.distanceKm(), old.status(), old.address(), old.reason());
    merchants.put(renamed.id(), renamed);
    orders.values().stream()
      .filter(order -> order.merchantId == renamed.id())
      .forEach(order -> order.merchantName = nextName);
    return renamed;
  }

  private void ensureStockDeducted(Order order) {
    if (order.stockDeducted) return;
    for (OrderLine line : order.lines) {
      if (line.itemId() == null) throw new BusinessException(40900, "订单商品信息不完整");
      if (order.type == OrderType.DELIVERY) {
        Product old = products.get(line.itemId());
        if (old == null || old.merchantId() != order.merchantId) throw new BusinessException(40900, "订单商品已失效");
        if (old.stock() < line.quantity()) throw new BusinessException(40900, old.name() + " 库存不足");
      } else if (order.type == OrderType.GROUP_BUY) {
        GroupDeal old = deals.get(line.itemId());
        if (old == null || old.merchantId() != order.merchantId) throw new BusinessException(40900, "团购套餐已失效");
        if (old.stock() < line.quantity()) throw new BusinessException(40900, old.title() + " 库存不足");
      }
    }
    for (OrderLine line : order.lines) {
      if (order.type == OrderType.DELIVERY) {
        Product old = products.get(line.itemId());
        products.put(old.id(), new Product(old.id(), old.merchantId(), old.name(), old.description(), old.priceCent(),
          old.stock() - line.quantity(), old.listed()));
      } else if (order.type == OrderType.GROUP_BUY) {
        GroupDeal old = deals.get(line.itemId());
        deals.put(old.id(), new GroupDeal(old.id(), old.merchantId(), old.title(), old.description(), old.priceCent(),
          old.stock() - line.quantity(), old.active()));
      }
    }
    order.stockDeducted = true;
  }

  private String merchantName(long merchantId) {
    Merchant merchant = merchants.get(merchantId);
    return merchant == null ? "未知商家" : merchant.name();
  }

  private Order withMerchantName(Order order) {
    if (order.merchantName == null || order.merchantName.isBlank() || "未知商家".equals(order.merchantName)) {
      order.merchantName = merchantName(order.merchantId);
    }
    return order;
  }

  private String mask(String name) {
    return name.length() <= 1 ? name + "*" : name.charAt(0) + "***";
  }

  private void log(String actor, String action) {
    logs.add(new OperationLog(ids.incrementAndGet(), actor, action, LocalDateTime.now()));
  }

  private void syncMerchantNamesFromAccounts() {
    boolean changed = false;
    for (User user : new ArrayList<>(users.values())) {
      changed = syncMerchantNameFromAccount(user, false) || changed;
    }
    if (changed) persistState();
  }

  private User syncMerchantNameFromAccount(User user) {
    syncMerchantNameFromAccount(user, true);
    return users.getOrDefault(user.phone(), user);
  }

  private boolean syncMerchantNameFromAccount(User user, boolean persist) {
    if (user == null || user.role() != UserRole.MERCHANT_ADMIN || user.merchantId() == null) return false;
    Merchant merchant = merchants.get(user.merchantId());
    if (merchant == null || user.nickname() == null || user.nickname().isBlank() || merchant.name().equals(user.nickname())) return false;
    renameMerchant(user.merchantId(), user.nickname());
    if (persist) persistState();
    return true;
  }

  private void loadPersistentState() {
    if (!persistenceEnabled || !Files.exists(stateFile)) return;
    try {
      PersistentState state = objectMapper.readValue(stateFile.toFile(), PersistentState.class);
      boolean needsMigration = state.accounts() == null
        || state.orders() == null
        || state.reviews() == null
        || state.conversations() == null
        || state.merchantProfiles() == null;
      if (state.accounts() != null) state.accounts().forEach(this::applyStoredAccount);
      if (state.orders() != null) state.orders().forEach(this::applyStoredOrder);
      if (state.reviews() != null) state.reviews().forEach(review -> {
        reviews.put(review.id(), review);
        bumpId(review.id());
      });
      if (state.conversations() != null) state.conversations().forEach(this::applyStoredConversation);
      if (state.merchantProfiles() != null) state.merchantProfiles().forEach(this::applyStoredMerchantProfile);
      if (state.favorites() != null) favorites.putAll(state.favorites());
      if (needsMigration) persistState();
    } catch (IOException error) {
      throw new IllegalStateException("Failed to load LumaLife state from " + stateFile, error);
    }
  }

  private void applyStoredAccount(AccountState account) {
    if (account == null || account.phone() == null || account.phone().isBlank() || account.password() == null || account.password().isBlank()) return;
    UserRole role = account.role() == null ? UserRole.USER : account.role();
    Long merchantId = account.merchantId();
    if (role == UserRole.MERCHANT_ADMIN) {
      if (merchantId == null && account.merchant() != null) merchantId = account.merchant().id();
      if (merchantId == null) return;
      Merchant storedMerchant = account.merchant();
      if (storedMerchant != null) {
        merchants.put(storedMerchant.id(), storedMerchant);
      } else if (!merchants.containsKey(merchantId)) {
        merchants.put(merchantId, new Merchant(merchantId, account.nickname(), 1, "川湘菜",
          DEFAULT_CHUANXIANG_COVER,
          5.0, 25, 0, 1.0, "营业中", "新商家地址待维护", "新入驻商家"));
      }
    }
    String username = account.phone().trim();
    String nextNickname = account.nickname() == null || account.nickname().isBlank() ? "新用户" + username : account.nickname().trim();
    User user = new User(account.id(), username, account.password(), nextNickname,
      account.avatarUrl() == null ? "" : account.avatarUrl(), role, merchantId);
    users.put(username, user);
    bumpId(user.id());
    if (merchantId != null) bumpId(merchantId);
  }

  private void applyStoredMerchantProfile(MerchantProfileState profile) {
    if (profile == null || profile.phone() == null || profile.nickname() == null || profile.nickname().isBlank()) return;
    User user = users.get(profile.phone());
    if (user == null || user.role() != UserRole.MERCHANT_ADMIN || user.merchantId() == null) return;
    long merchantId = merchants.containsKey(profile.merchantId()) ? profile.merchantId() : user.merchantId();
    if (!merchants.containsKey(merchantId)) return;
    String nextName = profile.nickname().trim();
    users.put(user.phone(), new User(user.id(), user.phone(), user.password(), nextName, user.avatarUrl(), user.role(), merchantId));
    renameMerchant(merchantId, nextName);
  }

  private void applyStoredOrder(Order order) {
    if (order == null || order.id <= 0) return;
    orders.put(order.id, withMerchantName(order));
    if (order.couponCode != null && !order.couponCode.isBlank()) couponIndex.put(order.couponCode, order.id);
    bumpId(order.id);
  }

  private void applyStoredConversation(ConversationState conversation) {
    if (conversation == null || conversation.messages() == null || conversation.messages().isEmpty()) return;
    ChatMessage first = conversation.messages().get(0);
    String key = conversationKey(first.userId(), first.merchantId());
    conversations.put(key, new ArrayList<>(conversation.messages()));
    conversation.messages().forEach(message -> bumpId(message.id()));
  }

  private void persistState() {
    if (!persistenceEnabled) return;
    try {
      Path parent = stateFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      List<AccountState> accounts = users.values().stream()
        .map(user -> new AccountState(user.id(), user.phone(), user.password(), user.nickname(), user.avatarUrl(),
          user.role(), user.merchantId(), user.merchantId() == null ? null : merchants.get(user.merchantId())))
        .toList();
      List<Order> persistedOrders = new ArrayList<>(orders.values());
      List<Review> persistedReviews = new ArrayList<>(reviews.values());
      List<ConversationState> persistedConversations = conversations.values().stream()
        .filter(messages -> !messages.isEmpty())
        .map(ConversationState::new)
        .toList();
      List<MerchantProfileState> merchantProfiles = users.values().stream()
        .filter(user -> user.role() == UserRole.MERCHANT_ADMIN && user.merchantId() != null && merchants.containsKey(user.merchantId()))
        .map(user -> new MerchantProfileState(user.phone(), user.merchantId(), merchants.get(user.merchantId()).name()))
        .toList();
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), new PersistentState(accounts, persistedOrders, persistedReviews, persistedConversations, merchantProfiles, favorites.isEmpty() ? null : favorites));
    } catch (IOException error) {
      throw new IllegalStateException("Failed to save LumaLife state to " + stateFile, error);
    }
  }

  private void bumpId(long id) {
    ids.updateAndGet(current -> Math.max(current, id));
  }

  private record PersistentState(List<AccountState> accounts, List<Order> orders, List<Review> reviews,
                                 List<ConversationState> conversations, List<MerchantProfileState> merchantProfiles,
                                 Map<Long, Set<Long>> favorites) {}
  private record AccountState(long id, String phone, String password, String nickname, String avatarUrl, UserRole role, Long merchantId, Merchant merchant) {}
  private record ConversationState(List<ChatMessage> messages) {}
  private record MerchantProfileState(String phone, long merchantId, String nickname) {}

  private void seed() {
    addSeedUser(new User(1, "13800000001", passwordEncoder.encode("abc123456"), "林夏", "", UserRole.USER, null));
    addSeedUser(new User(2, "13800000002", passwordEncoder.encode("abc123456"), "巷口川味研究所", "", UserRole.MERCHANT_ADMIN, 1L));
    addSeedUser(new User(3, "13800000003", passwordEncoder.encode("abc123456"), "晨雾咖啡局", "", UserRole.MERCHANT_ADMIN, 2L));
    addSeedUser(new User(4, "13800000004", passwordEncoder.encode("abc123456"), "绿盒轻食", "", UserRole.MERCHANT_ADMIN, 3L));
    addSeedUser(new User(5, "13800000005", passwordEncoder.encode("abc123456"), "栗香烘焙室", "", UserRole.MERCHANT_ADMIN, 4L));
    addSeedUser(new User(6, "13800000000", passwordEncoder.encode("admin123456"), "平台管理员", "", UserRole.PLATFORM_ADMIN, null));
    addresses.put(1L, new ArrayList<>(List.of(
      new Address(101, 1, "林夏", "13800000001", "梧桐路 18 号 2 单元 601", true),
      new Address(102, 1, "林夏", "13800000001", "学院路 66 号软件楼", false)
    )));
    categories.put(1L, new Category(1, "川湘菜", "flame"));
    categories.put(2L, new Category(2, "咖啡茶饮", "coffee"));
    categories.put(3L, new Category(3, "轻食简餐", "salad"));
    categories.put(4L, new Category(4, "甜品烘焙", "cake"));
    categories.put(5L, new Category(5, "生活服务", "sparkle"));
    merchants.put(1L, new Merchant(1, "巷口川味研究所", 1, "川湘菜", "https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=1000&q=80", 4.8, 38, 386, 1.2, "营业中", "梧桐路 18 号", "评分高、距离近、近期销量较好"));
    merchants.put(2L, new Merchant(2, "晨雾咖啡局", 2, "咖啡茶饮", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=1000&q=80", 4.7, 32, 268, 0.7, "营业中", "湖畔街 3 号", "距离近、评价稳定"));
    merchants.put(3L, new Merchant(3, "绿盒轻食", 3, "轻食简餐", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1000&q=80", 4.5, 29, 189, 2.4, "营业中", "学院路 66 号", "热量标注清晰、复购高"));
    merchants.put(4L, new Merchant(4, "栗香烘焙室", 4, "甜品烘焙", "https://images.unsplash.com/photo-1486427944299-d1955d23e34d?auto=format&fit=crop&w=1000&q=80", 4.6, 26, 142, 3.1, "营业中", "银杏街 9 号", "甜品评分高"));
    seedProduct(1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680);
    seedProduct(1, "毛血旺小锅", "课程演示热门搜索菜", 4280);
    seedProduct(1, "冰粉", "解辣甜品", 900);
    seedProduct(2, "桂花拿铁", "轻甜花香", 2800);
    seedProduct(2, "冷萃咖啡", "低酸清爽", 2600);
    seedProduct(3, "牛油果鸡胸碗", "高蛋白轻食", 3280);
    seedProduct(4, "栗子巴斯克", "招牌切块", 2200);
    deals.put(1L, new GroupDeal(1, 1, "双人川味到店套餐", "2 道主菜 + 2 杯饮品", 6990, 30, true));
    deals.put(2L, new GroupDeal(2, 2, "咖啡下午茶券", "任意两杯咖啡 + 甜点", 4990, 45, true));
    deals.put(3L, new GroupDeal(3, 4, "烘焙分享盒", "6 款切块组合", 5990, 20, true));
  }

  private void seedProduct(long merchantId, String name, String description, long price) {
    long id = ids.incrementAndGet();
    products.put(id, new Product(id, merchantId, name, description, price, 99, true));
  }

  private void addSeedUser(User next) {
    User existing = users.get(next.phone());
    if (existing == null || (next.role() == UserRole.MERCHANT_ADMIN && existing.role() != UserRole.MERCHANT_ADMIN)) {
      users.put(next.phone(), next);
    }
  }
}
