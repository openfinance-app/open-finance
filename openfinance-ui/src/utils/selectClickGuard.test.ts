import { describe, it, expect } from 'vitest';
import { markSelectInteraction, wasSelectJustClosed } from './selectClickGuard';

describe('selectClickGuard', () => {
    it('initially returns false', () => {
        expect(wasSelectJustClosed()).toBe(false);
    });

    it('returns true immediately after markSelectInteraction', () => {
        markSelectInteraction();
        expect(wasSelectJustClosed()).toBe(true);
    });

    it('resets to false after requestAnimationFrame', async () => {
        markSelectInteraction();
        expect(wasSelectJustClosed()).toBe(true);

        // Wait for rAF to fire
        await new Promise((resolve) => requestAnimationFrame(resolve));
        // After rAF the flag should be cleared
        expect(wasSelectJustClosed()).toBe(false);
    });
});
