import { isIP } from "node:net";

/** Name of the Spring session cookie; the panel relays it, never reads it. */
export const SESSION_COOKIE = "VURLI_SESSION";

/** Spring rejects state-changing API requests without this header (CSRF defence). */
export const CLIENT_HEADER = "X-Vurli-Client";

/**
 * The session id from Spring's Set-Cookie headers. Only the value is taken: the panel
 * sets its own cookie attributes instead of trusting whatever comes back.
 */
export function sessionIdFrom(setCookieHeaders: string[]): string | null {
  for (const header of setCookieHeaders) {
    const [pair] = header.split(";");
    const separator = pair.indexOf("=");
    if (separator > 0 && pair.slice(0, separator).trim() === SESSION_COOKIE) {
      const value = pair.slice(separator + 1).trim();
      return /^[A-Za-z0-9+/=_-]{1,200}$/.test(value) ? value : null;
    }
  }
  return null;
}

/**
 * Turns a Spring problem detail into a dictionary key such as "invalid_token" or
 * "weak_password.TOO_SHORT". Unknown shapes become "unexpected".
 */
export function errorKeyFrom(body: unknown): string {
  if (typeof body !== "object" || body === null) {
    return "unexpected";
  }
  const { code, reason } = body as { code?: unknown; reason?: unknown };
  if (typeof code !== "string" || !/^[a-z_]{1,40}$/.test(code)) {
    return "unexpected";
  }
  return typeof reason === "string" && /^[A-Z_]{1,40}$/.test(reason) ? `${code}.${reason}` : code;
}

/**
 * The visitor's address to pass on to Spring for rate limits: the last X-Forwarded-For
 * entry, which Caddy (or Next.js itself, without a proxy) sets. Earlier entries can be
 * forged by the visitor. Anything that is not a plain IP address is dropped.
 */
export function clientAddressFrom(forwardedFor: string | null): string | null {
  if (!forwardedFor) {
    return null;
  }
  const last = forwardedFor.slice(forwardedFor.lastIndexOf(",") + 1).trim();
  return isIP(last) !== 0 ? last : null;
}
