import { type NextRequest, NextResponse } from "next/server";

import { SESSION_COOKIE } from "@/lib/api-protocol";
import { contentSecurityPolicy } from "@/lib/csp";

/** Pages anyone may open; everything else needs a session. */
const PUBLIC_PATHS = new Set(["/login", "/register", "/forgot-password", "/reset-password", "/verify-email"]);

/**
 * Runs before every page:
 * - a fresh nonce for the Content-Security-Policy, so only our own scripts run;
 * - an optimistic redirect to /login when there is no session cookie at all.
 *
 * The cookie check proves nothing: pages still ask Spring who the user is
 * (lib/session.ts), and Spring makes every authorization decision.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (!PUBLIC_PATHS.has(pathname) && !request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const policy = contentSecurityPolicy(nonce, process.env.NODE_ENV === "development");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", policy);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: [
    {
      // Pages only: no static files, images, favicon or link prefetches.
      source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
