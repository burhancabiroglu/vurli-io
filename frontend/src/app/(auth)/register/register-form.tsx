"use client";

import { useActionState } from "react";

import { type FormState, registerAction } from "@/app/actions/auth";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/error-message";

type Props = {
  labels: {
    email: string;
    password: string;
    passwordHint: string;
    submit: string;
    sentTitle: string;
    sentBody: string;
  };
  errors: Record<string, string>;
  unexpected: string;
};

export function RegisterForm({ labels, errors, unexpected }: Props) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(registerAction, { status: "idle" });
  if (state.status === "done") {
    return (
      <div className="flex flex-col gap-2" aria-live="polite">
        <h2 className="font-medium">{labels.sentTitle}</h2>
        <p className="text-sm text-muted-foreground">{labels.sentBody}</p>
      </div>
    );
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
