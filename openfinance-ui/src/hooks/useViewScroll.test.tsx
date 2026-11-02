import { useCallback, useRef, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { useViewScroll } from '@/hooks/useViewScroll';
import { useViewStateStore } from '@/stores/viewState';

function AsyncList() {
  const scrollRef = useViewScroll();
  const [ready, setReady] = useState(false);
  const maxScroll = useRef(0);
  const attach = useCallback(
    (node: HTMLElement | null) => {
      if (node) {
        let position = 0;
        Object.defineProperty(node, 'scrollTop', {
          configurable: true,
          get: () => position,
          set: value => {
            position = Math.min(value, maxScroll.current);
          },
        });
      }
      scrollRef(node);
    },
    [scrollRef]
  );
  return (
    <>
      <button
        onClick={() => {
          maxScroll.current = 900;
          setReady(true);
        }}
      >
        Load results
      </button>
      <main ref={attach}>{ready ? <p>Results</p> : <p>Loading</p>}</main>
    </>
  );
}

describe('view scroll restoration', () => {
  afterEach(() => vi.unstubAllGlobals());
  beforeEach(() => {
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({ key: 'scroll-test' }, '', '/list');
    useViewStateStore.getState().save('scroll-test', '/list', 'scroll', 500);
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
  });

  it('waits for async results instead of losing the saved position when the loading surface is short', async () => {
    renderWithProviders(<AsyncList />);
    expect(screen.getByRole('main').scrollTop).toBe(0);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Load results' }));
    await waitFor(() => expect(screen.getByRole('main').scrollTop).toBe(500));
  });

  it('stops restoring when the user scrolls before the results arrive', async () => {
    renderWithProviders(<AsyncList />);
    fireEvent.wheel(screen.getByRole('main'));
    await userEvent.setup().click(screen.getByRole('button', { name: 'Load results' }));
    await waitFor(() => expect(screen.getByText('Results')).toBeInTheDocument());
    expect(screen.getByRole('main').scrollTop).toBe(0);
  });

  it('does not put scroll state back into the store when logout unmounts the old view', async () => {
    const { unmount } = renderWithProviders(<AsyncList />);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Load results' }));
    useViewStateStore.getState().clear();
    unmount();
    expect(useViewStateStore.getState().entries).toEqual({});
    expect(useViewStateStore.getState().latest).toEqual({});
  });
});
