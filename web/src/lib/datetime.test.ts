import { describe, expect, it } from "vitest";
import { formatDateTime, formatDuration, formatTime, formatWaitMinutes, parseServerDate } from "@/lib/datetime";

describe("datetime", () => {
  it("parses LocalDateTime with T and space separators", () => {
    expect(parseServerDate("2026-11-17T10:20:30")).toEqual({
      year: 2026,
      month: 11,
      day: 17,
      hour: 10,
      minute: 20,
      second: 30
    });
    expect(parseServerDate("2026-11-17 09:05")).toMatchObject({ hour: 9, minute: 5 });
  });

  it("formats without timezone drift", () => {
    expect(formatDateTime("2026-11-17T10:20:30")).toBe("2026-11-17 10:20");
    expect(formatDateTime("2026-11-17")).toBe("-");
    expect(formatTime("2026-11-17T10:20:30")).toBe("10:20");
  });

  it("formats countdown durations", () => {
    expect(formatDuration(0)).toBe("00:00");
    expect(formatDuration(65)).toBe("01:05");
    expect(formatDuration(3661)).toBe("01:01:01");
  });

  it("formats wait minutes", () => {
    expect(formatWaitMinutes(0)).toBe("0 分钟");
    expect(formatWaitMinutes(9)).toBe("9 分钟");
    expect(formatWaitMinutes(75)).toBe("1 小时 15 分钟");
    expect(formatWaitMinutes(60)).toBe("1 小时");
  });
});
