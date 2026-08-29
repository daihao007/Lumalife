/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Cart from "./Cart";
import Orders from "./Orders";
import MerchantOrders from "./MerchantOrders";
import type { Order } from "../types";

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({ api: apiMock }));

const pendingOrder: Order = {
  id: 101,
  merchantId: 1,
  merchantName: "晨光餐厅",
  type: "DELIVERY",
  status: "PENDING_PAYMENT",
  totalCent: 2800,
  reviewed: false,
  lines: [{ name: "招牌饭", quantity: 1, priceCent: 2800 }]
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => apiMock.mockReset());
afterEach(() => cleanup());

describe("user ordering interactions", () => {
  it("locks checkout while the order is being created", async () => {
    const request = deferred<void>();
    const createDeliveryOrder = vi.fn(() => request.promise);

    render(<Cart
      cart={[{ productId: 11, merchantId: 1, merchantName: "晨光餐厅", name: "招牌饭", priceCent: 2800, quantity: 1, subtotalCent: 2800 }]}
      addresses={[{ id: 7, userId: 1, contactName: "小林", phone: "13800000000", detail: "教学楼 101", defaultAddress: true }]}
      selectedAddressId={7}
      setSelectedAddressId={vi.fn()}
      reload={vi.fn()}
      createDeliveryOrder={createDeliveryOrder}
      setMessage={vi.fn()}
    />);

    const submit = screen.getByTestId("checkout-submit");
    fireEvent.click(submit);
    fireEvent.click(submit);

    expect(createDeliveryOrder).toHaveBeenCalledTimes(1);
    expect((submit as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("订单创建中…")).toBeTruthy();

    request.resolve();
    await waitFor(() => expect((submit as HTMLButtonElement).disabled).toBe(false));
  });

  it("prevents duplicate payment and exposes the pending state", async () => {
    const request = deferred<void>();
    const pay = vi.fn(() => request.promise);

    render(<Orders user={{ role: "USER" }} orders={[pendingOrder]} reload={vi.fn()} pay={pay} cancelOrder={vi.fn()} receiveOrder={vi.fn()} setMessage={vi.fn()} />);

    const payButton = screen.getByTestId("pay-order-101");
    fireEvent.click(payButton);
    fireEvent.click(payButton);

    expect(pay).toHaveBeenCalledTimes(1);
    expect((payButton as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("支付中…")).toBeTruthy();

    request.resolve();
    await waitFor(() => expect((payButton as HTMLButtonElement).disabled).toBe(false));
  });

  it("shows an actionable message when payment fails and allows retry", async () => {
    const setMessage = vi.fn();
    const pay = vi.fn()
      .mockRejectedValueOnce(new Error("支付服务暂不可用"))
      .mockResolvedValueOnce(undefined);

    render(<Orders user={{ role: "USER" }} orders={[pendingOrder]} reload={vi.fn()} pay={pay} cancelOrder={vi.fn()} receiveOrder={vi.fn()} setMessage={setMessage} />);

    const payButton = screen.getByTestId("pay-order-101");
    fireEvent.click(payButton);
    await waitFor(() => expect(setMessage).toHaveBeenCalledWith("支付服务暂不可用"));
    expect((payButton as HTMLButtonElement).disabled).toBe(false);

    fireEvent.click(payButton);
    await waitFor(() => expect(pay).toHaveBeenCalledTimes(2));
  });
});

describe("merchant fulfillment interactions", () => {
  it("does not expose delivery transitions for group-buy orders", () => {
    apiMock.mockResolvedValue([]);
    const groupOrder: Order = { ...pendingOrder, type: "GROUP_BUY", status: "PAID", couponCode: "123456789012" };

    render(<MerchantOrders user={{ id: 2 }} orders={[groupOrder]} reload={vi.fn()} setMessage={vi.fn()} />);

    expect(screen.queryByTestId("transition-order-101")).toBeNull();
    expect(screen.queryByText("接单")).toBeNull();
  });

  it("completes a delivering order and prevents duplicate transitions", async () => {
    const request = deferred<unknown>();
    apiMock.mockImplementation((path = "") => {
      if (path === "/api/v1/merchant-admin/reviews") return Promise.resolve([]);
      if (path.includes("/transition")) return request.promise;
      return Promise.resolve(undefined);
    });
    const deliveringOrder = { ...pendingOrder, status: "DELIVERING" };

    render(<MerchantOrders user={{ id: 2 }} orders={[deliveringOrder]} reload={vi.fn()} setMessage={vi.fn()} />);

    const button = screen.getByTestId("transition-order-101");
    expect(screen.getByText("完成订单")).toBeTruthy();
    fireEvent.click(button);
    fireEvent.click(button);

    expect(apiMock).toHaveBeenCalledTimes(2);
    expect(apiMock).toHaveBeenCalledWith(
      "/api/v1/merchant-admin/orders/101/transition",
      { method: "POST", body: JSON.stringify({ next: "COMPLETED" }) }
    );
    expect((button as HTMLButtonElement).disabled).toBe(true);

    request.resolve(undefined);
    await waitFor(() => expect((button as HTMLButtonElement).disabled).toBe(false));
  });

  it("validates coupon format before verification and reports API errors", async () => {
    apiMock.mockImplementation((path = "") => {
      if (path === "/api/v1/merchant-admin/reviews") return Promise.resolve([]);
      if (path === "/api/v1/merchant-admin/coupons/verify") return Promise.reject(new Error("券码不可重复核销"));
      return Promise.resolve(undefined);
    });
    const setMessage = vi.fn();

    render(<MerchantOrders user={{ id: 2 }} orders={[]} reload={vi.fn()} setMessage={setMessage} />);

    const input = screen.getByLabelText("团购券码");
    const verifyButton = screen.getByTestId("verify-coupon");
    expect((verifyButton as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(input, { target: { value: "123" } });
    expect(screen.getByTestId("coupon-format-error").textContent).toContain("请输入 12 位数字券码");

    fireEvent.change(input, { target: { value: "123456789012" } });
    expect((verifyButton as HTMLButtonElement).disabled).toBe(false);
    fireEvent.click(verifyButton);

    expect((await screen.findByTestId("merchant-orders-error")).textContent).toContain("券码不可重复核销");
    expect(setMessage).toHaveBeenCalledWith("券码不可重复核销");
    expect((input as HTMLInputElement).value).toBe("123456789012");
  });

  it("shows a page-level error when a stale fulfillment action is rejected", async () => {
    apiMock.mockImplementation((path = "") => {
      if (path === "/api/v1/merchant-admin/reviews") return Promise.resolve([]);
      if (path.includes("/transition")) return Promise.reject(new Error("非法订单状态流转"));
      return Promise.resolve(undefined);
    });
    const setMessage = vi.fn();

    render(<MerchantOrders user={{ id: 2 }} orders={[{ ...pendingOrder, status: "PAID" }]} reload={vi.fn()} setMessage={setMessage} />);

    fireEvent.click(screen.getByTestId("transition-order-101"));

    expect((await screen.findByTestId("merchant-orders-error")).textContent).toContain("非法订单状态流转");
    expect(setMessage).toHaveBeenCalledWith("非法订单状态流转");
    await waitFor(() => expect((screen.getByTestId("transition-order-101") as HTMLButtonElement).disabled).toBe(false));
  });

  it("shows a page-level error when another merchant's coupon is rejected", async () => {
    apiMock.mockImplementation((path = "") => {
      if (path === "/api/v1/merchant-admin/reviews") return Promise.resolve([]);
      if (path === "/api/v1/merchant-admin/coupons/verify") return Promise.reject(new Error("不能核销其他商家的券码"));
      return Promise.resolve(undefined);
    });
    const setMessage = vi.fn();

    render(<MerchantOrders user={{ id: 3 }} orders={[]} reload={vi.fn()} setMessage={setMessage} />);

    fireEvent.change(screen.getByLabelText("团购券码"), { target: { value: "123456789012" } });
    fireEvent.click(screen.getByTestId("verify-coupon"));

    expect((await screen.findByTestId("merchant-orders-error")).textContent).toContain("不能核销其他商家的券码");
    expect(setMessage).toHaveBeenCalledWith("不能核销其他商家的券码");
    await waitFor(() => expect((screen.getByTestId("verify-coupon") as HTMLButtonElement).disabled).toBe(false));
  });
});
