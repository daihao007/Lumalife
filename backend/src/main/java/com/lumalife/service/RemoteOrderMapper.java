package com.lumalife.service;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Enums.OrderType;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.OrderLine;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Converts the deliberately small order-service DTO into the frontend order contract. */
final class RemoteOrderMapper {
  private RemoteOrderMapper() {}

  static Order map(Map<?, ?> row, Map<?, ?> merchant, Map<?, ?> product, Map<?, ?> deal) {
    Order order = new Order();
    order.id = number(row, "id");
    order.userId = number(row, "userId");
    order.merchantId = number(row, "merchantId");
    order.merchantName = text(merchant, "name", "未知商家");
    order.type = enumValue(OrderType.class, first(row, "orderType", "type"), deal == null ? OrderType.DELIVERY : OrderType.GROUP_BUY);
    order.status = enumValue(OrderStatus.class, row.get("status"), OrderStatus.PENDING_PAYMENT);
    order.totalCent = number(row, "totalCent");
    order.clientRequestId = textOrNull(row.get("clientRequestId"));
    order.couponCode = textOrNull(first(row, "couponCode", "code"));
    order.addressSnapshot = textOrNull(row.get("addressSnapshot"));
    order.addressId = nullableLong(row.get("addressId"));
    order.reviewed = bool(row.get("reviewed"));
    order.createdAt = date(row.get("createdAt"));
    order.statusTimeline.put(order.status, order.createdAt);

    long itemId = number(row, "productId");
    int quantity = Math.max(1, integer(row.get("quantity")));
    if (deal != null) {
      itemId = number(deal, "id", itemId);
      order.lines.add(new OrderLine(itemId, text(deal, "title", "团购套餐 #" + itemId), quantity,
        number(deal, "priceCent", order.totalCent / quantity)));
    } else if (product != null) {
      itemId = number(product, "id", itemId);
      order.lines.add(new OrderLine(itemId, text(product, "name", "商品 #" + itemId), quantity,
        number(product, "priceCent", order.totalCent / quantity)));
    } else if (itemId > 0) {
      order.lines.add(new OrderLine(itemId, "商品 #" + itemId, quantity,
        order.totalCent / quantity));
    }
    return order;
  }

  private static Object first(Map<?, ?> row, String first, String second) {
    Object value = row.get(first);
    return value == null ? row.get(second) : value;
  }

  private static long number(Map<?, ?> row, String key) {
    return number(row, key, 0);
  }

  private static long number(Map<?, ?> row, String key, long fallback) {
    Object value = row == null ? null : row.get(key);
    return value instanceof Number number ? number.longValue() : parseLong(value, fallback);
  }

  private static int integer(Object value) {
    return value instanceof Number number ? number.intValue() : (int) parseLong(value, 0);
  }

  private static long parseLong(Object value, long fallback) {
    try {
      return value == null ? fallback : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static boolean bool(Object value) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    String text = textOrNull(value);
    return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
  }

  private static Long nullableLong(Object value) {
    if (value == null) return null;
    long parsed = parseLong(value, Long.MIN_VALUE);
    return parsed == Long.MIN_VALUE ? null : parsed;
  }

  private static String text(Map<?, ?> row, String key, String fallback) {
    String value = textOrNull(row == null ? null : row.get(key));
    return value == null ? fallback : value;
  }

  private static String textOrNull(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static <T extends Enum<T>> T enumValue(Class<T> type, Object value, T fallback) {
    String text = textOrNull(value);
    if (text == null) return fallback;
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }

  private static LocalDateTime date(Object value) {
    if (value instanceof LocalDateTime local) return local;
    String text = textOrNull(value);
    if (text != null) {
      try {
        return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
      } catch (DateTimeParseException ignored) {
        try {
          return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignoredAgain) {
          // Use the current time for malformed legacy rows; the order remains usable.
        }
      }
    }
    return LocalDateTime.now();
  }
}
