"use client";

import { useIsClient } from "@/hooks/use-is-client";

/**
 * A moment in the visitor's own time zone. The server does not know that zone, so it
 * renders the UTC date until the browser takes over.
 */
export function LocalTime({ iso, locale }: { iso: string; locale: string }) {
  const isClient = useIsClient();
  const text = isClient
    ? new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso))
    : iso.slice(0, 10);
  return <time dateTime={iso}>{text}</time>;
}
