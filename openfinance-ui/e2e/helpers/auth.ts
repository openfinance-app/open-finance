/**
 * E2E Authentication Helpers
 *
 * Reusable helpers for Playwright E2E tests covering login, logout,
 * and registration flows.
 *
 * Task 13.2.5: Add end-to-end tests with Playwright
 */
import type { Page } from '@playwright/test';

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

/**
 * Navigates to /login and fills + submits the login form.
 * Waits until the dashboard URL is reached.
 */
export async function loginAs(
  page: Page,
  credentials: { username: string; password: string; masterPassword: string } = E2E_USER,
): Promise<void> {
  const maxRetries = 3;
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.getByLabel(/username/i).fill(credentials.username);
    await page.getByLabel(/^password$/i).fill(credentials.password);
    await page.locator('#masterPassword').fill(credentials.masterPassword);

    const submitBtn = page.getByRole('button', { name: /sign in|log in/i });
    await submitBtn.click();

    // Race: either we navigate to dashboard/onboarding, or an error message appears
    const result = await Promise.race([
      page.waitForURL(/\/(dashboard|onboarding)/, { timeout: 15_000 }).then(() => 'navigated' as const),
      page.getByTestId('login-error-message').waitFor({ state: 'visible', timeout: 15_000 }).then(() => 'error' as const),
    ]);

    if (result === 'navigated') {
      break; // success
    }

    // Error appeared — check if it's rate limiting
    const errorText = await page.getByTestId('login-error-message').innerText();
    if (/too many requests/i.test(errorText) && attempt < maxRetries - 1) {
      // Extract wait time from error message and wait
      const match = errorText.match(/(\d+)\s*second/);
      const waitSec = match ? parseInt(match[1], 10) : 2;
      await page.waitForTimeout((waitSec + 1) * 1000);
      continue;
    }
    throw new Error(`Login failed with error: ${errorText}`);
  }

  // First-time users land on /onboarding — complete it so tests can proceed
  if (page.url().includes('/onboarding')) {
    const submitBtn = page.getByRole('button', { name: /get started/i });
    await submitBtn.click();
    await page.waitForURL('**/dashboard', { timeout: 15_000 });
  }
}

/**
 * Logs out the currently authenticated user via the user-menu dropdown.
 */
export async function logout(page: Page): Promise<void> {
  // Open user menu — look for avatar / initials button in the sidebar/topbar
  const userMenuButton = page.getByRole('button', { name: /user menu/i })
    .or(page.locator('[aria-label*="user" i], [aria-label*="menu" i], button:has-text("TU"), button:has-text("RU")').first());
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
  user: { username: string; email: string; password: string; masterPassword: string },
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
