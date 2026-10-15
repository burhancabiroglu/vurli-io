"use client";

import { useActionState } from "react";

import { type LinkFormState, createLinkAction } from "@/app/actions/links";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/error-message";
import { isoToLocalInput, localInputToIso } from "@/lib/links";

type Props = {
  labels: {
    targetUrl: string;
    targetPlaceholder: string;
    alias: string;
    aliasHint: string;
    expiresAt: string;
    create: string;
  };
  errors: Record<string, string>;
  unexpected: string;
  disabled: boolean;
};

export function CreateLinkForm({ labels, errors, unexpected, disabled }: Props) {
  const [state, formAction, pending] = useActionState<LinkFormState, FormData>(createLinkAction, { status: "idle" });
  const values = state.status === "error" ? state.values : undefined;

  // The expiry is picked in the browser's time zone and sent as an instant.
  function submit(formData: FormData) {
    const local = formData.get("expiresAtLocal");
    formData.delete("expiresAtLocal");
    formData.set("expiresAt", typeof local === "string" ? (localInputToIso(local) ?? "") : "");
    formAction(formData);
  }

  return (
    <form action={submit} className="flex flex-col gap-4">
      {state.status === "error" && <FormAlert message={errorMessage(errors, state.error, unexpected)} />}
      <div className="flex flex-col gap-2">
        <Label htmlFor="targetUrl">{labels.targetUrl}</Label>
        <Input
          id="targetUrl"
          name="targetUrl"
          type="text"
          inputMode="url"
          autoComplete="off"
          spellCheck={false}
          placeholder={labels.targetPlaceholder}
          defaultValue={values?.targetUrl}
          required
          disabled={disabled}
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="alias">{labels.alias}</Label>
          <Input
            id="alias"
            name="alias"
            type="text"
            autoComplete="off"
            spellCheck={false}
            maxLength={50}
            aria-describedby="alias-hint"
            defaultValue={values?.alias}
            disabled={disabled}
          />
          <p id="alias-hint" className="text-xs text-muted-foreground">
            {labels.aliasHint}
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="expiresAtLocal">{labels.expiresAt}</Label>
          <Input
            id="expiresAtLocal"
            name="expiresAtLocal"
            type="datetime-local"
            defaultValue={values?.expiresAt ? isoToLocalInput(values.expiresAt) : undefined}
            disabled={disabled}
          />
        </div>
      </div>
      <Button type="submit" className="self-start" disabled={disabled || pending}>
        {labels.create}
      </Button>
    </form>
  );
}
