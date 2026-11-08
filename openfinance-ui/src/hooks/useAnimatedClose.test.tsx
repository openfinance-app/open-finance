import { useState } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, mockAuthentication, renderWithProviders, screen } from '@/test/test-utils';
import { waitFor } from '@testing-library/react';
import { useAnimatedClose } from '@/hooks/useAnimatedClose';

function Panel({ onClosed }: { onClosed: () => void }) {
  const { onClose, closing, onAnimationEnd } = useAnimatedClose(onClosed);
  return (
    <div role="dialog" data-state={closing ? 'closed' : 'open'} onAnimationEnd={onAnimationEnd}>
      <button onClick={onClose}>Close details</button>
      <span>Child content</span>
    </div>
  );
}

beforeEach(() => mockAuthentication());

it('retains content until its own exit finishes, then closes only once', () => {
  const closed = vi.fn();
  renderWithProviders(<Panel onClosed={closed} />);
  fireEvent.click(screen.getByRole('button'));
  expect(screen.getByRole('dialog')).toHaveAttribute('data-state', 'closed');
  expect(closed).not.toHaveBeenCalled();
  fireEvent.animationEnd(screen.getByText('Child content'));
  expect(closed).not.toHaveBeenCalled();
  fireEvent.animationEnd(screen.getByRole('dialog'));
  fireEvent.animationEnd(screen.getByRole('dialog'));
  expect(closed).toHaveBeenCalledOnce();
});

it('still dismisses when the browser does not deliver animationend', async () => {
  function Example() {
    const [open, setOpen] = useState(true);
    return open ? <Panel onClosed={() => setOpen(false)} /> : <p>Closed</p>;
  }
  renderWithProviders(<Example />);
  fireEvent.click(screen.getByRole('button'));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(screen.getByText('Closed')).toBeInTheDocument();
});
