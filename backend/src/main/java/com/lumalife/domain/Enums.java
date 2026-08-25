package com.lumalife.domain;

public final class Enums {
  private Enums() {}

  public enum UserRole { USER, MERCHANT_ADMIN, PLATFORM_ADMIN }
  public enum OrderType { DELIVERY, GROUP_BUY }
  public enum OrderStatus { PENDING_PAYMENT, PAID, ACCEPTED, DELIVERING, RECEIVED, COMPLETED, USED, EXPIRED, CANCELLED }
  public enum PaymentStatus { SUCCESS, FAILED }
}
