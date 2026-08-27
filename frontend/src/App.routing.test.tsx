/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Order, User } from "./types";

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("./api", () => ({ api: apiMock }));
vi.mock("./pages/Login", () => ({ default: () => <div>login-page</div> }));
vi.mock("./pages/Home", () => ({ default: () => <div>home-page</div> }));
vi.mock("./pages/Detail", () => ({ default: ({ detail, addCart, buyDeal, setMessage }: { detail: { merchant: { id: number } }; addCart: (id: number) => Promise<void>; buyDeal: (id: number) => Promise<void>; setMessage: (message: string) => void }) => <div>detail-{detail.merchant.id}<button onClick={() => void addCart(11).catch(() => undefined)}>mock-add-cart</button><button onClick={() => void buyDeal(21).catch(error => setMessage(error instanceof Error ? error.message : "购买失败"))}>mock-buy-deal</button></div> }));
vi.mock("./pages/Cart", () => ({ default: () => <div>cart-page</div> }));
vi.mock("./pages/Orders", () => ({ default: () => <div>orders-page</div> }));
vi.mock("./pages/Profile", () => ({ default: () => <div>profile-page</div> }));
vi.mock("./pages/Favorites", () => ({ default: () => <div>favorites-page</div> }));
vi.mock("./pages/Assistant", () => ({ default: () => <div>assistant-page</div> }));
vi.mock("./pages/MerchantOrders", () => ({ default: () => <div>merchant-orders-page</div> }));
vi.mock("./pages/MerchantProducts", () => ({ default: () => <div>merchant-products-page</div> }));
vi.mock("./pages/MerchantSupport", () => ({ default: () => <div>merchant-support-page</div> }));
vi.mock("./pages/MerchantShop", () => ({ default: () => <div>merchant-shop-page</div> }));
vi.mock("./pages/Admin", () => ({ default: () => <div>admin-page</div> }));

const merchantUser: User = {
  id: 2,
  phone: "13800000002",
  nickname: "演示商家",
  role: "MERCHANT_ADMIN",
  merchantId: 1
};

const regularUser: User = {
  id: 1,
  phone: "13800000001",
  nickname: "演示用户",
  role: "USER"
};

function mockCommonApi(authResult?: User | Error) {
  apiMock.mockImplementation((path: string) => {
    if (path === "/api/v1/categories") return Promise.resolve([]);
    if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
    if (path === "/api/v1/auth/me") {
      return authResult instanceof Error ? Promise.reject(authResult) : Promise.resolve(authResult);
    }
    return Promise.resolve([]);
  });
}

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState(null, "", "/");
  apiMock.mockReset();
  mockCommonApi();
});

afterEach(() => cleanup());

