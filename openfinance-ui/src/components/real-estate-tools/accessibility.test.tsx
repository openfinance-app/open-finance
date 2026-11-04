import { useRef } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import {
  LiveRegion,
  SkipToContent,
  VisuallyHidden,
  useAnnouncer,
  useFocusTrap,
  useKeyboardShortcuts,
} from '@/components/real-estate-tools/accessibility';

function Shortcuts({ action }: { action: () => void }) {
  useKeyboardShortcuts({ 'ctrl+shift+alt+s': action });
  const { announce } = useAnnouncer();
  return (
    <>
      <input aria-label="Name" />
      <textarea aria-label="Notes" />
      <select aria-label="Type">
        <option>Home</option>
      </select>
      <button onClick={() => announce('Calculation complete')}>Announce</button>
      <LiveRegion id="calculation-status">Ready</LiveRegion>
      <SkipToContent contentId="results" />
      <VisuallyHidden>Calculation help</VisuallyHidden>
    </>
  );
}
function FocusDialog({ active = true }: { active?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  useFocusTrap(active, ref as React.RefObject<HTMLElement>);
  return (
    <div ref={ref}>
      <button disabled>Unavailable</button>
      <button>First</button>
      <button>Last</button>
    </div>
  );
}
beforeEach(() => mockAuthentication());

describe('keyboard and focus behavior', () => {
  it('handles shortcuts outside form fields and removes listeners on unmount', () => {
    const action = vi.fn();
    const { unmount } = renderWithProviders(<Shortcuts action={action} />);
    const keys = { key: 'S', ctrlKey: true, shiftKey: true, altKey: true };
    fireEvent.keyDown(window, keys);
    expect(action).toHaveBeenCalledOnce();
    for (const label of ['Name', 'Notes', 'Type'])
      fireEvent.keyDown(screen.getByLabelText(label), keys);
    expect(action).toHaveBeenCalledOnce();
    fireEvent.keyDown(window, { ...keys, ctrlKey: false, metaKey: true });
    expect(action).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('link')).toHaveAttribute('href', '#results');
    unmount();
    fireEvent.keyDown(window, keys);
    expect(action).toHaveBeenCalledTimes(2);
  });
  it('starts at the first enabled control and wraps tab navigation in both directions', () => {
    renderWithProviders(<FocusDialog />);
    const first = screen.getByRole('button', { name: 'First' });
    const last = screen.getByRole('button', { name: 'Last' });
    expect(first).toHaveFocus();
    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(first).toHaveFocus();
  });
  it('does not move focus when the trap is inactive', () => {
    renderWithProviders(<FocusDialog active={false} />);
    expect(screen.getByRole('button', { name: 'First' })).not.toHaveFocus();
  });
});
