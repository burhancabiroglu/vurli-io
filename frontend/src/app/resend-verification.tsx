"use client";

import { useActionState } from "react";

import { type FormState, resendVerificationAction } from "@/app/actions/auth";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/error-message";

type Props = {
  labels: { resend: string; resent: string };
  errors: Record<string, string>;
  unexpected: string;
};

export function ResendVerification({ labels, errors, unexpected }: Props) {
  const [state, formAction, pending] = useActionState<FormState>(resendVerificationAction, { status: "idle" });
  return (
    <form action={formAction} className="flex flex-col items-start gap-2">
      <Button type="submit" size="sm" variant="secondary" disabled={pending || state.status === "done"}>
        {labels.resend}
      </Button>
      <p className="text-sm" aria-live="polite">
        {state.status === "done" && labels.resent}
        {state.status === "error" && errorMessage(errors, state.error, unexpected)}
      </p>
    </form>
  );
}
