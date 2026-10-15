/** A link as the Spring API returns it (LinkView). */
export type Link = {
  id: string;
  code: string;
  shortUrl: string;
  targetUrl: string;
  status: "ACTIVE" | "DISABLED" | "BLOCKED";
  expiresAt: string | null;
  createdAt: string;
  version: number;
};

export type LinkPage = { items: Link[]; page: number; size: number; hasNext: boolean };

export type LinkState = "active" | "disabled" | "blocked" | "expired";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Link ids come back from the browser (forms, URLs) and end up in API paths. Anything
 * but a UUID could walk the path elsewhere ("../auth/logout"), so it never gets that far.
 */
export function isLinkId(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

/** The version a form last saw, or null if it is not a plain non-negative integer. */
export function parseVersion(value: unknown): number | null {
  if (typeof value !== "string" || !/^\d{1,15}$/.test(value)) {
    return null;
  }
  return Number(value);
}

/** Page number from the query string; anything odd means the first page. */
export function parsePage(value: unknown): number {
  if (typeof value !== "string" || !/^\d{1,6}$/.test(value)) {
    return 0;
  }
  return Number(value);
}

/**
 * What the owner should see. Spring decides whether a link redirects; this only labels
 * it, so an expired link reads "expired" even though its stored status is ACTIVE.
 */
export function linkState(link: Pick<Link, "status" | "expiresAt">, now: Date): LinkState {
  if (link.status === "BLOCKED") {
    return "blocked";
  }
  if (link.status === "DISABLED") {
    return "disabled";
  }
  if (link.expiresAt !== null && Date.parse(link.expiresAt) <= now.getTime()) {
    return "expired";
  }
  return "active";
}

/**
 * A datetime-local value ("2026-10-20T18:30") read in the browser's time zone, as an ISO
 * instant for the API. Empty means no expiry.
 */
export function localInputToIso(value: string): string | null {
  if (value === "") {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** The reverse, for prefilling a datetime-local input in the browser's time zone. */
export function isoToLocalInput(iso: string | null): string {
  if (iso === null) {
    return "";
  }
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Clicks of one link (Spring's LinkStatistics). Days are UTC dates ("2026-10-15"). */
export type LinkStatistics = {
  totalClicks: number;
  windowClicks: number;
  from: string;
  to: string;
  days: { day: string; clicks: number }[];
  referrers: Share[];
  countries: Share[];
  devices: Share[];
};

/** A breakdown entry; a null key means unknown (no referrer, no country). */
export type Share = { key: string | null; clicks: number };

/**
 * The listed entries plus one "other" entry for what the top list leaves out, so the
 * shares add up to the window's clicks.
 */
export function withOther(shares: Share[], windowClicks: number): { shares: Share[]; other: number } {
  const listed = shares.reduce((sum, share) => sum + share.clicks, 0);
  return { shares, other: Math.max(0, windowClicks - listed) };
}

/** Whole percent of the total, never NaN. */
export function percent(part: number, total: number): number {
  return total > 0 ? Math.round((part / total) * 100) : 0;
}
