/** @vitest-environment jsdom */

import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { User } from "./types";

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("./api", () => ({ api: apiMock }));
vi.mock("./pages/Login", () => ({ default: () => <div>login-page</div> }));
vi.mock("./pages/Home", () => ({ default: () => <div>home-page</div> }));
vi.mock("./pages/Detail", () => ({ default: ({ detail }: { detail: { merchant: { id: number } } }) => <div>detail-{detail.merchant.id}</div> }));
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
});
