import { describe, expect, it } from "vitest";

import { isLinkId, isoToLocalInput, linkState, localInputToIso, parsePage, parseVersion } from "./links";

describe("isLinkId", () => {
  it("accepts UUIDs", () => {
    expect(isLinkId("0b8f6a3e-4c1d-4e2f-9a7b-1c2d3e4f5a6b")).toBe(true);
    expect(isLinkId("0B8F6A3E-4C1D-4E2F-9A7B-1C2D3E4F5A6B")).toBe(true);
  });

  it.each([
    "../auth/logout",
    "0b8f6a3e-4c1d-4e2f-9a7b-1c2d3e4f5a6b/../../auth/logout",
    "0b8f6a3e-4c1d-4e2f-9a7b-1c2d3e4f5a6b?version=0",
    "%2e%2e",
    "",
    "abc",
  ])("rejects %j", (value) => {
    expect(isLinkId(value)).toBe(false);
  });

  it("rejects non-strings", () => {
    expect(isLinkId(null)).toBe(false);
    expect(isLinkId(42)).toBe(false);
  });
});

describe("parseVersion", () => {
  it("reads non-negative integers", () => {
    expect(parseVersion("0")).toBe(0);
    expect(parseVersion("17")).toBe(17);
  });

  it.each(["", "-1", "1.5", "1e3", " 1", "1&x=2", "9999999999999999"])("rejects %j", (value) => {
    expect(parseVersion(value)).toBeNull();
  });
});

describe("parsePage", () => {
  it("defaults to the first page", () => {
    expect(parsePage(undefined)).toBe(0);
    expect(parsePage("-3")).toBe(0);
    expect(parsePage(["1", "2"])).toBe(0);
    expect(parsePage("2")).toBe(2);
  });
});

describe("linkState", () => {
  const now = new Date("2026-10-15T12:00:00Z");

  it("labels stored states", () => {
    expect(linkState({ status: "ACTIVE", expiresAt: null }, now)).toBe("active");
    expect(linkState({ status: "DISABLED", expiresAt: null }, now)).toBe("disabled");
    expect(linkState({ status: "BLOCKED", expiresAt: null }, now)).toBe("blocked");
  });

  it("treats the exact expiry moment as expired, like the redirect does", () => {
    expect(linkState({ status: "ACTIVE", expiresAt: "2026-10-15T12:00:00Z" }, now)).toBe("expired");
    expect(linkState({ status: "ACTIVE", expiresAt: "2026-10-15T12:00:01Z" }, now)).toBe("active");
  });

  it("shows a block even after expiry", () => {
    expect(linkState({ status: "BLOCKED", expiresAt: "2020-01-01T00:00:00Z" }, now)).toBe("blocked");
  });
});

describe("local datetime conversion", () => {
  it("round-trips through the browser time zone", () => {
    const iso = localInputToIso("2026-10-20T18:30");
    expect(iso).not.toBeNull();
    expect(isoToLocalInput(iso)).toBe("2026-10-20T18:30");
  });

  it("maps empty to no expiry", () => {
    expect(localInputToIso("")).toBeNull();
    expect(isoToLocalInput(null)).toBe("");
  });

  it("rejects garbage", () => {
    expect(localInputToIso("yarın")).toBeNull();
  });
});
