/**
 * E2E Demo Tour — scripted showcase walkthrough.
 *
 * LOCAL-ONLY: records a video of the main workflows for the wiki.
 * Never runs in CI (CI runs vitest, which excludes e2e/**).
 * READ-ONLY except for net-zero creations, each verified removed afterwards:
 * - the Creation act (account, asset, liability, property, transaction —
 *   "Tour Demo *": create → show in list → delete → verify no match), and
 * - a net-zero payee seed (create + delete "Tour Demo") that populates the
 *   History showcase.
 *
 * DELIVERY NOTES (fix for black-flash video):
 * - All navigation after the initial login uses in-app SPA links/buttons
 *   (sidebar NavLinks, in-page buttons, GlobalSearch). The app shell and
 *   React lazy cache stay mounted, so no Suspense spinner / blank-document
 *   flashes appear between scenes. No page.goto after login, except as a
 *   last-resort fallback for link-less routes (History, Backup).
 * - The dashboard is slow-scrolled on both visits to showcase the
 *   Financial Map, Cash Flow / Sankey and Finance News sections.
 */
import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { signIn } from './helpers/auth';

test.use({ viewport: { width: 1440, height: 900 }, video: 'on' });

const DEMO = { username: 'demo', password: 'demo123', masterPassword: '' };
const BEAT = 2000; // pause per beat so viewers can follow the video
const SECTION_PAUSE = 1500; // pause on each dashboard showcase section
const TOUR_PAYEE = 'Tour Demo';
const TOUR_ACCOUNT = 'Tour Demo Checking';
const TOUR_ASSET = 'Tour Demo Asset';
const TOUR_LIABILITY = 'Tour Demo Loan';
const TOUR_PROPERTY = 'Tour Demo Studio';
const TOUR_TRANSACTION = 'Tour Demo Coffee';

/**
 * Click an SPA sidebar link (no full-page reload) and wait for the page.
 * Keeps the app shell mounted so no black fallback flashes between scenes.
 */
async function goLink(page: Page, name: RegExp, url: RegExp, beats = 1) {
  const link = page.getByRole('link', { name });
  await link.first().waitFor({ state: 'visible', timeout: 15_000 });
  await link.first().click();
  await page.waitForURL(url, { timeout: 15_000 });
  await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(BEAT * beats);
}

/** Expand a collapsible sidebar group (e.g. Transactions, Tools). No-op if already open. */
async function expandGroup(page: Page, group: RegExp) {
  const expandBtn = page.getByRole('button', { name: group });
  if (await expandBtn.first().isVisible({ timeout: 3000 }).catch(() => false)) {
    await expandBtn.first().click();
    await page.waitForTimeout(500);
  }
}

/** Expand the parent group if needed, then navigate via its child sidebar link. */
async function goChildLink(
  page: Page,
  parent: RegExp,
  name: RegExp,
  url: RegExp,
  beats = 1
) {
  const link = page.getByRole('link', { name });
  if (await link.first().isVisible({ timeout: 3000 }).catch(() => false)) {
    await goLink(page, name, url, beats);
    return;
  }
  await expandGroup(page, parent);
  await goLink(page, name, url, beats);
}

/**
 * SPA navigation for routes with no sidebar link (History while hidden,
 * Backup). Drives the router's history directly — no document reload, so the
 * app shell and lazy chunk cache stay mounted. Falls back to goto only if
 * the router ignores the popstate event.
 */
