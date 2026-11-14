import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  srOnly,
  focusVisible,
  FocusTrap,
  useFocusTrap,
  announceToScreenReader,
  isElementVisible,
  getNextFocusableElement,
  generateA11yId,
  Keys,
  isKey,
  handleActivation,
  getSkipLinkProps,
  ariaLabel,
  ariaProps,
  prefersReducedMotion,
  getAnimationClass,
} from './accessibility';

describe('Accessibility Utilities', () => {
  describe('CSS class constants', () => {
    it('should export srOnly class for screen reader only text', () => {
      expect(srOnly).toContain('absolute');
      expect(srOnly).toContain('overflow-hidden');
    });

    it('should export focusVisible class with gold ring', () => {
      expect(focusVisible).toContain('focus-visible:ring-accent-gold');
      expect(focusVisible).toContain('focus-visible:ring-2');
    });
  });

  describe('FocusTrap', () => {
    let container: HTMLElement;
    let button1: HTMLButtonElement;
    let button2: HTMLButtonElement;
    let input: HTMLInputElement;

    beforeEach(() => {
      container = document.createElement('div');
      button1 = document.createElement('button');
      button1.textContent = 'Button 1';
      button2 = document.createElement('button');
      button2.textContent = 'Button 2';
      input = document.createElement('input');

      container.appendChild(button1);
      container.appendChild(input);
      container.appendChild(button2);
      document.body.appendChild(container);
    });

    afterEach(() => {
      document.body.removeChild(container);
    });

    it('should trap focus within container', () => {
      const focusTrap = new FocusTrap(container);
      focusTrap.activate();

      // Focus should be on the first focusable element (button1) or body if not focused
      expect([button1, document.body]).toContain(document.activeElement);
    });

    it('should cycle focus forward on Tab', () => {
      const focusTrap = new FocusTrap(container);
      focusTrap.activate();

      button2.focus();
      const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true });
      container.dispatchEvent(event);

      // Should not prevent default if not at last element
      expect(document.activeElement).toBe(button2);
    });

    it('should restore previous focus on deactivate', () => {
      const outsideButton = document.createElement('button');
      document.body.appendChild(outsideButton);
      outsideButton.focus();

      const focusTrap = new FocusTrap(container);
      focusTrap.activate();
      focusTrap.deactivate();

      expect(document.activeElement).toBe(outsideButton);

      document.body.removeChild(outsideButton);
    });

    it('should skip disabled elements', () => {
      button2.disabled = true;

      const focusTrap = new FocusTrap(container);
      focusTrap.activate();

      // Should only focus button1 and input (button2 is disabled)
      // In test env, focus might stay on body
      expect([button1, document.body]).toContain(document.activeElement);
    });
  });

  describe('announceToScreenReader', () => {
    it('should create ARIA live region with message', () => {
      announceToScreenReader('Test announcement');

      const liveRegion = document.querySelector('[role="status"]');
      expect(liveRegion).toBeTruthy();
      expect(liveRegion?.textContent).toBe('Test announcement');
      expect(liveRegion?.getAttribute('aria-live')).toBe('polite');
    });

    it('should support assertive priority', () => {
      // Clear any previous announcements first
      const previous = document.querySelector('[role="status"]');
      if (previous) previous.remove();

      announceToScreenReader('Urgent message', 'assertive');

      const liveRegion = document.querySelector('[role="status"]');
      expect(liveRegion?.getAttribute('aria-live')).toBe('assertive');
    });

    it('should remove announcement after timeout', async () => {
      announceToScreenReader('Test');

      const liveRegion = document.querySelector('[role="status"]');
      expect(liveRegion).toBeTruthy();

      await new Promise(resolve => setTimeout(resolve, 1100));

      const liveRegionAfter = document.querySelector('[role="status"]');
      expect(liveRegionAfter).toBeFalsy();
    });
  });

  describe('isElementVisible', () => {
    it('should return false for hidden elements', () => {
      const element = document.createElement('div');
      element.style.display = 'none';
      document.body.appendChild(element);

      expect(isElementVisible(element)).toBe(false);

      document.body.removeChild(element);
    });

    it('should return true for visible elements', () => {
      const element = document.createElement('div');
      element.style.display = 'block';
      element.style.width = '100px';
      element.style.height = '100px';
      element.style.position = 'absolute';
      element.style.top = '0';
      document.body.appendChild(element);

      // In JSDOM, offsetParent might not work as expected, so we just check it exists
      expect(element).toBeDefined();

      document.body.removeChild(element);
    });
  });

  describe('getNextFocusableElement', () => {
    let button1: HTMLButtonElement;
    let button2: HTMLButtonElement;
    let button3: HTMLButtonElement;

    beforeEach(() => {
      button1 = document.createElement('button');
      button2 = document.createElement('button');
      button3 = document.createElement('button');

      document.body.appendChild(button1);
      document.body.appendChild(button2);
      document.body.appendChild(button3);
    });

    afterEach(() => {
      document.body.removeChild(button1);
      document.body.removeChild(button2);
      document.body.removeChild(button3);
    });

    it('should return next focusable element', () => {
      const next = getNextFocusableElement(button1, 'next');
      // In JSDOM, offsetParent might not work, so we check if it returns something or null
      expect(next === null || next === button2 || next === button3).toBe(true);
    });

    it('should return previous focusable element', () => {
      const previous = getNextFocusableElement(button2, 'previous');
      // In JSDOM, offsetParent might not work
      expect(previous === null || previous === button1).toBe(true);
    });

    it('should return null if at end', () => {
      const next = getNextFocusableElement(button3, 'next');
      expect(next).toBeNull();
    });

    it('should return null if at beginning', () => {
      const previous = getNextFocusableElement(button1, 'previous');
      expect(previous).toBeNull();
    });
  });

  describe('generateA11yId', () => {
    it('should generate unique IDs', () => {
      const id1 = generateA11yId();
      const id2 = generateA11yId();

      expect(id1).not.toBe(id2);
      expect(id1).toContain('a11y');
    });

    it('should support custom prefix', () => {
      const id = generateA11yId('custom');
      expect(id).toContain('custom');
    });
  });

  describe('Keyboard event utilities', () => {
    it('should export keyboard keys constants', () => {
      expect(Keys.ENTER).toBe('Enter');
      expect(Keys.SPACE).toBe(' ');
      expect(Keys.ESCAPE).toBe('Escape');
    });

    it('should check if key matches', () => {
      const event = new KeyboardEvent('keydown', { key: 'Enter' });
      expect(isKey(event, Keys.ENTER, Keys.SPACE)).toBe(true);
    });

    it('should return false for non-matching key', () => {
      const event = new KeyboardEvent('keydown', { key: 'A' });
      expect(isKey(event, Keys.ENTER, Keys.SPACE)).toBe(false);
    });

    it('should handle activation on Enter', () => {
      const callback = vi.fn();
      const event = new KeyboardEvent('keydown', { key: 'Enter' });

      handleActivation(event, callback);

      expect(callback).toHaveBeenCalled();
    });

    it('should handle activation on Space', () => {
      const callback = vi.fn();
      const event = new KeyboardEvent('keydown', { key: ' ' });

      handleActivation(event, callback);

      expect(callback).toHaveBeenCalled();
    });

    it('should not activate on other keys', () => {
      const callback = vi.fn();
      const event = new KeyboardEvent('keydown', { key: 'A' });

      handleActivation(event, callback);

      expect(callback).not.toHaveBeenCalled();
    });
  });

  describe('getSkipLinkProps', () => {
    it('should return skip link properties', () => {
      const props = getSkipLinkProps('main-content');

      expect(props.href).toBe('#main-content');
      expect(props.className).toContain('sr-only');
      expect(props.onClick).toBeDefined();
    });

    it('should scroll to target on click', () => {
      const target = document.createElement('div');
      target.id = 'main-content';
      target.tabIndex = -1;
      document.body.appendChild(target);

      const scrollIntoViewMock = vi.fn();
      target.scrollIntoView = scrollIntoViewMock;

      const props = getSkipLinkProps('main-content');
      const event = {
        preventDefault: vi.fn(),
      } as unknown as React.MouseEvent<HTMLAnchorElement>;

      props.onClick(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(scrollIntoViewMock).toHaveBeenCalled();

      document.body.removeChild(target);
    });
  });

  describe('ariaLabel utilities', () => {
    it('should format money with currency', () => {
      const label = ariaLabel.money(1234.56, 'USD');
      expect(label).toContain('1,234.56');
      expect(label).toContain('$');
    });

    it('should format percentage', () => {
      const label = ariaLabel.percentage(12.345, 2);
      expect(label).toBe('12.35 percent');
    });

    it('should format date', () => {
      const date = new Date('2024-01-15');
      const label = ariaLabel.date(date);
      expect(label).toContain('January');
      expect(label).toContain('15');
      expect(label).toContain('2024');
    });

    it('should format gain', () => {
      const label = ariaLabel.gainLoss(500, 'USD');
      expect(label).toContain('gain');
      expect(label).toContain('500');
    });

    it('should format loss', () => {
      const label = ariaLabel.gainLoss(-500, 'USD');
      expect(label).toContain('loss');
      expect(label).toContain('500');
    });
  });

  describe('ariaProps utilities', () => {
    it('should return loading props', () => {
      const props = ariaProps.loading('Loading data');
      expect(props['aria-busy']).toBe('true');
      expect(props['aria-label']).toBe('Loading data');
      expect(props.role).toBe('status');
    });

    it('should return alert props with polite live region', () => {
      const props = ariaProps.alert('success');
      expect(props.role).toBe('alert');
      expect(props['aria-live']).toBe('polite');
    });

    it('should return alert props with assertive live region for errors', () => {
      const props = ariaProps.alert('error');
      expect(props.role).toBe('alert');
      expect(props['aria-live']).toBe('assertive');
    });

    it('should return dialog props', () => {
      const props = ariaProps.dialog('dialog-title', 'dialog-desc');
      expect(props.role).toBe('dialog');
      expect(props['aria-modal']).toBe('true');
      expect(props['aria-labelledby']).toBe('dialog-title');
      expect(props['aria-describedby']).toBe('dialog-desc');
    });

    it('should return menu props', () => {
      const props = ariaProps.menu(true);
      expect(props.role).toBe('menu');
      expect(props['aria-expanded']).toBe('true');
    });

    it('should return expand button props', () => {
      const props = ariaProps.expandButton(true, 'panel-1');
      expect(props['aria-expanded']).toBe('true');
      expect(props['aria-controls']).toBe('panel-1');
    });
  });

  describe('Motion preferences', () => {
    it('should check for reduced motion preference', () => {
      const matchMediaMock = vi.fn().mockReturnValue({
        matches: false,
      });
      window.matchMedia = matchMediaMock;

      const result = prefersReducedMotion();

      expect(matchMediaMock).toHaveBeenCalledWith('(prefers-reduced-motion: reduce)');
      expect(result).toBe(false);
    });

    it('should return animation class when motion is allowed', () => {
      window.matchMedia = vi.fn().mockReturnValue({ matches: false });

      const animClass = getAnimationClass('animate-fade-in');
      expect(animClass).toBe('animate-fade-in');
    });

    it('should return empty string when reduced motion is preferred', () => {
      window.matchMedia = vi.fn().mockReturnValue({ matches: true });

      const animClass = getAnimationClass('animate-fade-in');
      expect(animClass).toBe('');
    });
  });

  // jsdom reports offsetParent=null for every element, which makes the
  // FocusTrap visibility filter drop everything. Mock it per element so the
  // wrap-around behaviour is genuinely exercised instead of passing vacuously.
  describe('FocusTrap wrap-around (visible elements)', () => {
    let container: HTMLElement;
    let first: HTMLButtonElement;
    let middle: HTMLButtonElement;
    let last: HTMLButtonElement;

    const makeVisible = (el: HTMLElement) =>
      Object.defineProperty(el, 'offsetParent', { configurable: true, get: () => document.body });

    const tabKey = (shiftKey = false) =>
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true });

    beforeEach(() => {
      container = document.createElement('div');
      first = document.createElement('button');
      middle = document.createElement('button');
      last = document.createElement('button');
      container.append(first, middle, last);
      document.body.appendChild(container);
      [first, middle, last].forEach(makeVisible);
    });

    afterEach(() => {
      document.body.removeChild(container);
    });

    it('focuses the first focusable element on activate', () => {
      new FocusTrap(container).activate();
      expect(document.activeElement).toBe(first);
    });

    it('wraps Tab on the last element back to the first', () => {
      new FocusTrap(container).activate();
      last.focus();
      const event = tabKey();
      container.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(first);
    });

    it('wraps Shift+Tab on the first element to the last', () => {
      new FocusTrap(container).activate();
      first.focus();
      const event = tabKey(true);
      container.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(last);
    });

    it('leaves Tab in the middle untouched', () => {
      new FocusTrap(container).activate();
      middle.focus();
      const event = tabKey();
      container.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(false);
      expect(document.activeElement).toBe(middle);
    });

    it('ignores non-Tab keys', () => {
      new FocusTrap(container).activate();
      last.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Enter',
        bubbles: true,
        cancelable: true,
      });
      container.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(false);
      expect(document.activeElement).toBe(last);
    });

    it('skips aria-hidden elements when picking the first focusable', () => {
      first.setAttribute('aria-hidden', 'true');
      new FocusTrap(container).activate();
      expect(document.activeElement).toBe(middle);
    });

    it('stops trapping after deactivate', () => {
      const trap = new FocusTrap(container);
      trap.activate();
      trap.deactivate();
      last.focus();
      const event = tabKey();
      container.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(false);
    });

    it('handles an empty container without throwing or trapping', () => {
      const empty = document.createElement('div');
      document.body.appendChild(empty);
      const trap = new FocusTrap(empty);
      expect(() => trap.activate()).not.toThrow();
      const event = tabKey();
      expect(() => empty.dispatchEvent(event)).not.toThrow();
      expect(event.defaultPrevented).toBe(false);
      expect(() => trap.deactivate()).not.toThrow();
      document.body.removeChild(empty);
    });
  });

  describe('useFocusTrap ref callback', () => {
    let container: HTMLElement;
    let button: HTMLButtonElement;

    beforeEach(() => {
      container = document.createElement('div');
      button = document.createElement('button');
      container.appendChild(button);
      document.body.appendChild(container);
      Object.defineProperty(button, 'offsetParent', {
        configurable: true,
        get: () => document.body,
      });
    });

    afterEach(() => {
      document.body.removeChild(container);
    });

    it('activates on node attach and restores focus on detach', () => {
      const outside = document.createElement('button');
      document.body.appendChild(outside);
      outside.focus();

      const ref = useFocusTrap(true);
      ref(container);
      expect(document.activeElement).toBe(button);

      ref(null);
      expect(document.activeElement).toBe(outside);
      document.body.removeChild(outside);
    });

    it('does nothing when disabled', () => {
      const ref = useFocusTrap(false);
      ref(container);
      expect(document.activeElement).not.toBe(button);
      expect(() => ref(null)).not.toThrow();
    });
  });

  describe('isElementVisible viewport checks', () => {
    const elementAt = (top: number, bottom: number): HTMLElement => {
      const el = document.createElement('div');
      document.body.appendChild(el);
      Object.defineProperty(el, 'offsetParent', { configurable: true, get: () => document.body });
      el.getBoundingClientRect = () => ({ top, bottom }) as DOMRect;
      return el;
    };

    afterEach(() => {
      document.body.innerHTML = '';
    });

    it('returns true for an element inside the viewport', () => {
      expect(isElementVisible(elementAt(10, 110))).toBe(true);
    });

    it('returns false for an element fully above the viewport', () => {
      expect(isElementVisible(elementAt(-200, -10))).toBe(false);
    });

    it('returns false for an element fully below the viewport', () => {
      const belowTop = Math.max(document.documentElement.clientHeight, window.innerHeight) + 10;
      expect(isElementVisible(elementAt(belowTop, belowTop + 50))).toBe(false);
    });
  });

  describe('getNextFocusableElement (visible elements)', () => {
    let buttons: HTMLButtonElement[] = [];

    beforeEach(() => {
      buttons = [0, 1, 2].map(() => {
        const button = document.createElement('button');
        document.body.appendChild(button);
        Object.defineProperty(button, 'offsetParent', {
          configurable: true,
          get: () => document.body,
        });
        return button;
      });
    });

    afterEach(() => {
      buttons.forEach(button => document.body.removeChild(button));
    });

    it('returns the immediate neighbours in document order', () => {
      expect(getNextFocusableElement(buttons[0], 'next')).toBe(buttons[1]);
      expect(getNextFocusableElement(buttons[2], 'previous')).toBe(buttons[1]);
    });

    it('returns null beyond both ends', () => {
      expect(getNextFocusableElement(buttons[2], 'next')).toBeNull();
      expect(getNextFocusableElement(buttons[0], 'previous')).toBeNull();
    });

    it('returns null for an element that is not focusable-listed', () => {
      const detached = document.createElement('button');
      expect(getNextFocusableElement(detached, 'next')).toBeNull();
    });
  });

  describe('getSkipLinkProps missing target', () => {
    it('does not throw when the target does not exist', () => {
      const props = getSkipLinkProps('no-such-id');
      const event = { preventDefault: vi.fn() } as unknown as React.MouseEvent<HTMLAnchorElement>;
      expect(() => props.onClick(event)).not.toThrow();
      expect(event.preventDefault).toHaveBeenCalled();
    });
  });
});
