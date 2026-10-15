import { readFile } from "node:fs/promises";

import { expect, test } from "@playwright/test";

import tr from "../src/lib/i18n/dictionaries/tr.json";
import { BROWSER, PASSWORD, SHORT_BASE_URL, verificationLink, visit } from "./support";

test("a new user registers, creates a link, downloads its QR code and manages it", async ({ page, request }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const email = `e2e-${suffix}@example.com`;
  const alias = `e2e-${suffix}`;
  // CSP violations and hydration errors show up as console errors.
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push(message.text());
    }
  });

  await test.step("register and verify the email address", async () => {
    await page.goto("/register");
    await page.getByLabel(tr.common.email).fill(email);
    await page.getByLabel(tr.common.password).fill(PASSWORD);
    await page.getByRole("button", { name: tr.register.submit }).click();
    await expect(page.getByText(tr.register.sentTitle)).toBeVisible();

    await page.goto(await verificationLink(request, email));
    await expect(page.getByText(tr.verify.done)).toBeVisible();
    await expect(page).not.toHaveURL(/token=/);
  });

  await test.step("sign in", async () => {
    await page.goto("/login");
    await page.getByLabel(tr.common.email).fill(email);
    await page.getByLabel(tr.common.password).fill(PASSWORD);
    await page.getByRole("button", { name: tr.login.submit }).click();
    await expect(page).toHaveURL("/");
    await expect(page.getByText(tr.links.empty)).toBeVisible();
  });

  await test.step("create a link with a custom name", async () => {
    await page.getByLabel(tr.links.targetUrl).fill("https://example.com/e2e");
    await page.getByLabel(tr.links.alias).fill(alias);
    await page.getByRole("button", { name: tr.links.create }).click();
    await expect(page).toHaveURL(/\/links\/[0-9a-f-]{36}\?created=1$/);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(alias);
    expect(await visit(request, alias)).toEqual({ status: 302, location: "https://example.com/e2e" });
  });

  await test.step("see a visit in the statistics", async () => {
    // visit() above identifies as a monitor and is not counted; this one looks like a phone.
    const response = await request.get(`${SHORT_BASE_URL}/${alias}`, {
      maxRedirects: 0,
      headers: { "User-Agent": BROWSER, Referer: "https://www.instagram.com/p/abc" },
    });
    expect(response.status()).toBe(302);
    // Clicks are written in batches every second.
    await expect
      .poll(
        async () => {
          await page.reload();
          return page.getByTestId("total-clicks").textContent();
        },
        { timeout: 10_000 },
      )
      .toBe("1");
    await expect(page.getByText("instagram.com")).toBeVisible();
    await expect(page.getByText(tr.stats.device.MOBILE)).toBeVisible();
  });

  await test.step("download the QR code", async () => {
    const downloading = page.waitForEvent("download");
    await page.getByRole("link", { name: tr.link.downloadPng }).click();
    const download = await downloading;
    expect(download.suggestedFilename()).toBe(`vurli-${alias}.png`);
    const png = await readFile(await download.path());
    expect([...png.subarray(0, 4)]).toEqual([0x89, 0x50, 0x4e, 0x47]);
  });

  await test.step("change the destination", async () => {
    await page.getByLabel(tr.link.targetTitle).fill("https://example.com/e2e-yeni");
    await page.getByRole("button", { name: tr.link.save }).first().click();
    await expect(page.getByText(tr.link.saved)).toBeVisible();
    expect(await visit(request, alias)).toEqual({ status: 302, location: "https://example.com/e2e-yeni" });
  });

  await test.step("see it in the list", async () => {
    await page.getByRole("link", { name: tr.link.back }).click();
    await expect(page.getByRole("link", { name: new RegExp(`/${alias}$`) })).toBeVisible();
    await expect(page.getByText("https://example.com/e2e-yeni")).toBeVisible();
    await page.getByRole("link", { name: new RegExp(`/${alias}$`) }).click();
  });

  await test.step("turn it off, then delete it", async () => {
    await page.getByRole("button", { name: tr.link.disable }).click();
    await expect(page.getByRole("button", { name: tr.link.enable })).toBeVisible();
    expect((await visit(request, alias)).status).toBe(410);

    await page.getByRole("button", { name: tr.link.delete, exact: true }).click();
    await page.getByRole("button", { name: tr.link.confirmDelete }).click();
    await expect(page).toHaveURL("/?deleted=1");
    await expect(page.getByText(tr.links.deleted)).toBeVisible();
    expect((await visit(request, alias)).status).toBe(410);
  });

  expect(consoleErrors).toEqual([]);
});

test("pages need a session", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL("/login");
  await page.goto("/links/0b8f6a3e-4c1d-4e2f-9a7b-1c2d3e4f5a6b");
  await expect(page).toHaveURL("/login");
});
