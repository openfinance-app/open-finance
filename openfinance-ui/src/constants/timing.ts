/**
 * Centralized `setTimeout` delay constants.
 *
 * Previously every delay was an inline numeric literal repeated across many files (e.g. `150` at
 * 5 sites, `3000` at 9 sites), with zero indication of *why* a given value was chosen. Naming them
 * here documents intent and lets a value be tuned in one place.
 */

/** Forces a `window.resize` event after toggling dashboard card visibility so `react-grid-layout`
 * recalculates. */
export const RESIZE_EVENT_DELAY_MS = 50;

/** Default throttle interval for the generic `useThrottle` hook. */
export const DEFAULT_THROTTLE_MS = 100;

/** Fallback delay before running a prefetch calculation when `requestIdleCallback` is unavailable. */
export const PREFETCH_FALLBACK_DELAY_MS = 100;

/** Delay before `scrollIntoView` on a newly-highlighted list row, letting the DOM render first. */
export const ROW_HIGHLIGHT_SCROLL_DELAY_MS = 150;

/** Delay before closing a combobox/dropdown, letting a click/blur race resolve. */
export const DROPDOWN_CLOSE_DELAY_MS = 150;

/** Delay before un-focusing a search input, letting a click on a result register first. */
export const SEARCH_BLUR_DELAY_MS = 200;

/** Delay before triggering the print dialog, letting the print-preview window finish loading. */
export const PRINT_PREVIEW_DELAY_MS = 250;

/** Default debounce interval for the generic `useDebounce` hook. */
export const DEFAULT_DEBOUNCE_MS = 300;

/** Search-input debounce before firing a query. */
export const SEARCH_DEBOUNCE_MS = 300;

/** Delay matching a dismiss/exit CSS animation before removing an element from state. */
export const DISMISS_ANIMATION_DELAY_MS = 300;

/** How long an ARIA live-region announcement element stays in the DOM before cleanup, giving
 * screen readers time to read it. */
export const ARIA_ANNOUNCEMENT_CLEANUP_MS = 1000;

/** How long a "Copied!" clipboard-action indicator stays visible. */
export const COPY_FEEDBACK_RESET_MS = 2000;

/** Idle-prefetch trigger delay after the user stops typing calculator inputs. */
export const PREFETCH_IDLE_DELAY_MS = 2000;

/** How long a settings-page "saved successfully" banner stays visible. */
export const SETTINGS_SUCCESS_MESSAGE_DURATION_MS = 3000;

/** How long the profile-image upload/removal success banner stays visible. */
export const PROFILE_IMAGE_SUCCESS_MESSAGE_DURATION_MS = 4000;

/** How long a message banner needing extra read time stays visible (error banners, or a success
 * banner covering a more involved action). */
export const EXTENDED_MESSAGE_DURATION_MS = 5000;

/** Extended banner duration for security actions whose message needs more read time (e.g.
 * surfacing a recovery/backup code). */
export const SECURITY_EXTENDED_MESSAGE_DURATION_MS = 8000;
