/**
 * Content-Security-Policy for panel pages, following the Next.js nonce guide.
 * Development only relaxes three things: 'unsafe-eval' (React's error overlays), inline
 * styles (Next.js dev tools set style attributes) and plain http on localhost. A
 * production page was checked in the browser with no violations.
 */
export function contentSecurityPolicy(nonce: string, development: boolean): string {
  const directives = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`,
    development ? "style-src 'self' 'unsafe-inline'" : `style-src 'self' 'nonce-${nonce}'`,
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
  if (!development) {
    directives.push("upgrade-insecure-requests");
  }
  return directives.join("; ");
}
