import { cn } from "cn";

import type { LinkState } from "@/lib/links";

const tones: Record<LinkState, string> = {
  active: "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200",
  disabled: "bg-muted text-muted-foreground",
  expired: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  blocked: "bg-destructive/10 text-destructive",
};

export function LinkStateBadge({ state, label }: { state: LinkState; label: string }) {
  return (
    <span className={cn("inline-flex rounded-md px-1.5 py-0.5 text-xs font-medium", tones[state])}>{label}</span>
  );
}
