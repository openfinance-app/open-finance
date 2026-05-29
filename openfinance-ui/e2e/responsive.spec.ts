/**
 * E2E Tests: Responsive Design (Mobile)
 *
 * These tests run ONLY on the "mobile-chrome" Playwright project (Pixel 5 viewport).
 * They verify that the application is usable on a small-screen touch device.
 *
 * Covers critical flows:
 * - core-070: Mobile hamburger menu button is visible on small screens
 * - core-071: Mobile sidebar opens and closes via hamburger / close button
 * - core-072: Mobile sidebar navigation links work correctly
 * - core-073: Touch targets meet WCAG 2.5.5 minimum size (44×44 px)
 * - core-074: Login page is usable on mobile viewport
 * - core-075: Dashboard is scrollable and renders on mobile viewport
 *
 * Task 13.2.5: Add end-to-end tests with Playwright
 * Requirements: REQ-5.1 (Responsive Design), REQ-5.2 (Touch Targets)
 */
import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';

// Pixel 5 dimensions (set by the mobile-chrome Playwright project)
// width: 393, height: 851 — these tests only run via the mobile-chrome project

test.describe('Responsive Design (Mobile)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
    await page.waitForLoadState('networkidle');
  });

  // ─── Hamburger menu ────────────────────────────────────────────────────────

  test('core-070: mobile hamburger menu button is visible', async ({ page }) => {
    // The Sidebar component renders a fixed hamburger button on mobile
    // aria-label is t('openMenu') from the navigation namespace
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await expect(hamburger).toBeVisible({ timeout: 10_000 });
  });

  test('core-070b: hamburger button meets 44px minimum touch target', async ({ page }) => {
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await expect(hamburger).toBeVisible({ timeout: 10_000 });

    const box = await hamburger.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      expect(box.width).toBeGreaterThanOrEqual(44);
      expect(box.height).toBeGreaterThanOrEqual(44);
    }
  });

  // ─── Mobile sidebar open/close ────────────────────────────────────────────

  test('core-071a: clicking hamburger opens the mobile sidebar', async ({ page }) => {
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await expect(hamburger).toBeVisible({ timeout: 10_000 });
    await hamburger.click();

    // The sidebar slides in — it contains the "Open Finance" logo text or nav links
    const sidebar = page.locator('aside').filter({ hasText: /open finance|dashboard/i });
    await expect(sidebar).toBeVisible({ timeout: 5_000 });
  });

  test('core-071b: close button inside mobile sidebar closes it', async ({ page }) => {
    // Open sidebar
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await hamburger.click();

    // Close button inside sidebar has aria-label t('closeMenu')
    const closeBtn = page.getByRole('button', { name: /close menu/i });
    await expect(closeBtn).toBeVisible({ timeout: 5_000 });
    await closeBtn.click();

    // After closing, sidebar should have -translate-x-full (off-screen)
    const sidebar = page.locator('aside');
    await expect(sidebar).toHaveClass(/-translate-x-full/, { timeout: 5_000 });
  });

  test('core-071c: tapping backdrop closes the mobile sidebar', async ({ page }) => {
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await hamburger.click();

    // Wait for sidebar to open
    const closeBtn = page.getByRole('button', { name: /close menu/i });
    await expect(closeBtn).toBeVisible({ timeout: 5_000 });

    // The backdrop div sits behind the sidebar — click on the right side of screen (outside sidebar)
    // Pixel 5 width is 393px; sidebar is 240px wide; click at x=350 to hit backdrop
    await page.mouse.click(350, 400);

    // After closing, sidebar should have -translate-x-full (off-screen)
    const sidebar = page.locator('aside');
    await expect(sidebar).toHaveClass(/-translate-x-full/, { timeout: 5_000 });
  });

  // ─── Mobile sidebar navigation ────────────────────────────────────────────

  test('core-072: clicking a mobile sidebar link navigates to correct page', async ({ page }) => {
    const hamburger = page.getByRole('button', { name: /^open menu$/i });
    await hamburger.click();

    // Click "Accounts" inside the mobile sidebar
    const accountsLink = page.getByRole('link', { name: /^accounts$/i });
    await expect(accountsLink).toBeVisible({ timeout: 5_000 });
    await accountsLink.click();

    // Sidebar should close (onClick calls onClose) and navigation should happen
    await expect(page).toHaveURL(/\/accounts/, { timeout: 10_000 });
  });

  // ─── Mobile viewport rendering ────────────────────────────────────────────

  test('core-074: login page is usable on mobile viewport', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Username, password, and master password fields should be reachable
    await expect(page.getByLabel(/username/i)).toBeVisible();
    await expect(page.getByLabel(/^password$/i)).toBeVisible();
    await expect(page.locator('#masterPassword')).toBeVisible();

    // Submit button should have adequate touch target
    const submitBtn = page.getByRole('button', { name: /sign in|log in/i });
    await expect(submitBtn).toBeVisible();
    const box = await submitBtn.boundingBox();
    if (box) {
      expect(box.height).toBeGreaterThanOrEqual(44);
    }
  });

  test('core-075: dashboard page is scrollable and renders on mobile', async ({ page }) => {
    await expect(page).toHaveURL(/\/dashboard/);

    // The page should have some content (heading)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 20_000 });

    // Scroll down and verify page still works (no JS errors would crash content)
    await page.evaluate(() => window.scrollBy(0, 500));
    await page.waitForTimeout(300);

    // Main content still visible
    await expect(page.getByRole('main')).toBeVisible();
  });

  // ─── Touch target sizes ───────────────────────────────────────────────────

  test('core-073: Add Account button meets 44px touch target on mobile', async ({ page }) => {
    await page.goto('/accounts');
    await page.waitForLoadState('networkidle');

    const addBtn = page.getByRole('button', { name: /add account/i });
    await expect(addBtn).toBeVisible({ timeout: 10_000 });

    const box = await addBtn.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      expect(box.height).toBeGreaterThanOrEqual(44);
    }
  });

  test('core-073b: Add Transaction button meets 44px touch target on transactions page', async ({ page }) => {
    await page.goto('/transactions');
    await page.waitForLoadState('networkidle');

    const addBtn = page.getByRole('button', { name: /add transaction/i }).first();
    await expect(addBtn).toBeVisible({ timeout: 10_000 });

    const box = await addBtn.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      expect(box.height).toBeGreaterThanOrEqual(44);
    }
  });
});
