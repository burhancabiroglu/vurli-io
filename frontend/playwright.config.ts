import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against the real stack: a production build of the panel, the Spring
 * backend (`./gradlew bootRun`) and Mailpit. The backend and Mailpit must already run;
 * the panel is started here unless one is already listening.
 */
export default defineConfig({
  testDir: "./e2e",
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: "http://localhost:3000",
    locale: "tr-TR",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run start",
    url: "http://localhost:3000/login",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
