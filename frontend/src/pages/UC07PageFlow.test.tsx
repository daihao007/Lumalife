/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";
import MerchantProducts from "./MerchantProducts";

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({ api: apiMock }));

const merchantUser = {
  id: 2,
  phone: "13800000002",
  nickname: "演示商家",
  role: "MERCHANT_ADMIN" as const,
  merchantId: 1
};

const user = {
  id: 1,
  phone: "13800000001",
  nickname: "演示用户",
  role: "USER" as const
};

const merchant = {
  id: 1,
  name: "晨雾咖啡局",
  categoryName: "咖啡茶饮",
  cover: "https://example.com/cover.png",
  avgScore: 4.7,
  avgPrice: 32,
  monthlySales: 268,
  distanceKm: 0.7,
  status: "营业中",
  address: "人民路 1 号",
  reason: "距离近、评价稳定"
};

const publishedProduct = {
  id: 9001,
  merchantId: 1,
  name: "页面闭环商品",
  description: "编辑前描述",
  priceCent: 2880,
  stock: 8,
  listed: true
};

let merchantProduct = { ...publishedProduct };
let userDetail: any;

function emptyDetail(products = [merchantProduct]) {
  return { merchant, products, groupDeals: [], reviews: [] };
}

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState(null, "", "/");
  merchantProduct = { ...publishedProduct };
  userDetail = emptyDetail();
  apiMock.mockReset();
  apiMock.mockImplementation((path: string, options: RequestInit = {}) => {
    if (path === "/api/v1/merchant-admin/products" && !options.method) return Promise.resolve([merchantProduct]);
    if (path === "/api/v1/merchant-admin/group-deals" && !options.method) return Promise.resolve([]);
    if (path === "/api/v1/merchant-admin/products" && options.method === "POST") {
      const body = JSON.parse(String(options.body));
      merchantProduct = { ...merchantProduct, ...body, priceCent: body.priceCent, listed: body.listed };
      return Promise.resolve(merchantProduct);
    }
    if (path === `/api/v1/merchant-admin/products/${merchantProduct.id}/toggle`) {
      merchantProduct = { ...merchantProduct, listed: !merchantProduct.listed };
      return Promise.resolve(merchantProduct);
    }
    if (path === "/api/v1/categories") return Promise.resolve([]);
    if (path.startsWith("/api/v1/merchants?")) return Promise.resolve({ records: [] });
    if (path === "/api/v1/auth/me") return Promise.resolve(user);
    if (path === "/api/v1/user/favorites") return Promise.resolve([]);
    if (path === "/api/v1/merchants/1") return Promise.resolve(userDetail);
    return Promise.resolve([]);
  });
});

afterEach(() => cleanup());

describe("UC07 页面级编辑发布下架闭环", () => {
  it("allows a merchant to edit, publish, and unpublish a product from the maintenance page", async () => {
    const setMessage = vi.fn();
    render(<MerchantProducts user={merchantUser} setMessage={setMessage} />);

    await screen.findByTestId("product-row-9001");
    fireEvent.click(screen.getByTestId("product-edit-9001"));
    fireEvent.change(screen.getByTestId("product-name"), { target: { value: "页面闭环商品-已编辑" } });
    fireEvent.change(screen.getByTestId("product-description"), { target: { value: "编辑后描述" } });
    fireEvent.click(screen.getByTestId("product-save"));

    await waitFor(() => expect(screen.getByTestId("product-name-9001").textContent).toContain("页面闭环商品-已编辑"));
    expect(setMessage).toHaveBeenCalledWith("商品已更新");
    expect(merchantProduct.description).toBe("编辑后描述");
    expect(merchantProduct.listed).toBe(true);

    fireEvent.click(screen.getByTestId("product-toggle-9001"));
    await waitFor(() => expect(screen.getByTestId("product-status-9001").textContent).toBe("下架"));
    expect(merchantProduct.listed).toBe(false);
  });

  it("shows the published product after a user refresh and removes it after unpublish", async () => {
    localStorage.setItem("lumalife-token", "user-token");
    window.history.replaceState(null, "", "#/merchants/1");

    const firstLoad = render(<App />);
    await screen.findByTestId("merchant-detail-page");
    expect(screen.getByTestId("detail-product-name-9001").textContent).toBe("页面闭环商品");
    expect(screen.getByTestId("add-cart-9001")).toBeTruthy();

    userDetail = emptyDetail([]);
    firstLoad.unmount();
    render(<App />);

    await screen.findByTestId("merchant-detail-page");
    expect(screen.queryByTestId("detail-product-name-9001")).toBeNull();
    expect(screen.queryByTestId("add-cart-9001")).toBeNull();
  });
});
