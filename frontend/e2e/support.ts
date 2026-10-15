import { execFileSync } from "node:child_process";
import { createHmac } from "node:crypto";

import { type APIRequestContext, type Page, expect } from "@playwright/test";

import tr from "../src/lib/i18n/dictionaries/tr.json";

export const MAILPIT_URL = process.env.MAILPIT_URL ?? "http://localhost:8025";
export const SHORT_BASE_URL = process.env.SHORT_BASE_URL ?? "http://localhost:8080";
export const PASSWORD = "uçtan uca test şifresi";
export const BROWSER =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";

/** The verification link from the newest email to this address, as a user would click it. */
export async function verificationLink(request: APIRequestContext, email: string): Promise<string> {
  let link: string | undefined;
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${email}"` } });
        const { messages } = (await search.json()) as { messages: { ID: string }[] };
        if (messages.length === 0) {
          return undefined;
        }
        const message = await request.get(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`);
        const { Text } = (await message.json()) as { Text: string };
        link = Text.match(/https?:\/\/\S+\/verify-email#token=[A-Za-z0-9_-]+/)?.[0];
        return link;
      },
      { message: `verification email to ${email}`, timeout: 15_000 },
    )
    .toBeDefined();
  return link!;
}

/**
 * Asks the short link itself where it goes, without following the redirect. Identifies
 * as a monitor, so these checks are not counted as clicks (the request fixture would
 * otherwise send the desktop Chrome User-Agent of the test project).
 */
export async function visit(request: APIRequestContext, alias: string) {
  const response = await request.get(`${SHORT_BASE_URL}/${alias}`, {
    maxRedirects: 0,
    headers: { "User-Agent": "Vurli-E2E-Monitor/1.0" },
  });
  return { status: response.status(), location: response.headers()["location"] };
}

/** The admin panel; in the dev profile it shares localhost with the API. */
export const ADMIN_URL = process.env.ADMIN_URL ?? "http://localhost:8080";

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

/** Registers through the panel, follows the email link and signs in. */
export async function registerAndSignIn(page: Page, request: APIRequestContext, email: string): Promise<void> {
  await page.goto("/register");
  await page.getByLabel(tr.common.email).fill(email);
  await page.getByLabel(tr.common.password).fill(PASSWORD);
  await page.getByRole("button", { name: tr.register.submit }).click();
  await expect(page.getByText(tr.register.sentTitle)).toBeVisible();
  await page.goto(await verificationLink(request, email));
  await expect(page.getByText(tr.verify.done)).toBeVisible();
  await page.goto("/login");
  await page.getByLabel(tr.common.email).fill(email);
  await page.getByLabel(tr.common.password).fill(PASSWORD);
  await page.getByRole("button", { name: tr.login.submit }).click();
  await expect(page).toHaveURL("/");
}

/**
 * Makes an account an administrator directly in the database. In production this is the
 * VURLI_ADMIN_PROMOTE startup setting (K48), which runs before a test could register.
 */
export function promoteToAdministrator(email: string): void {
  if (!/^[a-z0-9.@-]+$/.test(email)) {
    throw new Error(`Unexpected email ${email}`);
  }
  execFileSync(
    "docker",
    [
      "compose",
      "-f",
      "../compose.yaml",
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "vurli",
      "-d",
      "vurli",
      "-v",
      "ON_ERROR_STOP=1",
      "-c",
      `update users set role = 'ADMIN' where email = '${email}'`,
    ],
    { stdio: "pipe" },
  );
}

/** The code an authenticator app shows for a Base32 secret (RFC 6238, SHA-1, 6 digits). */
export function totpNow(secret: string): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of secret.toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  }
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((byte) => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
  const hash = createHmac("sha1", key).update(counter).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}
