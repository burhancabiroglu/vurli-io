"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";

import { type ApiResult, api } from "@/lib/api";
import { type Link, isLinkId, parseVersion } from "@/lib/links";

type LinkFormError = {
  status: "error";
  error: string;
  values?: { targetUrl?: string; alias?: string; expiresAt?: string };
};

/**
 * After a submit: nothing yet, saved, or an error key. Errors echo what was typed so the
 * form keeps it after React resets it.
 */
export type LinkFormState = { status: "idle" } | { status: "done" } | LinkFormError;

function field(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

/**
 * The link id and the version the form was rendered with. Both come from the browser, so
 * they are checked before they reach an API path. Spring still decides whether this
 * workspace owns the link and whether the version is current.
 */
function target(formData: FormData): { id: string; version: number } | null {
  const id = formData.get("id");
  const version = parseVersion(formData.get("version"));
  return isLinkId(id) && version !== null ? { id, version } : null;
}

/** An expired session sends the visitor to sign in; a stale page is re-rendered. */
function failed(result: ApiResult<unknown> & { ok: false }): LinkFormError {
  if (result.status === 401) {
    redirect("/login");
  }
  if (result.error === "version_conflict" || result.error === "link_not_found") {
    refresh();
  }
  return { status: "error", error: result.error };
}

/** On a conflict the form shows the current link instead of what was typed. */
function saved(result: ApiResult<unknown>, values?: LinkFormError["values"]): LinkFormState {
  if (!result.ok) {
    const error = failed(result);
    return error.error === "version_conflict" ? error : { ...error, values };
  }
  refresh();
  return { status: "done" };
}

export async function createLinkAction(_previous: LinkFormState, formData: FormData): Promise<LinkFormState> {
  const targetUrl = field(formData, "targetUrl");
  const alias = field(formData, "alias").trim();
  const expiresAt = field(formData, "expiresAt");
  const result = await api<Link>("/api/v1/links", {
    method: "POST",
    body: { targetUrl, alias: alias || null, expiresAt: expiresAt || null },
  });
  if (!result.ok) {
    return { ...failed(result), values: { targetUrl, alias, expiresAt } };
  }
  redirect(`/links/${result.data.id}?created=1`);
}

export async function changeTargetAction(_previous: LinkFormState, formData: FormData): Promise<LinkFormState> {
  const link = target(formData);
  if (!link) {
    return { status: "error", error: "unexpected" };
  }
  const targetUrl = field(formData, "targetUrl");
  return saved(
    await api(`/api/v1/links/${link.id}/target`, { method: "PUT", body: { version: link.version, targetUrl } }),
    { targetUrl },
  );
}

/** An empty value removes the expiry. */
export async function changeExpiryAction(_previous: LinkFormState, formData: FormData): Promise<LinkFormState> {
  const link = target(formData);
  if (!link) {
    return { status: "error", error: "unexpected" };
  }
  const expiresAt = field(formData, "expiresAt");
  return saved(
    await api(`/api/v1/links/${link.id}/expiry`, {
      method: "PUT",
      body: { version: link.version, expiresAt: expiresAt || null },
    }),
    { expiresAt },
  );
}

export async function setLinkEnabledAction(_previous: LinkFormState, formData: FormData): Promise<LinkFormState> {
  const link = target(formData);
  if (!link) {
    return { status: "error", error: "unexpected" };
  }
  const operation = field(formData, "enabled") === "true" ? "enable" : "disable";
  return saved(
    await api(`/api/v1/links/${link.id}/${operation}`, { method: "POST", body: { version: link.version } }),
  );
}

export async function deleteLinkAction(_previous: LinkFormState, formData: FormData): Promise<LinkFormState> {
  const link = target(formData);
  if (!link) {
    return { status: "error", error: "unexpected" };
  }
  const result = await api(`/api/v1/links/${link.id}?version=${link.version}`, { method: "DELETE" });
  if (!result.ok) {
    return failed(result);
  }
  redirect("/?deleted=1");
}
