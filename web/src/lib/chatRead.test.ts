import { describe, expect, it } from "vitest";
import { isConversationUnread } from "@/lib/chatRead";
import type { ChatConversation } from "@/types/api";

const base: ChatConversation = {
  roomSn: "ORDER_1",
  roomType: "order",
  peerUserId: 2,
  lastMessage: "你好",
  lastMessageTime: "2026-11-19T10:00:00",
  lastSenderId: 2,
  lastMessageType: 0
};

describe("isConversationUnread", () => {
  it("returns true when the peer's message is newer than the last read time", () => {
    expect(
      isConversationUnread({ ...base, lastReadTime: "2026-11-19T09:59:00" }, 1)
    ).toBe(true);
  });

  it("returns false when the room has been read after the last message", () => {
    expect(
      isConversationUnread({ ...base, lastReadTime: "2026-11-19T10:01:00" }, 1)
    ).toBe(false);
  });

  it("never reports unread for system messages", () => {
    expect(
      isConversationUnread(
        { ...base, lastMessageType: 1, lastSenderId: 0, lastReadTime: undefined },
        1
      )
    ).toBe(false);
  });
});
