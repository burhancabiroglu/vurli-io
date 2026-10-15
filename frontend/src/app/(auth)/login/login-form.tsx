"use client";

import { useActionState } from "react";

import { type FormState, loginAction } from "@/app/actions/auth";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/error-message";

type Props = {
  labels: { email: string; password: string; submit: string };
  errors: Record<string, string>;
  unexpected: string;
};

export function LoginForm({ labels, errors, unexpected }: Props) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(loginAction, { status: "idle" });
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
        <Input id="password" name="password" type="password" autoComplete="current-password" required />
      </div>
      <Button type="submit" disabled={pending}>
        {labels.submit}
      </Button>
    </form>
  );
}
