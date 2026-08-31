/** @vitest-environment jsdom */

import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("api response handling", () => {
  it("turns an HTML 413 response into a readable upload error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>too large</html>", {
      status: 413,
      headers: { "Content-Type": "text/html" }
    })));

    await expect(api("/api/v1/user/profile", { method: "POST" }))
      .rejects.toThrow("请求内容过大，请压缩头像后重试");
  });

  it("preserves a JSON API error message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 40000, message: "请求体格式错误" }),
      { status: 400, headers: { "Content-Type": "application/json" } }
    )));

    await expect(api("/api/v1/user/profile", { method: "POST" }))
      .rejects.toThrow("请求体格式错误");
  });
});
