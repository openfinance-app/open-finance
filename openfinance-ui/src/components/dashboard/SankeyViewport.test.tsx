import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { SankeyViewport } from '@/components/dashboard/SankeyViewport';

beforeEach(() => {
  mockAuthentication();
  vi.stubGlobal('PointerEvent', MouseEvent);
  Element.prototype.setPointerCapture = vi.fn();
});
afterEach(() => vi.unstubAllGlobals());
function mount() {
  const click = vi.fn();
  const view = renderWithProviders(
    <SankeyViewport>
      <button onClick={click}>Inspect category</button>
    </SankeyViewport>
  );
  const viewport = screen.getByRole('group', { name: 'Cash flow diagram' });
  vi.spyOn(viewport, 'getBoundingClientRect').mockReturnValue({
    x: 10,
    y: 20,
    left: 10,
    top: 20,
    right: 410,
    bottom: 520,
    width: 400,
    height: 500,
    toJSON: () => ({}),
  });
  return {
    ...view,
    viewport,
    click,
    content: viewport.firstElementChild!,
    child: screen.getByRole('button', { name: 'Inspect category' }),
  };
}

describe('Cash flow diagram interaction boundaries', () => {
  it.each([
    [0, -500],
    [1, -31.25],
    [2, -1],
  ])('anchors wheel zoom consistently across delta unit %s', (deltaMode, deltaY) => {
    const { viewport, content } = mount();
    fireEvent.wheel(viewport, { deltaY, deltaMode, clientX: 110, clientY: 120 });
    expect(content).toHaveStyle({ transform: 'translate(-100px, -100px) scale(2)' });
  });

  it('does not consume horizontal-only wheel scrolling', () => {
    const { viewport, content } = mount();
    const event = new WheelEvent('wheel', { deltaX: 20, deltaY: 0, cancelable: true });
    fireEvent(viewport, event);
    expect(event.defaultPrevented).toBe(false);
    expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
  });

  it.each(['+', '=', '-'])(
    'zooms with %s around the viewport center without hijacking a child control',
    key => {
      const { viewport, content, child } = mount();
      fireEvent.keyDown(child, { key });
      expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
      fireEvent.keyDown(viewport, { key });
      expect(screen.getByRole('button', { name: 'Reset zoom' })).toHaveTextContent(
        key === '-' ? '80%' : '125%'
      );
      fireEvent.keyDown(viewport, { key: 'ArrowRight' });
      expect(screen.getByRole('button', { name: 'Reset zoom' })).toHaveTextContent(
        key === '-' ? '80%' : '125%'
      );
    }
  );

  it.each(['Home', '0'])('resets panning and zoom with %s', key => {
    const { viewport, content } = mount();
    fireEvent.keyDown(viewport, { key: '+' });
    fireEvent.keyDown(viewport, { key });
    expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
    expect(screen.getByRole('button', { name: 'Reset zoom' })).toBeDisabled();
  });

  it('ignores right-button drags and movement without an active press', () => {
    const { viewport, content } = mount();
    fireEvent.pointerMove(viewport, { clientX: 50, clientY: 60 });
    fireEvent.pointerDown(viewport, { button: 2, clientX: 0, clientY: 0 });
    fireEvent.pointerMove(viewport, { clientX: 50, clientY: 60 });
    expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
  });

  it('preserves normal clicks through small hand movement', () => {
    const { viewport, content, child, click } = mount();
    fireEvent.pointerDown(viewport, { button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(viewport, { clientX: 12, clientY: 12 });
    fireEvent.pointerUp(viewport);
    fireEvent.click(child);
    expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
    expect(click).toHaveBeenCalledOnce();
  });

  it('suppresses the click following a drag exactly once', () => {
    const { viewport, content, child, click } = mount();
    fireEvent.pointerDown(viewport, { button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(viewport, { clientX: 14, clientY: 10 });
    fireEvent.pointerMove(viewport, { clientX: 11, clientY: 10 });
    expect(content).toHaveStyle({ transform: 'translate(1px, 0px) scale(1)' });
    fireEvent.pointerUp(viewport);
    fireEvent.click(child);
    expect(click).not.toHaveBeenCalled();
    fireEvent.click(child);
    expect(click).toHaveBeenCalledOnce();
  });

  it.each(['pointerUp', 'pointerCancel', 'pointerLeave'] as const)(
    'ends a pending gesture after %s',
    end => {
      const { viewport, content } = mount();
      fireEvent.pointerDown(viewport, { button: 0, clientX: 10, clientY: 10 });
      fireEvent[end](viewport);
      fireEvent.pointerMove(viewport, { clientX: 50, clientY: 60 });
      expect(content).toHaveStyle({ transform: 'translate(0px, 0px) scale(1)' });
    }
  );

  it('continues captured dragging outside the diagram and stops on cancellation', () => {
    const { viewport, content } = mount();
    fireEvent.pointerDown(viewport, { button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(viewport, { clientX: 20, clientY: 20 });
    fireEvent.pointerLeave(viewport);
    fireEvent.pointerMove(viewport, { clientX: 40, clientY: 50 });
    expect(content).toHaveStyle({ transform: 'translate(30px, 40px) scale(1)' });
    fireEvent.pointerCancel(viewport);
    fireEvent.pointerMove(viewport, { clientX: 90, clientY: 90 });
    expect(content).toHaveStyle({ transform: 'translate(30px, 40px) scale(1)' });
  });
});
