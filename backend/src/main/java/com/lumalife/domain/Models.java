package com.lumalife.domain;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Enums.OrderType;
import com.lumalife.domain.Enums.UserRole;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Models {
  private Models() {}

  public record User(long id, String phone, String password, String nickname, String avatarUrl, UserRole role, Long merchantId) {}
  public record Address(long id, long userId, String contactName, String phone, String detail, boolean defaultAddress) {}
  public record Category(long id, String name, String icon) {}
  public record Merchant(long id, String name, long categoryId, String categoryName, String cover, double avgScore,
                         int avgPrice, int monthlySales, double distanceKm, String status, String address, String reason) {}
  public record Product(long id, long merchantId, String name, String description, long priceCent, int stock, boolean listed) {}
  public record GroupDeal(long id, long merchantId, String title, String description, long priceCent, int stock, boolean active) {}
  public record CartItem(long productId, int quantity) {}
  public record CartLine(long productId, long merchantId, String merchantName, String name, long priceCent, int quantity, long subtotalCent) {}
  public record Review(long id, long orderId, long merchantId, String userName, int score, int tasteScore,
                       int serviceScore, String content, LocalDateTime createdAt) {}
  public record ChatMessage(long id, long userId, long merchantId, String senderRole, String senderName,
                            String content, LocalDateTime createdAt) {}

  public static class Order {
    public long id;
    public long userId;
    public long merchantId;
    public String merchantName;
    public OrderType type;
    public OrderStatus status;
    public long totalCent;
    public String clientRequestId;
    public String couponCode;
    public Long addressId;
    public String addressSnapshot;
    public boolean reviewed;
    public boolean stockDeducted;
    public LocalDateTime createdAt = LocalDateTime.now();
    public List<OrderLine> lines = new ArrayList<>();
    public Map<OrderStatus, LocalDateTime> statusTimeline = new LinkedHashMap<>();
  }

  public record OrderLine(Long itemId, String name, int quantity, long priceCent) {}
  public record OperationLog(long id, String actor, String action, LocalDateTime createdAt) {}
}
