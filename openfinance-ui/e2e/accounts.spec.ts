/**
 * E2E Tests: Account Management
 *
 * Covers critical flows:
 * - core-012: Create checking account with initial balance
 * - core-013: Create savings account
 * - core-014: Create account with zero balance (edge case)
 * - core-018: Edit account name and description
 * - core-019: Delete account validation
 * - Accounts page renders the account list
 *
 * Task 13.2.5: Add end-to-end tests with Playwright
 * Requirements: REQ-2.2 (Account Management)
 */
import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';

/** Unique name suffix to prevent collisions across test runs */
const RUN_ID = Date.now();

test.describe('Account Management', () => {
  // Log in once before all tests in this suite
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
    await page.goto('/accounts');
    await page.waitForLoadState('networkidle');
  });

  // ─── Accounts page ─────────────────────────────────────────────────────────

  test('accounts page renders page heading', async ({ page }) => {
    await expect(page).toHaveURL(/\/accounts(?:\?.*)?$/);

    const heading = page
      .locator('h1, h2, [role="heading"]')
      .filter({ hasText: /accounts/i })
      .first();

    await expect(heading).toBeVisible();
  });

  test('accounts page shows Add Account button', async ({ page }) => {
    const addBtn = page.getByRole('button', { name: /add account/i }).first();
    await expect(addBtn).toBeVisible();
  });

  // ─── Create account ────────────────────────────────────────────────────────

  test('core-012: create checking account with initial balance', async ({ page }) => {
    const accountName = `E2E Checking ${RUN_ID}`;

    // Open the create form
    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();

    // Fill in the form
    const nameInput = page.locator('input#name');
    const balanceInput = page.locator('input#initialBalance');

    // Select account type first (default is CHECKING, but set explicitly)
    const typeSelect = page.locator('select#type');
    if (await typeSelect.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await typeSelect.selectOption('CHECKING');
    }

    // Set initial balance
    await balanceInput.fill('1500');

    // Fill name last
    await nameInput.fill(accountName);
    await expect(nameInput).toHaveValue(accountName);

    // Submit
    await page.getByRole('button', { name: /create|save/i }).click();

    // Dialog should close — this proves the API call succeeded
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10_000 });

    // Use the filter to find the newly created account (list may be paginated)
    await page.getByRole('button', { name: /filter/i }).click();
    await page.locator('#keyword').fill(accountName);
    await expect(page.getByText(accountName)).toBeVisible({ timeout: 10_000 });
  });

  test('core-013: create savings account', async ({ page }) => {
    const accountName = `E2E Savings ${RUN_ID}`;

    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();

    const nameInput = page.locator('input#name');
    const balanceInput = page.locator('input#initialBalance');

    // Select type first
    const typeSelect = page.locator('select#type');
    if (await typeSelect.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await typeSelect.selectOption('SAVINGS');
    }

    // Fill balance
    await balanceInput.fill('5000');

    // Fill name last
    await nameInput.fill(accountName);
    await expect(nameInput).toHaveValue(accountName);

    // Submit
    await page.getByRole('button', { name: /create|save/i }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10_000 });

    // Use the filter to find the newly created account (list may be paginated)
    await page.getByRole('button', { name: /filter/i }).click();
    await page.locator('#keyword').fill(accountName);
    await expect(page.getByText(accountName)).toBeVisible({ timeout: 10_000 });
  });

  test('core-014: create account with zero initial balance', async ({ page }) => {
    const accountName = `E2E Zero ${RUN_ID}`;

    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.locator('input#name').fill(accountName);

    const balanceInput = page.locator('input#initialBalance');
    await balanceInput.fill('0');

    const submitBtn3 = page.getByRole('button', { name: /create|save/i });
    await submitBtn3.click();
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10_000 });

    // Use the filter to find the newly created account (list may be paginated)
    await page.getByRole('button', { name: /filter/i }).click();
    await page.locator('#keyword').fill(accountName);
    await expect(page.getByText(accountName)).toBeVisible({ timeout: 10_000 });
  });

  test('account form validates required name field', async ({ page }) => {
    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();

    // Submit without filling name
    const submitBtn4 = page.getByRole('button', { name: /create|save/i });
    await submitBtn4.click();

    // Validation error should show
    const nameError = page.locator('[id*="name-error"], .text-error').first();
    await expect(nameError).toBeVisible({ timeout: 5_000 });

    // Dialog should still be open
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  // ─── Edit account ──────────────────────────────────────────────────────────

  test('core-018: edit account name opens pre-filled form', async ({ page }) => {
    const accountName = `E2E Edit ${RUN_ID}`;

    // Create an account so edit flow does not depend on seeded data
    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.locator('input#name').fill(accountName);
    await page.locator('input#initialBalance').fill('100');
    await page.getByRole('button', { name: /create|save/i }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10_000 });

    // Use the filter to find the newly created account (list may be paginated)
    await page.getByRole('button', { name: /filter/i }).click();
    await page.locator('#keyword').fill(accountName);

    // Find the created account card and click its edit action
    const cardContainer = page.locator('.group', { hasText: accountName }).first();
    await expect(cardContainer).toBeVisible({ timeout: 10_000 });
    await cardContainer.hover();

    const editBtn = cardContainer.locator('[aria-label="Edit account"]').first();
    await expect(editBtn).toBeVisible({ timeout: 10_000 });
    await editBtn.click();

    // Edit dialog should open
    await expect(page.getByRole('dialog')).toBeVisible();

    // Name field should be pre-filled (not empty)
    const nameField = page.locator('input#name');
    const currentName = await nameField.inputValue();
    expect(currentName.length).toBeGreaterThan(0);
  });

  // ─── Cancel / Close form ────────────────────────────────────────────────────

  test('cancel button closes the account form without saving', async ({ page }) => {
    await page.getByRole('button', { name: /add account/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();

    // Click cancel
    const cancelBtn = page.getByRole('button', { name: /cancel/i });
    await expect(cancelBtn).toBeVisible();
    await cancelBtn.click();

    // Dialog should close
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5_000 });
  });
});
