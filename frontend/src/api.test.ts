/** @vitest-environment jsdom */

import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

afterEach(() => {
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe("gateway API client", () => {
  it("uses only the frozen v1 gateway path and propagates auth plus request id", async () => {
    localStorage.setItem("lumalife-token", "token-1");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 200, message: "success", data: { id: 1 } }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api<{ id: number }>("/api/v1/auth/me")).resolves.toEqual({ id: 1 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/auth/me");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer token-1");
    expect(new Headers(init.headers).get("X-Request-Id")).toBeTruthy();
  });

  it("keeps the UI off internal service endpoints", async () => {
    await expect(api("/internal/v1/users/me")).rejects.toThrow("冻结契约仅允许前端访问网关");
  });

  it("preserves gateway error details for page-level recovery", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 50300, message: "订单服务暂不可用", requestId: "req-503", reason: "DEPENDENCY_UNAVAILABLE" }), { status: 503 })));

    await expect(api("/api/v1/orders")).rejects.toMatchObject({
      name: "GatewayApiError", status: 503, code: 50300, requestId: "req-503", reason: "DEPENDENCY_UNAVAILABLE"
    });
  });

  it("turns a non-envelope proxy failure into a safe error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("upstream unavailable", { status: 502 })));
    await expect(api("/api/v1/categories")).rejects.toThrow("网关请求失败（HTTP 502）");
  });
});
