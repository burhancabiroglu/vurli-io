import "server-only";

import { cookies } from "next/headers";

import { CLIENT_HEADER, SESSION_COOKIE, errorKeyFrom, sessionIdFrom } from "./api-protocol";
import { getLocale } from "./i18n";

/**
 * The Spring API, reachable only from the server (K28). Read at request time from a
 * server-only variable, never from the incoming request.
 */
function apiBaseUrl(): string {
  return process.env.VURLI_API_URL ?? "http://localhost:8080";
}

type ApiPath = `/api/v1/${string}`;

export type ApiResult<T> = { ok: true; status: number; data: T } | { ok: false; status: number; error: string };

/**
 * Calls the Spring API on behalf of the current visitor. Of all browser cookies, only
 * the session cookie is forwarded; the browser never talks to Spring itself.
 */
export async function api<T = unknown>(
  path: ApiPath,
  init: { method?: "GET" | "POST" | "PUT" | "DELETE"; body?: unknown } = {},
): Promise<ApiResult<T>> {
  const response = await send(path, init.method ?? "GET", init.body);
  const text = await response.text();
  const body = text ? safeJson(text) : null;
  if (response.ok) {
    return { ok: true, status: response.status, data: body as T };
  }
  return { ok: false, status: response.status, error: errorKeyFrom(body) };
}

export type ApiFile = { contentType: string; disposition: string | null; content: ArrayBuffer };

/** A binary download such as a QR code; only the headers a download needs are kept. */
export async function apiFile(path: ApiPath): Promise<ApiResult<ApiFile>> {
  const response = await send(path, "GET");
  if (!response.ok) {
    const text = await response.text();
    return { ok: false, status: response.status, error: errorKeyFrom(text ? safeJson(text) : null) };
  }
  return {
    ok: true,
    status: response.status,
    data: {
      contentType: response.headers.get("Content-Type") ?? "application/octet-stream",
      disposition: response.headers.get("Content-Disposition"),
      content: await response.arrayBuffer(),
    },
  };
}

async function send(path: ApiPath, method: string, body?: unknown): Promise<Response> {
  const cookieStore = await cookies();
  const headers = new Headers({
    Accept: "application/json",
    "Accept-Language": await getLocale(),
    [CLIENT_HEADER]: "web",
  });
  const session = cookieStore.get(SESSION_COOKIE)?.value;
  if (session) {
    headers.set("Cookie", `${SESSION_COOKIE}=${session}`);
  }
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(new URL(path, apiBaseUrl()), {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    cache: "no-store",
    redirect: "manual",
  });
}

/**
 * Login on the API and, if it succeeds, hand the new session id to the browser as a
 * host-only, HttpOnly, SameSite=Lax cookie. Spring changes the id on every login.
 */
export async function login(email: string, password: string): Promise<ApiResult<unknown>> {
  const response = await fetch(new URL("/api/v1/auth/login", apiBaseUrl()), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "Accept-Language": await getLocale(),
      [CLIENT_HEADER]: "web",
    },
    body: JSON.stringify({ email, password }),
    cache: "no-store",
    redirect: "manual",
  });
  const text = await response.text();
  const body = text ? safeJson(text) : null;
  if (!response.ok) {
    return { ok: false, status: response.status, error: errorKeyFrom(body) };
  }
  const sessionId = sessionIdFrom(response.headers.getSetCookie());
  if (!sessionId) {
    return { ok: false, status: 502, error: "unexpected" };
  }
  (await cookies()).set({
    name: SESSION_COOKIE,
    value: sessionId,
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
  });
  return { ok: true, status: response.status, data: body };
}

export async function clearSessionCookie(): Promise<void> {
  (await cookies()).delete(SESSION_COOKIE);
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
