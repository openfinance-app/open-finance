/**
 * E2E Authentication Helpers
 *
 * Reusable helpers for Playwright E2E tests covering login, logout,
 * and registration flows.
 *
 * Task 13.2.5: Add end-to-end tests with Playwright
 */
import type { APIRequestContext, APIResponse, Page } from '@playwright/test';

/** Credentials for the pre-existing E2E test user */
export const E2E_USER = {
  username: 'real_test_user',
  password: 'Password123!',
  masterPassword: 'RealMaster123!',
} as const;

/** Credentials for a fresh registration test user */
export const E2E_REGISTER_USER = {
  username: `e2e_reg_${Date.now()}`,
  email: `e2e_reg_${Date.now()}@test.local`,
  password: 'E2eTest123!',
  masterPassword: 'E2eMaster123!',
} as const;

/** Provision synthetic users while honoring the production authentication rate limit. */
export async function registerWithRetry(
  request: APIRequestContext,
  data: Record<string, unknown>
): Promise<APIResponse> {
  for (let attempt = 0; ; attempt++) {
    const response = await request.post('/api/v1/auth/register', { data });
    if (response.status() !== 429 || attempt === 4) return response;
    await new Promise(resolve =>
      setTimeout(resolve, retryDelay(response.headers()['retry-after']))
    );
  }
}

function retryDelay(header: string | undefined): number {
  const seconds = Number(header ?? '6');
  return (Number.isFinite(seconds) ? Math.max(1, seconds) : 6) * 1000 + 250;
}

/** Sign in through the real form, retaining the onboarding screen for new users. */
export async function signIn(
  page: Page,
  credentials: { username: string; password: string; masterPassword: string }
): Promise<void> {
  for (let attempt = 0; attempt < 5; attempt++) {
    await page.goto('/login');
    await page.getByLabel(/username/i).fill(credentials.username);
    await page.getByLabel(/^password$/i).fill(credentials.password);
    const masterPassword = page.locator('#masterPassword');
    if (await masterPassword.isVisible()) await masterPassword.fill(credentials.masterPassword);
    const responsePromise = page.waitForResponse(
      response =>
        response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    const response = await responsePromise;
    if (response.ok()) {
      await page.waitForURL(/\/(dashboard|onboarding)/);
      return;
    }
    if (response.status() !== 429 || attempt === 4) {
      throw new Error(`Login failed: HTTP ${response.status()}`);
    }
    await page.waitForTimeout(retryDelay(response.headers()['retry-after']));
  }
}

/** Sign in and complete first-use onboarding when a test needs the dashboard. */
export async function loginAs(
  page: Page,
  credentials: { username: string; password: string; masterPassword: string } = E2E_USER
): Promise<void> {
  await signIn(page, credentials);
  if (page.url().includes('/onboarding')) {
    await page.getByRole('button', { name: /get started/i }).click();
    await page.waitForURL('**/dashboard');
  }
}

/**
 * Logs out the currently authenticated user via the user-menu dropdown.
 */
export async function logout(page: Page): Promise<void> {
  // Open user menu — look for avatar / initials button in the sidebar/topbar
  const userMenuButton = page
    .getByRole('button', { name: /user menu/i })
    .or(
      page
        .locator(
          '[aria-label*="user" i], [aria-label*="menu" i], button:has-text("TU"), button:has-text("RU")'
        )
        .first()
    );
  await userMenuButton.click();

  // Click the "Logout" or "Sign out" menu item
  await page.getByRole('menuitem', { name: /log ?out|sign ?out/i }).click();
  await page.waitForURL('**/login', { timeout: 10_000 });
}

/**
 * Registers a new user and waits for redirect to /login.
 */
export async function registerUser(
  page: Page,
  user: { username: string; email: string; password: string; masterPassword: string }
): Promise<void> {
  await page.goto('/register');
  await page.waitForLoadState('networkidle');

  await page.getByLabel(/username/i).fill(user.username);
  await page.getByLabel(/email/i).fill(user.email);

  // Password fields — target by id to avoid matching toggle buttons
  await page.locator('#password').fill(user.password);
  await page.locator('#confirmPassword').fill(user.password);
  await page.locator('#masterPassword').fill(user.masterPassword);
  await page.locator('#confirmMasterPassword').fill(user.masterPassword);

  await page.getByRole('button', { name: /create account|register|sign up/i }).click();
  // After successful registration navigate to /login
  await page.waitForURL('**/login', { timeout: 15_000 });
}
