import "server-only";

import { redirect } from "next/navigation";
import { cache } from "react";

import { api } from "./api";

export type CurrentUser = {
  id: string;
  email: string;
  emailVerified: boolean;
  locale: string;
  workspaceId: string;
};

/**
 * The signed-in user according to Spring, or null. Asked once per request; the cookie
 * alone proves nothing, so every page that needs a user goes through here.
 */
export const getCurrentUser = cache(async (): Promise<CurrentUser | null> => {
  const result = await api<CurrentUser>("/api/v1/auth/me");
  if (result.ok) {
    return result.data;
  }
  if (result.status === 401) {
    return null;
  }
  throw new Error(`Session check failed with status ${result.status}`);
});

export async function requireUser(): Promise<CurrentUser> {
  const user = await getCurrentUser();
  if (!user) {
    redirect("/login");
  }
  return user;
}
