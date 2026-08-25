import { describe, expect, it } from "vitest";
import { canAccess, defaultView, parseRoute, routeHash } from "./routing";

describe("core role routing", () => {
  it("maps stable hashes to core pages", () => {
    expect(parseRoute("")).toEqual({ view: "home" });
    expect(parseRoute("#/orders")).toEqual({ view: "orders" });
    expect(parseRoute("#/merchant/products")).toEqual({ view: "merchant-products" });
    expect(parseRoute("#/merchants/12")).toEqual({ view: "detail", merchantId: 12 });
    expect(parseRoute("#/unknown")).toBeNull();
  });

  it("builds traceable merchant detail hashes", () => {
    expect(routeHash("detail", 12)).toBe("#/merchants/12");
    expect(routeHash("detail")).toBe("#/discover");
  });

  it("allows users only into user pages", () => {
    expect(canAccess("orders", "USER")).toBe(true);
    expect(canAccess("merchant-orders", "USER")).toBe(false);
    expect(canAccess("admin", "USER")).toBe(false);
  });

  it("isolates merchant and platform workspaces", () => {
    expect(canAccess("merchant-orders", "MERCHANT_ADMIN")).toBe(true);
    expect(canAccess("orders", "MERCHANT_ADMIN")).toBe(false);
    expect(canAccess("admin", "PLATFORM_ADMIN")).toBe(true);
    expect(canAccess("merchant-shop", "PLATFORM_ADMIN")).toBe(false);
  });

  it("requires guests to sign in for protected pages", () => {
    expect(canAccess("home", null)).toBe(true);
    expect(canAccess("assistant", null)).toBe(true);
    expect(canAccess("cart", null)).toBe(false);
  });

  it("selects a safe landing page for every role", () => {
    expect(defaultView(null)).toBe("home");
    expect(defaultView("USER")).toBe("home");
    expect(defaultView("MERCHANT_ADMIN")).toBe("merchant-orders");
    expect(defaultView("PLATFORM_ADMIN")).toBe("admin");
  });
});
