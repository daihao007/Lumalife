import { describe, expect, it } from "vitest";
import { createPaymentRequestId } from "./utils";

describe("createPaymentRequestId", () => {
  it("uses Web Crypto UUIDs when the browser exposes them", () => {
    expect(createPaymentRequestId({ randomUUID: () => "browser-uuid" })).toBe("browser-uuid");
  });

  it("still creates distinct request IDs without a secure-context crypto API", () => {
    const first = createPaymentRequestId(null);
    const second = createPaymentRequestId(null);

    expect(first).toMatch(/^pay-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+$/);
    expect(second).not.toBe(first);
  });
});
