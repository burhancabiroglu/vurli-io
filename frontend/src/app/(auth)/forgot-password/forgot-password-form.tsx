"use client";

import { useActionState } from "react";

import { type FormState, forgotPasswordAction } from "@/app/actions/auth";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/error-message";

type Props = {
  labels: { email: string; submit: string; sent: string };
  errors: Record<string, string>;
  unexpected: string;
};

export function ForgotPasswordForm({ labels, errors, unexpected }: Props) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(forgotPasswordAction, {
    status: "idle",
  });
  if (state.status === "done") {
    return <FormAlert tone="info" message={labels.sent} />;
  }
  return (
    <form action={formAction} className="flex flex-col gap-4">
      {state.status === "error" && <FormAlert message={errorMessage(errors, state.error, unexpected)} />}
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">{labels.email}</Label>
        <Input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          defaultValue={state.status === "error" ? state.email : undefined}
          required
        />
      </div>
      <Button type="submit" disabled={pending}>
        {labels.submit}
      </Button>
    </form>
  );
}
