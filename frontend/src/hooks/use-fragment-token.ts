"use client";

import { useSyncExternalStore } from "react";

let captured: { path: string; token: string | null } | null = null;

/**
 * Takes the token from the URL fragment once and removes it from the address bar, so
 * it stays out of browser history and screen shares. A later visit with a new fragment
 * replaces it.
 */
function readToken(): string | null {
  const { hash, pathname, search } = window.location;
  const fromHash = new URLSearchParams(hash.slice(1)).get("token");
  if (fromHash !== null || captured?.path !== pathname) {
    captured = { path: pathname, token: fromHash };
  }
  if (hash) {
    window.history.replaceState(null, "", pathname + search);
  }
  return captured.token;
}

const subscribe = () => () => {};

/**
 * Reads `#token=...` from the URL. Fragments never reach a server, which is why the
 * emails carry tokens there (K38).
 *
 * @returns undefined during server rendering, null if there is no token, otherwise the token
 */
export function useFragmentToken(): string | null | undefined {
  return useSyncExternalStore(subscribe, readToken, () => undefined);
}
