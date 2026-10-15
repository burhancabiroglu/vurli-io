"use client";

import { useActionState, useState } from "react";

import {
  type LinkFormState,
  changeExpiryAction,
  changeTargetAction,
  deleteLinkAction,
  setLinkEnabledAction,
} from "@/app/actions/links";
import { FormAlert } from "@/components/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useIsClient } from "@/hooks/use-is-client";
import { errorMessage } from "@/lib/error-message";
import { isoToLocalInput, localInputToIso } from "@/lib/links";

type Messages = { errors: Record<string, string>; unexpected: string; saved: string };

/** The link a form changes and the version it was rendered with (checked by Spring). */
type Target = { id: string; version: number };

const idle: LinkFormState = { status: "idle" };

function Result({ state, messages }: { state: LinkFormState; messages: Messages }) {
  if (state.status === "error") {
    return <FormAlert message={errorMessage(messages.errors, state.error, messages.unexpected)} />;
  }
  if (state.status === "done") {
    return <FormAlert tone="info" message={messages.saved} />;
  }
  return null;
}

function Hidden({ link }: { link: Target }) {
  return (
    <>
      <input type="hidden" name="id" value={link.id} />
      <input type="hidden" name="version" value={String(link.version)} />
    </>
  );
}

export function TargetForm({
  link,
  targetUrl,
  label,
  submit,
  disabled,
  messages,
}: {
  link: Target;
  targetUrl: string;
  label: string;
  submit: string;
  disabled: boolean;
  messages: Messages;
}) {
  const [state, formAction, pending] = useActionState(changeTargetAction, idle);
  const typed = state.status === "error" ? state.values?.targetUrl : undefined;
  return (
    <form action={formAction} className="flex flex-col gap-3">
      <Result state={state} messages={messages} />
      <Hidden link={link} />
      <Label htmlFor="targetUrl" className="sr-only">
        {label}
      </Label>
      {/* A new version (saved here or elsewhere) remounts the field with the current target. */}
      <Input
        key={link.version}
        id="targetUrl"
        name="targetUrl"
        type="text"
        inputMode="url"
        autoComplete="off"
        spellCheck={false}
        defaultValue={typed ?? targetUrl}
        required
        disabled={disabled}
      />
      <Button type="submit" variant="secondary" className="self-start" disabled={disabled || pending}>
        {submit}
      </Button>
    </form>
  );
}

export function ExpiryForm({
  link,
  expiresAt,
  label,
  hint,
  submit,
  disabled,
  messages,
}: {
  link: Target;
  expiresAt: string | null;
  label: string;
  hint: string;
  submit: string;
  disabled: boolean;
  messages: Messages;
}) {
  const [state, formAction, pending] = useActionState(changeExpiryAction, idle);
  // The browser's time zone is unknown on the server; the input fills in after hydration.
  const isClient = useIsClient();
  const typed = state.status === "error" ? state.values?.expiresAt : undefined;
  const current = isClient ? isoToLocalInput(typed || expiresAt) : "";

  function submitForm(formData: FormData) {
    const local = formData.get("expiresAtLocal");
    formData.delete("expiresAtLocal");
    formData.set("expiresAt", typeof local === "string" ? (localInputToIso(local) ?? "") : "");
    formAction(formData);
  }

  return (
    <form action={submitForm} className="flex flex-col gap-3">
      <Result state={state} messages={messages} />
      <Hidden link={link} />
      <Label htmlFor="expiresAtLocal" className="sr-only">
        {label}
      </Label>
      <Input
        key={`${isClient}-${link.version}-${current}`}
        id="expiresAtLocal"
        name="expiresAtLocal"
        type="datetime-local"
        aria-describedby="expiry-hint"
        defaultValue={current}
        disabled={disabled}
      />
      <p id="expiry-hint" className="text-xs text-muted-foreground">
        {hint}
      </p>
      <Button type="submit" variant="secondary" className="self-start" disabled={disabled || pending}>
        {submit}
      </Button>
    </form>
  );
}

export function StatusForm({
  link,
  enable,
  label,
  messages,
}: {
  link: Target;
  enable: boolean;
  label: string;
  messages: Messages;
}) {
  const [state, formAction, pending] = useActionState(setLinkEnabledAction, idle);
  return (
    <form action={formAction} className="flex flex-col gap-3">
      <Result state={state} messages={messages} />
      <Hidden link={link} />
      <input type="hidden" name="enabled" value={String(enable)} />
      <Button type="submit" variant="outline" className="self-start" disabled={pending}>
        {label}
      </Button>
    </form>
  );
}

export function DeleteForm({
  link,
  labels,
  messages,
}: {
  link: Target;
  labels: { delete: string; confirmDelete: string; cancel: string };
  messages: Messages;
}) {
  const [state, formAction, pending] = useActionState(deleteLinkAction, idle);
  const [confirming, setConfirming] = useState(false);
  return (
    <form action={formAction} className="flex flex-col gap-3">
      <Result state={state} messages={messages} />
      <Hidden link={link} />
      {confirming ? (
        <div className="flex flex-wrap gap-2">
          <Button type="submit" variant="destructive" disabled={pending}>
            {labels.confirmDelete}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setConfirming(false)}>
            {labels.cancel}
          </Button>
        </div>
      ) : (
        <Button type="button" variant="destructive" className="self-start" onClick={() => setConfirming(true)}>
          {labels.delete}
        </Button>
      )}
    </form>
  );
}

export function CopyButton({ text, labels }: { text: string; labels: { copy: string; copied: string } }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }
  return (
    <Button type="button" variant="outline" size="sm" onClick={copy} aria-live="polite">
      {copied ? labels.copied : labels.copy}
    </Button>
  );
}
