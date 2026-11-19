import { describe, expect, it } from "vitest";
import { detectPayScene } from "@/lib/payScene";

describe("payScene", () => {
  it("uses Native on desktop browsers", () => {
    expect(detectPayScene("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36")).toBe("native");
  });

  it("uses H5 on non-WeChat mobile browsers", () => {
    expect(detectPayScene("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148 Safari/604.1")).toBe("h5");
    expect(detectPayScene("Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile Safari/537.36")).toBe("h5");
  });

  it("blocks WeChat in-app browser", () => {
    expect(
      detectPayScene(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 MicroMessenger/8.0.47"
      )
    ).toBe("wechat-browser");
  });
});
