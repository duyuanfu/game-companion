import { describe, expect, it } from "vitest";
import { fenToYuan, fenToYuanInput, formatFen, formatYuan, yuanToFen } from "@/lib/money";

describe("money", () => {
  it("converts fen to yuan", () => {
    expect(fenToYuan(5000)).toBe(50);
    expect(fenToYuan(0)).toBe(0);
    expect(fenToYuan(undefined)).toBe(0);
  });

  it("converts yuan to fen with rounding", () => {
    expect(yuanToFen(50)).toBe(5000);
    expect(yuanToFen("25.55")).toBe(2555);
    expect(yuanToFen(0.1 + 0.2)).toBe(30);
    expect(yuanToFen("abc")).toBe(0);
  });

  it("formats fen with two decimals and optional symbol", () => {
    expect(formatFen(5000)).toBe("50.00");
    expect(formatFen(5000, true)).toBe("¥50.00");
    expect(formatFen(30)).toBe("0.30");
    expect(formatFen(undefined)).toBe("0.00");
  });

  it("formats yuan without double conversion", () => {
    expect(formatYuan(25)).toBe("25.00");
    expect(formatYuan(25, true)).toBe("¥25.00");
  });

  it("produces editable yuan input values", () => {
    expect(fenToYuanInput(5000)).toBe("50");
    expect(fenToYuanInput(2550)).toBe("25.50");
  });
});
