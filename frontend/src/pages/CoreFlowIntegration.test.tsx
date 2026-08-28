/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Detail from "./Detail";
import Admin from "./Admin";

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({ api: apiMock }));
vi.mock("recharts", () => ({
  PieChart: ({ children }: any) => <div>{children}</div>,
  Pie: ({ children }: any) => <div>{children}</div>,
  Cell: () => null,
  Tooltip: () => null,
  Legend: () => null,
  LineChart: ({ children }: any) => <div>{children}</div>,
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  ResponsiveContainer: ({ children }: any) => <div>{children}</div>
}));

const detail = {
  merchant: { id: 1, name: "晨光餐厅", cover: "cover.png", address: "学府路 1 号", reason: "附近热门" },
  products: [{ id: 11, merchantId: 1, name: "招牌饭", description: "现做", priceCent: 2800, stock: 10, listed: true }],
  groupDeals: [{ id: 21, merchantId: 1, title: "双人套餐", description: "到店核销", priceCent: 6800, stock: 5, active: true }],
  reviews: []
};

const metrics = {
  overview: { users: 3, merchants: 2, todayOrders: 1, todayAmountCent: 2800 },
  orderStatusDistribution: { PAID: 1 },
  orderTypeDistribution: { DELIVERY: 1 },
  revenueTrend: [], merchantRanking: [],
  deliveryMetrics: { completionRate: 1, avgAcceptMinutes: 2, avgDeliveryMinutes: 18 },
  activeOrders: [], health: { status: "UP", source: "/actuator/health", pendingOrders: 1 }, userAccounts: [], merchantAccounts: [], logs: []
};

beforeEach(() => apiMock.mockReset());
afterEach(() => cleanup());

describe("D04 core flow integration", () => {
  it("locks a user purchase entry and reports the API failure inline", async () => {
    let rejectRequest!: (reason?: unknown) => void;
    const request = new Promise<void>((_, reject) => { rejectRequest = reject; });
    const addCart = vi.fn(() => request);
    const setMessage = vi.fn();

    render(<Detail detail={detail} addCart={addCart} buyDeal={vi.fn()} openCart={vi.fn()} backHome={vi.fn()} contactMerchant={vi.fn()} setMessage={setMessage} />);

    const button = screen.getByTestId("add-cart-11") as HTMLButtonElement;
    fireEvent.click(button);
    fireEvent.click(button);

    expect(addCart).toHaveBeenCalledTimes(1);
    expect(button.disabled).toBe(true);
    expect(screen.getByText("加购中…")).toBeTruthy();

    rejectRequest(new Error("商品库存不足"));
    expect((await screen.findByRole("alert")).textContent).toContain("商品库存不足");
    expect(setMessage).toHaveBeenCalledWith("商品库存不足");
    await waitFor(() => expect(button.disabled).toBe(false));
  });

  it("shows an understandable admin error and recovers through retry", async () => {
    apiMock.mockRejectedValueOnce(new Error("统计服务暂不可用")).mockResolvedValueOnce(metrics);

    render(<Admin />);

    expect((await screen.findByRole("alert")).textContent).toContain("统计服务暂不可用");
    fireEvent.click(screen.getByTestId("admin-retry"));

    expect(await screen.findByText("今日订单")).toBeTruthy();
    expect(screen.getByText("¥28.00")).toBeTruthy();
    expect(screen.getByTestId("admin-health").textContent).toContain("UP");
    expect(screen.getByTestId("admin-health").textContent).toContain("Actuator");
    expect(screen.getByTestId("admin-health").textContent).toContain("待处理订单1");
    expect(apiMock).toHaveBeenCalledTimes(2);
  });
});
