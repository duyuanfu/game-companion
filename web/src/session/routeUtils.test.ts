import { describe, expect, it } from "vitest";
import { personaHome, safeRedirect } from "@/session/routeUtils";

describe("route utils", () => {
  it("lands each persona on the correct home", () => {
    expect(personaHome("client", 1)).toBe("/companions");
    expect(personaHome("companion", 2)).toBe("/companion/status");
    expect(personaHome("admin", 9)).toBe("/admin");
  });

  it("rejects open redirects and login loops", () => {
    expect(safeRedirect(null, "/companions")).toBe("/companions");
    expect(safeRedirect("https://evil.example", "/companions")).toBe("/companions");
    expect(safeRedirect("//evil.example", "/companions")).toBe("/companions");
    expect(safeRedirect("/login?redirect=/x", "/companions")).toBe("/companions");
    expect(safeRedirect("/orders", "/companions")).toBe("/orders");
  });
});
