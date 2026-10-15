"use client";

import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

/**
 * False while rendering on the server and hydrating, true afterwards. For values that
 * depend on the browser, such as its time zone.
 */
export function useIsClient(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
}