async function spaGo(page: Page, url: string, beats = 1) {
  await page.evaluate(target => {
    window.history.pushState({}, '', target);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, url);
  try {
    await page.waitForURL(u => u.pathname === url, { timeout: 5000 });
  } catch {
    await page.goto(url);
  }
  await page.waitForLoadState('networkidle');
  await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(BEAT * beats);
}

/**
 * Slow-scroll showcase of the dashboard's below-the-fold sections.
 * Pauses on Financial Map, Cash Flow / Sankey and Finance News, then
 * returns to the top so the next scene (and the poster) starts clean.
 */
async function showcaseDashboard(page: Page) {
  await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(800);
  for (const section of [/financial map/i, /cash flow/i, /finance news/i]) {
    const target = page.getByText(section).first();
    const visible = await target.isVisible({ timeout: 3000 }).catch(() => false);
    if (visible) {
      await target.scrollIntoViewIfNeeded();
      await page.waitForTimeout(SECTION_PAUSE);
    } else {
      // Section not rendered yet (lazy card) — wheel down and retry once.
      await page.mouse.wheel(0, 700);
      await page.waitForTimeout(1000);
      if (await target.isVisible({ timeout: 2000 }).catch(() => false)) {
        await target.scrollIntoViewIfNeeded();
        await page.waitForTimeout(SECTION_PAUSE);
      }
    }
  }
  // NOTE: the scroll container is <main> (overflow-y-auto), not the window.
  await page.getByRole('main').evaluate(el => el.scrollTo(0, 0));
  await page.waitForTimeout(800);
}

/** Delete the "Tour Demo" payee via its card button + confirm dialog. */
async function deleteTourPayee(page: Page) {
  const delBtn = page.getByRole('button', { name: new RegExp(`delete ${TOUR_PAYEE}`, 'i') });
  await delBtn.first().waitFor({ state: 'visible', timeout: 15_000 });
  await delBtn.first().scrollIntoViewIfNeeded();
  await delBtn.first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByRole('button', { name: /^delete$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);
}

/**
 * Seed History with a net-zero payee create + delete ("Tour Demo", no
 * category). Verifies the payee is gone afterwards (search shows no match).
 * Throws a BLOCKED error instead of leaving junk data behind.
 */
async function seedHistoryNetZero(page: Page) {
  await goChildLink(page, /expand.*transactions/i, /^payees$/i, /\/payees/);

  const search = page.getByPlaceholder(/search payees/i);
  await expect(search).toBeVisible({ timeout: 15_000 });

  // Idempotency: clear a leftover from an interrupted run first (still net-zero).
  await search.fill(TOUR_PAYEE);
  await page.waitForTimeout(1000);
  if (
    await page.getByText(TOUR_PAYEE, { exact: true }).first().isVisible({ timeout: 3000 }).catch(() => false)
  ) {
    await deleteTourPayee(page);
  }
  await search.fill('');
  await page.waitForTimeout(1000);

  // Create "Tour Demo" (category is optional — none needed for the tour).
  await page.getByRole('button', { name: /^add payee$/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByPlaceholder(/e\.g\., amazon/i).fill(TOUR_PAYEE);
  await dialog.getByRole('button', { name: /^create$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(page.getByText(TOUR_PAYEE, { exact: true }).first()).toBeVisible({
    timeout: 15_000,
  });
  await page.waitForTimeout(BEAT);

  // Delete it again — the operation itself stays in History.
  await deleteTourPayee(page);

  // Verify net-zero: search must show no match and no row may remain.
  await search.fill(TOUR_PAYEE);
  await page.waitForTimeout(1000);
  await expect(page.getByText(/no payees match/i)).toBeVisible({ timeout: 15_000 });
  if (
    await page.getByText(TOUR_PAYEE, { exact: true }).first().isVisible({ timeout: 3000 }).catch(() => false)
  ) {
    throw new Error(
      'BLOCKED: Tour Demo payee could not be deleted — refusing to leave junk data'
    );
  }
  await search.fill('');
  await page.waitForTimeout(1000);
}

/**
 * Locator plan for one net-zero creation target: how to filter the page's
 * list to the entity, which button deletes it, how to confirm, and which
 * empty-state copy proves zero matches afterwards.
 */
interface EntityTarget {
  filterSelector: string; // keyword input inside the page's filter panel
  deleteAria: RegExp; // aria-label of the filtered card/row delete button
  confirm: RegExp; // confirm button name in the delete dialog
  noMatch: RegExp; // empty-state copy shown when the filter matches nothing
  textExact?: boolean; // false when the name element also renders extra text
}

/** Locator for the entity's visible name in the filtered list. */
function nameText(page: Page, target: EntityTarget, name: string) {
  return page.getByText(name, { exact: target.textExact !== false });
}

const ACCOUNT_TARGET: EntityTarget = {
  filterSelector: '#keyword',
  deleteAria: /^delete account permanently$/i,
  confirm: /^delete permanently$/i,
  noMatch: /no accounts match/i,
};
const ASSET_TARGET: EntityTarget = {
  filterSelector: '#keyword',
  deleteAria: /^delete asset$/i,
  confirm: /^delete$/i,
  noMatch: /no assets match/i,
};
const LIABILITY_TARGET: EntityTarget = {
  filterSelector: '#search',
  deleteAria: /^delete liability$/i,
  confirm: /^delete$/i,
  noMatch: /no liabilities match/i,
  textExact: false, // card <h3> may append a funding-status badge after the name
};
const PROPERTY_TARGET: EntityTarget = {
  filterSelector: '#keyword',
  deleteAria: /^delete property$/i,
  confirm: /^delete$/i,
  noMatch: /no properties match/i,
};
const TRANSACTION_TARGET: EntityTarget = {
  filterSelector: '#keyword',
  deleteAria: /^delete transaction$/i,
  confirm: /^delete$/i,
  noMatch: /no transactions match/i,
};

/** Open the page's filter panel (no-op if the keyword input is already visible). */
async function openFilterPanel(page: Page, target: EntityTarget) {
  const input = page.locator(target.filterSelector);
  if (await input.isVisible({ timeout: 2000 }).catch(() => false)) return;
  await page.getByRole('button', { name: /^filters$/i }).first().click();
  await expect(input).toBeVisible({ timeout: 10_000 });
}

/** Filter the list to `name` and wait for the (debounced) search to settle. */
async function filterByName(page: Page, target: EntityTarget, name: string) {
  await openFilterPanel(page, target);
  await page.locator(target.filterSelector).fill(name);
  await page.waitForTimeout(1000);
}

/** Clear the keyword filter so the full list is visible again. */
async function clearFilter(page: Page, target: EntityTarget) {
  await page.locator(target.filterSelector).fill('');
  await page.waitForTimeout(1000);
}

/**
 * Delete the currently filtered entity via its card/row delete button plus
 * the confirmation dialog. Assumes the list is already filtered to its match.
 */
async function deleteFilteredEntity(page: Page, target: EntityTarget, name: string) {
  // Hover first so hover-revealed action buttons fade in for the recording.
  await nameText(page, target, name).first().hover();
  const delBtn = page.getByRole('button', { name: target.deleteAria });
  await delBtn.first().waitFor({ state: 'visible', timeout: 15_000 });
  await delBtn.first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByRole('button', { name: target.confirm }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);
}

/** Remove a leftover from an interrupted run first (idempotency, still net-zero). */
async function ensureNoLeftover(page: Page, target: EntityTarget, name: string) {
  await filterByName(page, target, name);
  if (await nameText(page, target, name).first().isVisible({ timeout: 3000 }).catch(() => false)) {
    await deleteFilteredEntity(page, target, name);
  }
  await clearFilter(page, target);
}

/** Filter to the freshly created entity and pause on it for the recording. */
async function showCreatedEntity(page: Page, target: EntityTarget, name: string) {
  await filterByName(page, target, name);
  await expect(nameText(page, target, name).first()).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(BEAT);
}

/**
 * Assert the entity is gone (filtered list shows its no-match empty state).
 * Throws a BLOCKED error instead of leaving junk data behind.
 */
async function verifyEntityGone(page: Page, target: EntityTarget, name: string) {
  await filterByName(page, target, name);
  await expect(page.getByText(target.noMatch)).toBeVisible({ timeout: 15_000 });
  if (await nameText(page, target, name).first().isVisible({ timeout: 3000 }).catch(() => false)) {
    throw new Error(`BLOCKED: "${name}" could not be deleted — refusing to leave junk data`);
  }
  await page.waitForTimeout(SECTION_PAUSE); // let viewers register the empty state
  await clearFilter(page, target);
}

/** ACCOUNT: create "Tour Demo Checking" (CHECKING, base currency) → show → delete. */
async function createAccountNetZero(page: Page) {
  await goLink(page, /^accounts$/i, /\/accounts/);
  await ensureNoLeftover(page, ACCOUNT_TARGET, TOUR_ACCOUNT);

  await page.getByRole('button', { name: /^add account$/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.locator('select#type').selectOption('CHECKING');
  await dialog.locator('input#initialBalance').fill('250');
  await dialog.locator('input#name').fill(TOUR_ACCOUNT);
  await page.waitForTimeout(1000); // let viewers read the filled form
  await dialog.getByRole('button', { name: /^create account$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);

  await showCreatedEntity(page, ACCOUNT_TARGET, TOUR_ACCOUNT);
  await deleteFilteredEntity(page, ACCOUNT_TARGET, TOUR_ACCOUNT);
  await verifyEntityGone(page, ACCOUNT_TARGET, TOUR_ACCOUNT);
}

/** ASSET: create a simple stock "Tour Demo Asset" → show → delete. */
async function createAssetNetZero(page: Page) {
  await goLink(page, /^assets$/i, /\/assets/);
  await ensureNoLeftover(page, ASSET_TARGET, TOUR_ASSET);

  await page.getByRole('button', { name: /^add asset$/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.locator('select#type').selectOption('STOCK');
  await dialog.locator('input#name').fill(TOUR_ASSET);
  await dialog.locator('input#quantity').fill('10');
  await dialog.locator('input#purchasePrice').fill('100');
  await dialog.locator('input#currentPrice').fill('110');
  await page.waitForTimeout(1000);
  await dialog.getByRole('button', { name: /^create asset$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);

  await showCreatedEntity(page, ASSET_TARGET, TOUR_ASSET);
  await deleteFilteredEntity(page, ASSET_TARGET, TOUR_ASSET);
  await verifyEntityGone(page, ASSET_TARGET, TOUR_ASSET);
}

/** LIABILITY: create a small loan "Tour Demo Loan" → show → delete. */
async function createLiabilityNetZero(page: Page) {
  await goLink(page, /^liabilities$/i, /\/liabilities/);
  await ensureNoLeftover(page, LIABILITY_TARGET, TOUR_LIABILITY);

  await page.getByRole('button', { name: /^add liability$/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.locator('select#type').selectOption('LOAN');
  await dialog.locator('input#name').fill(TOUR_LIABILITY);
  await dialog.locator('input#principal').fill('500');
  await dialog.locator('input#currentBalance').fill('500');
  await page.waitForTimeout(1000);
  await dialog.getByRole('button', { name: /^create liability$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);

  await showCreatedEntity(page, LIABILITY_TARGET, TOUR_LIABILITY);
  await deleteFilteredEntity(page, LIABILITY_TARGET, TOUR_LIABILITY);
  await verifyEntityGone(page, LIABILITY_TARGET, TOUR_LIABILITY);
}

/** REAL ESTATE: create property "Tour Demo Studio" → show → delete. */
async function createPropertyNetZero(page: Page) {
  await goLink(page, /^real estate$/i, /\/real-estate$/);
  await ensureNoLeftover(page, PROPERTY_TARGET, TOUR_PROPERTY);

  await page.getByRole('button', { name: /^add property$/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.locator('input#name').fill(TOUR_PROPERTY);
  await dialog.locator('input#address').fill('1 Demo Street');
  await dialog.locator('input#purchasePrice').fill('75000');
  await dialog.locator('input#currentValue').fill('82000');
  await page.waitForTimeout(1000);
  await dialog.getByRole('button', { name: /^create property$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);

  await showCreatedEntity(page, PROPERTY_TARGET, TOUR_PROPERTY);
  await deleteFilteredEntity(page, PROPERTY_TARGET, TOUR_PROPERTY);
  await verifyEntityGone(page, PROPERTY_TARGET, TOUR_PROPERTY);
}

/** TRANSACTION: create a small expense "Tour Demo Coffee" → show → delete. */
async function createTransactionNetZero(page: Page) {
  await goLink(page, /transactions/i, /\/transactions/);
  await ensureNoLeftover(page, TRANSACTION_TARGET, TOUR_TRANSACTION);

  await page
    .getByRole('button', { name: /add transaction/i })
    .first()
    .click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  // Type defaults to EXPENSE. Pick the first real account (Radix combobox).
  await dialog.locator('button[role="combobox"]').first().click();
  const accountOption = page.locator('[role="listbox"] [role="option"]').first();
  await accountOption.waitFor({ state: 'visible', timeout: 10_000 });
  await accountOption.click();
  await dialog.locator('input#amount').fill('4.5');
  // Date defaults to today; description carries the distinctive tour name.
  await dialog.locator('input#description').fill(TOUR_TRANSACTION);
  await page.waitForTimeout(1000);
  await dialog.getByRole('button', { name: /^create transaction$/i }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await page.waitForTimeout(1000);

  await showCreatedEntity(page, TRANSACTION_TARGET, TOUR_TRANSACTION);
  await deleteFilteredEntity(page, TRANSACTION_TARGET, TOUR_TRANSACTION);
  await verifyEntityGone(page, TRANSACTION_TARGET, TOUR_TRANSACTION);
}

/**
 * Final integrity sweep: revisit every creation page and prove that filtering
 * for "Tour Demo" matches nothing — demo data is exactly as the tour found it.
 */
async function finalNetZeroSweep(page: Page) {
  const pages: Array<{ nav: RegExp; url: RegExp; heading: RegExp; target: EntityTarget }> = [
    { nav: /^accounts$/i, url: /\/accounts/, heading: /^accounts$/i, target: ACCOUNT_TARGET },
    {
      nav: /^assets$/i,
      url: /\/assets/,
      heading: /investment assets/i,
      target: ASSET_TARGET,
    },
    {
      nav: /^liabilities$/i,
      url: /\/liabilities/,
      heading: /^liabilities$/i,
      target: LIABILITY_TARGET,
    },
    {
      nav: /^real estate$/i,
      url: /\/real-estate$/,
      heading: /real estate portfolio/i,
      target: PROPERTY_TARGET,
    },
    {
      nav: /transactions/i,
      url: /\/transactions/,
      heading: /^transactions$/i,
      target: TRANSACTION_TARGET,
    },
  ];
  for (const p of pages) {
    await goLink(page, p.nav, p.url);
    // Wait for THIS page's heading: the previous lazy route stays mounted
    // during the SPA transition, so a bare `main` check can match stale DOM.
    await expect(page.getByRole('heading', { name: p.heading }).first()).toBeVisible({
      timeout: 15_000,
    });
    await filterByName(page, p.target, 'Tour Demo');
    await expect(page.getByText(p.target.noMatch)).toBeVisible({ timeout: 15_000 });
    await clearFilter(page, p.target);
  }
}

test.describe('Demo tour', () => {
  test.skip(!!process.env.CI, 'Demo tour is recorded locally only; browser-ci has no demo user.');
  test('demo-tour: full showcase walkthrough (net-zero creations)', async ({ page }) => {
    test.setTimeout(480_000);
    await signIn(page, DEMO);
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
    await page.waitForTimeout(BEAT);
    await showcaseDashboard(page);

    // ── Act 1: daily money flow ──
    // NOTE: "Transactions" is a parent link whose accessible name includes the
    // expand/collapse button text — match it unanchored.
    await goLink(page, /transactions/i, /\/transactions/);
    const filterBtn = page.getByRole('button', { name: /^filters$/i });
    if (await filterBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await filterBtn.click();
    }
    const searchInput = page.getByPlaceholder(/search|keyword/i).first();
    if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await searchInput.fill('salary');
      await page.waitForTimeout(BEAT);
      await searchInput.fill('');
      await page.waitForTimeout(1000);
    }
    const addTx = page.getByRole('button', { name: /add transaction/i }).first();
    if (await addTx.isVisible({ timeout: 3000 }).catch(() => false)) {
      await addTx.click();
      await page.waitForTimeout(BEAT);
      await page.keyboard.press('Escape'); // read-only: never submit
      await page.waitForTimeout(1000);
    }
    await goChildLink(page, /expand.*transactions/i, /^rules$/i, /\/transaction-rules/);
    await goLink(page, /^budgets$/i, /\/budget/);
    await goLink(page, /^import$/i, /\/import/);

    // ── Act 2: creation workflows (net-zero: create → show → delete → verify) ──
    await createAccountNetZero(page);
    await createAssetNetZero(page);
    await createLiabilityNetZero(page);
    await createPropertyNetZero(page);
    await createTransactionNetZero(page);

    // ── Act 3: wealth overview ──
    await goLink(page, /^accounts$/i, /\/accounts/);
    await goLink(page, /^assets$/i, /\/assets/);
    await goLink(page, /^liabilities$/i, /\/liabilities/);
    await goLink(page, /^real estate$/i, /\/real-estate$/);
    // Real-estate tools hub via the in-page Tools button (SPA, no reload).
    const reToolsBtn = page.getByRole('main').getByRole('button', { name: /^tools$/i });
    if (await reToolsBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await reToolsBtn.click();
      await page.waitForURL(/\/real-estate\/tools/, { timeout: 15_000 });
      await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 15_000 });
      await page.waitForTimeout(BEAT);
    }
    await goChildLink(page, /expand.*tools/i, /^loan calculator$/i, /\/tools\/loan-calculator/);

    // ── Act 4: intelligence finale ──
    // Search via the top-bar GlobalSearch (SPA navigation, no reload).
    const globalSearch = page.getByPlaceholder(/search accounts/i);
    await expect(globalSearch).toBeVisible({ timeout: 10_000 });
    await globalSearch.fill('salary');
    await page.keyboard.press('Enter');
    await page.waitForURL(/\/search/, { timeout: 15_000 });
    await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 15_000 });
    await page.waitForTimeout(BEAT);
    await page.getByRole('textbox').first().fill('salary');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(BEAT);

    // Seed History net-zero, then showcase the recorded operation.
    await seedHistoryNetZero(page);
    await spaGo(page, '/history');
    await expect(page.getByText(TOUR_PAYEE, { exact: true }).first()).toBeVisible({
      timeout: 15_000,
    });
    await page.waitForTimeout(BEAT);
    await spaGo(page, '/backup');

    // Finale: dashboard showcase, AI widget, poster.
    await goLink(page, /^dashboard$/i, /\/dashboard/);
    await showcaseDashboard(page);
    const aiOpen = page.getByRole('button', { name: /open ai assistant/i });
    if (await aiOpen.isVisible({ timeout: 3000 }).catch(() => false)) {
      await aiOpen.click();
      await page.waitForTimeout(BEAT);
      const aiClose = page
        .getByRole('dialog')
        .getByRole('button', { name: /minimize|close chat/i })
        .first();
      if (await aiClose.isVisible({ timeout: 3000 }).catch(() => false)) await aiClose.click();
      await page.waitForTimeout(1000);
    }
    // Prove every "Tour Demo" creation is gone, then close on the dashboard.
    await finalNetZeroSweep(page);
    await goLink(page, /^dashboard$/i, /\/dashboard/);
    await expect(page.getByRole('heading', { name: /dashboard/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    // Closing frame + poster for the wiki
    await page.waitForTimeout(BEAT);
    await page.screenshot({ path: '../docs/wiki/demo/demo-tour-poster.jpg' });
  });
});
