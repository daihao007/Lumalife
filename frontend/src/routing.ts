import type { Role } from "./types";

export type AppView =
  | "home"
  | "login"
  | "detail"
  | "cart"
  | "orders"
  | "favorites"
  | "profile"
  | "assistant"
  | "merchant-orders"
  | "merchant-products"
  | "merchant-support"
  | "merchant-shop"
  | "admin";

export type AppRoute = { view: AppView; merchantId?: number };

const STATIC_ROUTES: Record<Exclude<AppView, "detail">, string> = {
  home: "/discover",
  login: "/login",
  cart: "/cart",
  orders: "/orders",
  favorites: "/favorites",
  profile: "/profile",
  assistant: "/support",
  "merchant-orders": "/merchant/orders",
  "merchant-products": "/merchant/products",
  "merchant-support": "/merchant/support",
  "merchant-shop": "/merchant/shop",
  admin: "/admin"
};

const VIEW_BY_PATH = new Map(Object.entries(STATIC_ROUTES).map(([view, path]) => [path, view as AppView]));

const ACCESS: Record<AppView, Array<Role | "GUEST">> = {
  home: ["GUEST", "USER"],
  login: ["GUEST"],
  detail: ["GUEST", "USER"],
  cart: ["USER"],
  orders: ["USER"],
  favorites: ["USER"],
  profile: ["USER"],
  assistant: ["GUEST", "USER"],
  "merchant-orders": ["MERCHANT_ADMIN"],
  "merchant-products": ["MERCHANT_ADMIN"],
  "merchant-support": ["MERCHANT_ADMIN"],
  "merchant-shop": ["MERCHANT_ADMIN"],
  admin: ["PLATFORM_ADMIN"]
};

export function defaultView(role: Role | null): AppView {
  if (role === "MERCHANT_ADMIN") return "merchant-orders";
  if (role === "PLATFORM_ADMIN") return "admin";
  return "home";
}

export function canAccess(view: AppView, role: Role | null): boolean {
  return ACCESS[view].includes(role ?? "GUEST");
}

export function routeHash(view: AppView, merchantId?: number): string {
  if (view === "detail") {
    if (!merchantId || !Number.isInteger(merchantId) || merchantId < 1) return "#/discover";
    return `#/merchants/${merchantId}`;
  }
  return `#${STATIC_ROUTES[view]}`;
}

export function parseRoute(hash: string): AppRoute | null {
  const path = hash.replace(/^#/, "").split("?")[0].replace(/\/$/, "") || "/discover";
  const merchantMatch = path.match(/^\/merchants\/(\d+)$/);
  if (merchantMatch) {
    const merchantId = Number(merchantMatch[1]);
    return merchantId > 0 ? { view: "detail", merchantId } : null;
  }

  const view = VIEW_BY_PATH.get(path);
  return view ? { view } : null;
}

export function accessDeniedMessage(role: Role | null): string {
  return role ? "当前角色无权访问该页面，已返回工作台" : "请先登录后再访问该页面";
}
