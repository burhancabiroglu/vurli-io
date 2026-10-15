import { describe, expect, it } from "vitest";

import { contentSecurityPolicy } from "./csp";

describe("contentSecurityPolicy", () => {
  const production = contentSecurityPolicy("bm9uY2U=", false);

  it("allows only our own and nonce-bearing scripts in production", () => {
    expect(production).toContain("script-src 'self' 'nonce-bm9uY2U=' 'strict-dynamic';");
    expect(production).not.toContain("unsafe-eval");
    expect(production).not.toContain("unsafe-inline");
  });

  it("forbids framing, plugins and foreign form targets", () => {
    expect(production).toContain("frame-ancestors 'none'");
    expect(production).toContain("object-src 'none'");
    expect(production).toContain("form-action 'self'");
    expect(production).toContain("base-uri 'self'");
  });

  it("upgrades insecure requests only in production", () => {
    expect(production).toContain("upgrade-insecure-requests");
    expect(contentSecurityPolicy("n", true)).not.toContain("upgrade-insecure-requests");
  });

  it("relaxes scripts and styles only for development", () => {
    const development = contentSecurityPolicy("n", true);
    expect(development).toContain("'unsafe-eval'");
    expect(development).toContain("style-src 'self' 'unsafe-inline'");
    expect(production).toContain("style-src 'self' 'nonce-bm9uY2U='");
  });
});
