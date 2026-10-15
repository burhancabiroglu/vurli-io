"use client";

import { useActionState } from "react";

import { type FormState, resetPasswordAction } from "@/app/actions/auth";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/error-message";
import { useFragmentToken } from "@/hooks/use-fragment-token";

type Props = {
  labels: { password: string; passwordHint: string; submit: string; noToken: string };
  errors: Record<string, string>;
  unexpected: string;
};

export function ResetPasswordForm({ labels, errors, unexpected }: Props) {
  const token = useFragmentToken();
  const [state, formAction, pending] = useActionState<FormState, FormData>(resetPasswordAction, {
    status: "idle",
  });
  if (token === undefined) {
    return null;
  }
  if (token === null) {
    return <FormAlert message={labels.noToken} />;
  }
  return (
    <form action={formAction} className="flex flex-col gap-4">
      {state.status === "error" && <FormAlert message={errorMessage(errors, state.error, unexpected)} />}
      <input type="hidden" name="token" value={token} />
      <div className="flex flex-col gap-2">
        <Label htmlFor="password">{labels.password}</Label>
        <Input
          id="password"
          name="password"
          type="password"
          autoComplete="new-password"
          minLength={10}
          maxLength={128}
          aria-describedby="password-hint"
          required
        />
        <p id="password-hint" className="text-xs text-muted-foreground">
          {labels.passwordHint}
        </p>
      </div>
      <Button type="submit" disabled={pending}>
        {labels.submit}
      </Button>
    </form>
  );
}
