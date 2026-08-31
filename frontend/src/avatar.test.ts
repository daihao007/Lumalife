import { describe, expect, it } from "vitest";
import { prepareAvatar } from "./avatar";

describe("prepareAvatar", () => {
  it("rejects non-image files before reading them", async () => {
    const file = new File(["not an image"], "notes.txt", { type: "text/plain" });
    await expect(prepareAvatar(file)).rejects.toThrow("请选择图片文件");
  });
});
