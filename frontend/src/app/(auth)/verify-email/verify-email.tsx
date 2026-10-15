"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

import { verifyEmailAction } from "@/app/actions/auth";
import { FormAlert } from "@/components/form-alert";
import { buttonVariants } from "@/components/ui/button";
import { useFragmentToken } from "@/hooks/use-fragment-token";

type Props = { labels: { working: string; done: string; invalid: string; continue: string } };

export function VerifyEmail({ labels }: Props) {
  const token = useFragmentToken();
  const [outcome, setOutcome] = useState<"done" | "invalid" | null>(null);
  // Tokens are single use: never send the same one twice, even if the effect re-runs.
  const sent = useRef(false);

  useEffect(() => {
    if (!token || sent.current) {
      return;
    }
    sent.current = true;
    verifyEmailAction(token).then(
      (state) => setOutcome(state.status === "done" ? "done" : "invalid"),
      () => setOutcome("invalid"),
    );
  }, [token]);

  const result = token === null ? "invalid" : (outcome ?? "working");
  if (result === "working") {
    return (
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {labels.working}
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <FormAlert tone={result === "done" ? "info" : "error"} message={result === "done" ? labels.done : labels.invalid} />
      <Link href="/" className={buttonVariants()}>
        {labels.continue}
      </Link>
    </div>
  );
}