describe("App route wiring", () => {
  it("normalizes an empty hash to the stable discover route", async () => {
    render(<App />);

    await waitFor(() => expect(window.location.hash).toBe("#/discover"));
    expect(screen.getByText("home-page")).toBeTruthy();
  });

  it("redirects a guest from a protected route to login", async () => {
    window.history.replaceState(null, "", "#/cart");
    render(<App />);

    await waitFor(() => expect(window.location.hash).toBe("#/login"));
    expect(screen.getByText("login-page")).toBeTruthy();
    expect(screen.getByText("请先登录后再访问该页面")).toBeTruthy();
  });

  it("clears an invalid token before applying the guest guard", async () => {
    localStorage.setItem("lumalife-token", "expired-token");
    window.history.replaceState(null, "", "#/orders");
    mockCommonApi(new Error("token expired"));
    render(<App />);

    await waitFor(() => expect(window.location.hash).toBe("#/login"));
    expect(localStorage.getItem("lumalife-token")).toBeNull();
    expect(screen.getByText("login-page")).toBeTruthy();
  });

  it("redirects a merchant away from a user route to its workspace", async () => {
    localStorage.setItem("lumalife-token", "merchant-token");
    window.history.replaceState(null, "", "#/orders");
    mockCommonApi(merchantUser);
    render(<App />);

    await waitFor(() => expect(window.location.hash).toBe("#/merchant/orders"));
    expect(screen.getByText("merchant-orders-page")).toBeTruthy();
    expect(screen.getByText("当前角色无权访问该页面，已返回工作台")).toBeTruthy();
  });

  it("keeps the latest merchant when detail responses finish out of order", async () => {
    let resolveFirst!: (value: unknown) => void;
    let resolveSecond!: (value: unknown) => void;
    const first = new Promise(resolve => { resolveFirst = resolve; });
    const second = new Promise(resolve => { resolveSecond = resolve; });
    apiMock.mockImplementation((path: string) => {
      if (path === "/api/v1/categories") return Promise.resolve([]);
      if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
      if (path === "/api/v1/merchants/1") return first;
      if (path === "/api/v1/merchants/2") return second;
      return Promise.resolve([]);
    });
    window.history.replaceState(null, "", "#/merchants/1");
    render(<App />);
    await waitFor(() => expect(apiMock).toHaveBeenCalledWith("/api/v1/merchants/1"));

    act(() => {
      window.history.pushState(null, "", "#/merchants/2");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await waitFor(() => expect(apiMock).toHaveBeenCalledWith("/api/v1/merchants/2"));

    await act(async () => resolveSecond({ merchant: { id: 2 } }));
    expect(await screen.findByText("detail-2")).toBeTruthy();
    await act(async () => resolveFirst({ merchant: { id: 1 } }));
    expect(screen.getByText("detail-2")).toBeTruthy();
    expect(screen.queryByText("detail-1")).toBeNull();
  });

  it("redirects guest purchase actions to login without calling protected APIs", async () => {
    window.history.replaceState(null, "", "#/merchants/1");
    apiMock.mockImplementation((path: string) => {
      if (path === "/api/v1/categories") return Promise.resolve([]);
      if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
      if (path === "/api/v1/merchants/1") return Promise.resolve({ merchant: { id: 1 } });
      return Promise.resolve([]);
    });
    render(<App />);

    fireEvent.click(await screen.findByText("mock-add-cart"));

    await waitFor(() => expect(window.location.hash).toBe("#/login"));
    expect(screen.getByText("请先登录后再加购商品")).toBeTruthy();
    expect(apiMock).not.toHaveBeenCalledWith("/api/v1/cart/items", expect.anything());
  });

  it("redirects a guest group-buy action without creating an order", async () => {
    window.history.replaceState(null, "", "#/merchants/1");
    apiMock.mockImplementation((path: string) => {
      if (path === "/api/v1/categories") return Promise.resolve([]);
      if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
      if (path === "/api/v1/merchants/1") return Promise.resolve({ merchant: { id: 1 } });
      return Promise.resolve([]);
    });
    render(<App />);

    fireEvent.click(await screen.findByText("mock-buy-deal"));

    await waitFor(() => expect(window.location.hash).toBe("#/login"));
    expect(screen.getByText("请先登录后再购买团购套餐")).toBeTruthy();
    expect(apiMock).not.toHaveBeenCalledWith("/api/v1/orders/group-buy", expect.anything());
  });

  it("retries payment for the same group-buy order after an uncertain response", async () => {
    localStorage.setItem("lumalife-token", "user-token");
    window.history.replaceState(null, "", "#/merchants/1");
    const pendingOrder: Order = { id: 501, merchantId: 1, type: "GROUP_BUY", status: "PENDING_PAYMENT", totalCent: 6800, reviewed: false, lines: [] };
    const paidOrder: Order = { ...pendingOrder, status: "PAID", couponCode: "123456789012" };
    let paymentAttempts = 0;
    apiMock.mockImplementation((path: string) => {
      if (path === "/api/v1/categories") return Promise.resolve([]);
      if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
      if (path === "/api/v1/auth/me") return Promise.resolve(regularUser);
      if (path === "/api/v1/user/favorites") return Promise.resolve([]);
      if (path === "/api/v1/merchants/1") return Promise.resolve({ merchant: { id: 1 } });
      if (path === "/api/v1/orders/group-buy") return Promise.resolve(pendingOrder);
      if (path === "/api/v1/payments") {
        paymentAttempts += 1;
        return paymentAttempts === 1 ? Promise.reject(new Error("支付响应超时")) : Promise.resolve(paidOrder);
      }
      if (path === "/api/v1/orders") return Promise.resolve(paymentAttempts > 1 ? [paidOrder] : [pendingOrder]);
      return Promise.resolve([]);
    });
    render(<App />);

    const buyButton = await screen.findByText("mock-buy-deal");
    fireEvent.click(buyButton);
    expect(await screen.findByText(/团购订单 #501 已创建，支付结果未确认/)).toBeTruthy();

    fireEvent.click(buyButton);
    await waitFor(() => expect(window.location.hash).toBe("#/orders"));

    const groupOrderCalls = apiMock.mock.calls.filter(([path]) => path === "/api/v1/orders/group-buy");
    const paymentCalls = apiMock.mock.calls.filter(([path]) => path === "/api/v1/payments");
    expect(groupOrderCalls).toHaveLength(1);
    expect(paymentCalls).toHaveLength(2);
    const firstPayment = JSON.parse(paymentCalls[0][1].body);
    const secondPayment = JSON.parse(paymentCalls[1][1].body);
    expect(firstPayment.orderId).toBe(501);
    expect(secondPayment.orderId).toBe(501);
    expect(secondPayment.clientRequestId).toBe(firstPayment.clientRequestId);
    expect(screen.getByText("orders-page")).toBeTruthy();
  });

  it("accepts the reconciled paid state when the payment response times out", async () => {
    localStorage.setItem("lumalife-token", "user-token");
    window.history.replaceState(null, "", "#/merchants/1");
    const pendingOrder: Order = { id: 502, merchantId: 1, type: "GROUP_BUY", status: "PENDING_PAYMENT", totalCent: 6800, reviewed: false, lines: [] };
    const paidOrder: Order = { ...pendingOrder, status: "PAID", couponCode: "210987654321" };
    apiMock.mockImplementation((path: string) => {
      if (path === "/api/v1/categories") return Promise.resolve([]);
      if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
      if (path === "/api/v1/auth/me") return Promise.resolve(regularUser);
      if (path === "/api/v1/user/favorites") return Promise.resolve([]);
      if (path === "/api/v1/merchants/1") return Promise.resolve({ merchant: { id: 1 } });
      if (path === "/api/v1/orders/group-buy") return Promise.resolve(pendingOrder);
      if (path === "/api/v1/payments") return Promise.reject(new Error("支付响应超时"));
      if (path === "/api/v1/orders") return Promise.resolve([paidOrder]);
      return Promise.resolve([]);
    });
    render(<App />);

    fireEvent.click(await screen.findByText("mock-buy-deal"));

    await waitFor(() => expect(window.location.hash).toBe("#/orders"));
    expect(screen.getByText("团购支付已确认，券码 210987654321")).toBeTruthy();
    expect(apiMock.mock.calls.filter(([path]) => path === "/api/v1/orders/group-buy")).toHaveLength(1);
    expect(apiMock.mock.calls.filter(([path]) => path === "/api/v1/payments")).toHaveLength(1);
  });
});
