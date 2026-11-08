import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPortal } from 'react-dom';
import { fireEvent, renderWithProviders, mockAuthentication, screen } from '@/test/test-utils';
import { SelectionClickGuard } from '@/components/SelectionClickGuard';

function selectText(element: HTMLElement): void {
  const range = document.createRange();
  range.selectNodeContents(element);
  document.getSelection()?.removeAllRanges();
  document.getSelection()?.addRange(range);
  fireEvent(document, new Event('selectionchange'));
}

function pointerDown(element: HTMLElement): void {
  fireEvent(element, new MouseEvent('pointerdown', { button: 0, bubbles: true }));
}

describe('SelectionClickGuard', () => {
  beforeEach(() => {
    mockAuthentication();
    document.getSelection()?.removeAllRanges();
  });

  it('keeps selected card text without activating the card or its parent', () => {
    const open = vi.fn();
    const parent = vi.fn();
    renderWithProviders(
      <>
        <SelectionClickGuard />
        <div onClick={parent}>
          <div onClick={open}>
            <span>Everyday account</span>
          </div>
        </div>
      </>
    );
    const text = screen.getByText('Everyday account');
    pointerDown(text);
    selectText(text);
    fireEvent.click(text, { detail: 1 });
    expect(open).not.toHaveBeenCalled();
    expect(parent).not.toHaveBeenCalled();
    expect(document.getSelection()?.toString()).toBe('Everyday account');
  });

  it('allows a subsequent ordinary click and keyboard activation', () => {
    const open = vi.fn();
    renderWithProviders(
      <>
        <SelectionClickGuard />
        <button onClick={open}>Open details</button>
      </>
    );
    const button = screen.getByRole('button');
    selectText(button);
    pointerDown(button);
    fireEvent.click(button, { detail: 1 });
    fireEvent.click(button, { detail: 0 });
    expect(open).toHaveBeenCalledTimes(2);
  });

  it('protects portal surfaces and recovers after a cancelled pointer gesture', () => {
    const close = vi.fn();
    renderWithProviders(
      <>
        <SelectionClickGuard />
        {createPortal(
          <div onClick={close}>
            <span>Selectable details</span>
          </div>,
          document.body
        )}
      </>
    );
    const text = screen.getByText('Selectable details');
    pointerDown(text);
    selectText(text);
    fireEvent.click(text, { detail: 1 });
    expect(close).not.toHaveBeenCalled();
    pointerDown(text);
    fireEvent.pointerCancel(text);
    fireEvent.click(text, { detail: 1 });
    expect(close).toHaveBeenCalledOnce();
  });
});
