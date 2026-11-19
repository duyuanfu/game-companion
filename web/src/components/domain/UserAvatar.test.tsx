import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { UserAvatar } from "@/components/domain/UserAvatar";

describe("UserAvatar", () => {
  it("uses the classic default avatar when no image is set", () => {
    render(<UserAvatar name="测试用户" />);
    expect(screen.getByRole("img", { name: "测试用户" })).toHaveAttribute(
      "src",
      "/default-avatar.svg"
    );
  });

  it("falls back to the default avatar when the image fails", () => {
    render(<UserAvatar src="https://example.com/broken.png" name="测试用户" />);
    fireEvent.error(screen.getByRole("img", { name: "测试用户" }));
    expect(screen.getByRole("img", { name: "测试用户" })).toHaveAttribute(
      "src",
      "/default-avatar.svg"
    );
  });
});
