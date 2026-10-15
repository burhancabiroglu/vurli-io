import { expect, test } from "@playwright/test";

import tr from "../src/lib/i18n/dictionaries/tr.json";
import {
  ADMIN_URL,
  PASSWORD,
  promoteToAdministrator,
  registerAndSignIn,
  totpNow,
  uniqueEmail,
  visit,
} from "./support";

/** Texts of the admin panel (backend messages_tr.properties). */
const ADMIN_TEXT = {
  email: "E-posta",
  password: "Şifre",
  continue: "Devam",
  code: "Kod",
  confirm: "Onayla",
  stored: "Kaydettim",
  findLinks: "Link ara",
  search: "Ara",
  reason: "Gerekçe (zorunlu)",
  block: "Engelle",
  blocked: "Link engellendi.",
};

/**
 * M3's definition of done: a block made in the admin panel stops the short link for the
 * next visitor (roadmap). Everything goes through the real screens and the real backend.
 */
test("an administrator blocks a link and its next visit is refused", async ({ browser, page, request }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const alias = `engel-${suffix}`;
  // Unique per run: the audit log keeps entries of earlier runs.
  const reason = `uçtan uca test ${suffix}: phishing şüphesi`;
  const consoleErrors: string[] = [];

  await test.step("an owner creates a link that redirects", async () => {
    await registerAndSignIn(page, request, uniqueEmail("e2e-owner"));
    await page.getByLabel(tr.links.targetUrl).fill("https://example.com/supheli");
    await page.getByLabel(tr.links.alias).fill(alias);
    await page.getByRole("button", { name: tr.links.create }).click();
    await expect(page).toHaveURL(/\/links\/[0-9a-f-]{36}\?created=1$/);
    expect((await visit(request, alias)).status).toBe(302);
  });

  const adminEmail = uniqueEmail("e2e-admin");
  const adminContext = await browser.newContext({ locale: "tr-TR" });
  const admin = await adminContext.newPage();
  admin.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push(message.text());
    }
  });

  await test.step("an administrator sets up the second factor and signs in", async () => {
    await registerAndSignIn(admin, request, adminEmail);
    promoteToAdministrator(adminEmail);
    await admin.goto(`${ADMIN_URL}/admin/login`);
    await admin.getByLabel(ADMIN_TEXT.email).fill(adminEmail);
    await admin.getByLabel(ADMIN_TEXT.password).fill(PASSWORD);
    await admin.getByRole("button", { name: ADMIN_TEXT.continue }).click();
    await expect(admin).toHaveURL(`${ADMIN_URL}/admin/login/setup`);
    const secret = (await admin.locator("code.secret").textContent())!.trim();
    await admin.getByLabel(ADMIN_TEXT.code).fill(totpNow(secret));
    await admin.getByRole("button", { name: ADMIN_TEXT.confirm }).click();
    await expect(admin.locator("ul.codes li")).toHaveCount(10);
    await admin.getByRole("link", { name: ADMIN_TEXT.stored }).click();
    await expect(admin).toHaveURL(`${ADMIN_URL}/admin`);
  });

  await test.step("blocks the link with a reason", async () => {
    await admin.getByLabel(ADMIN_TEXT.findLinks).fill(alias);
    await admin.getByRole("button", { name: ADMIN_TEXT.search }).first().click();
    const row = admin.locator(`tr[data-code="${alias}"]`);
    await expect(row).toBeVisible();
    await row.getByLabel(ADMIN_TEXT.reason).fill(reason);
    await row.getByRole("button", { name: ADMIN_TEXT.block }).click();
    await expect(admin.getByText(ADMIN_TEXT.blocked)).toBeVisible();
  });

  await test.step("the next visit is refused and the owner sees the block", async () => {
    const after = await visit(request, alias);
    expect(after.status).toBe(410);
    expect(after.location).toBeUndefined();
    await page.reload();
    await expect(page.getByText(tr.links.state.blocked).first()).toBeVisible();
    await expect(page.getByText(tr.link.blockedHint)).toBeVisible();
  });

  await test.step("the block is in the audit log", async () => {
    await admin.goto(`${ADMIN_URL}/admin/audit`);
    const entry = admin.locator("tr", { hasText: reason });
    await expect(entry).toHaveCount(1);
    await expect(entry.getByText("LINK_BLOCKED")).toBeVisible();
    await expect(entry.getByText(adminEmail)).toBeVisible();
  });

  await adminContext.close();
  expect(consoleErrors).toEqual([]);
});
