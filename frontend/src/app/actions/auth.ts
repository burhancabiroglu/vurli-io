"use server";

import { redirect } from "next/navigation";

import { api, clearSessionCookie, login } from "@/lib/api";

/**
 * What a form shows after submitting: nothing yet, success, or an error key. On error the
 * email is echoed back so the form keeps it; passwords never are.
 */
export type FormState = { status: "idle" } | { status: "done" } | { status: "error"; error: string; email?: string };

function field(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

/**
 * Always lands on the dashboard. A "return to" parameter from the request would be an
 * open redirect.
 */
export async function loginAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email");
  const result = await login(email, field(formData, "password"));
  if (!result.ok) {
    return { status: "error", error: result.error, email };
  }
  redirect("/");
}

/** Spring answers the same for new and existing addresses; so do we. */
export async function registerAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email");
  const result = await api("/api/v1/auth/register", {
    method: "POST",
    body: { email, password: field(formData, "password") },
  });
  return result.ok ? { status: "done" } : { status: "error", error: result.error, email };
}

export async function verifyEmailAction(token: string): Promise<FormState> {
  const result = await api("/api/v1/auth/verify-email", { method: "POST", body: { token } });
  return result.ok ? { status: "done" } : { status: "error", error: result.error };
}

export async function resendVerificationAction(): Promise<FormState> {
  const result = await api("/api/v1/auth/verification-email", { method: "POST" });
  return result.ok ? { status: "done" } : { status: "error", error: result.error };
}

export async function forgotPasswordAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email");
  const result = await api("/api/v1/auth/password-reset", { method: "POST", body: { email } });
  return result.ok ? { status: "done" } : { status: "error", error: result.error, email };
}

/** Spring signs the account out everywhere; the visitor signs in again with the new password. */
export async function resetPasswordAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const result = await api("/api/v1/auth/password-reset/confirm", {
    method: "POST",
    body: { token: field(formData, "token"), password: field(formData, "password") },
  });
  if (!result.ok) {
    return { status: "error", error: result.error };
  }
  await clearSessionCookie();
  redirect("/login?reset=1");
}

export async function logoutAction(): Promise<void> {
  await api("/api/v1/auth/logout", { method: "POST" });
  await clearSessionCookie();
  redirect("/login");
}
