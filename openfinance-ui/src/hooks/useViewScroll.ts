import { useCallback, useLayoutEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { readViewState, useViewStateStore } from '@/stores/viewState';

/** Restore the actual scrolling surface, including content that arrives asynchronously. */
export function useViewScroll(field = 'scroll') {
  const { key, pathname, search } = useLocation();
  const [element, setElement] = useState<HTMLElement | null>(null);
  const ref = useCallback((node: HTMLElement | null) => setElement(node), []);
  useLayoutEffect(() => {
    if (!element) return;
    const generation = useViewStateStore.getState().generation;
    const target = readViewState(key, pathname, field, () => 0, Boolean(search));
    let restoring = true;
    const save = () => {
      if (useViewStateStore.getState().generation === generation)
        useViewStateStore.getState().save(key, pathname, field, element.scrollTop);
    };
    const restore = () => {
      if (!restoring) return;
      // eslint-disable-next-line react-hooks/immutability -- This is a DOM scroll surface, not mutable application state.
      element.scrollTop = target;
      if (Math.abs(element.scrollTop - target) < 1) restoring = false;
    };
    const stop = () => {
      restoring = false;
      save();
    };
    const onScroll = () => {
      if (!restoring) save();
    };
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(restore);
    const contentObserver = new MutationObserver(restore);
    contentObserver.observe(element, { childList: true, subtree: true, characterData: true });
    observer?.observe(element);
    if (element.firstElementChild) observer?.observe(element.firstElementChild);
    element.addEventListener('scroll', onScroll, { passive: true });
    element.addEventListener('wheel', stop, { passive: true });
    element.addEventListener('touchstart', stop, { passive: true });
    element.addEventListener('pointerdown', stop);
    element.addEventListener('keydown', stop);
    restore();
    return () => {
      observer?.disconnect();
      contentObserver.disconnect();
      if (!restoring) save();
      element.removeEventListener('scroll', onScroll);
      element.removeEventListener('wheel', stop);
      element.removeEventListener('touchstart', stop);
      element.removeEventListener('pointerdown', stop);
      element.removeEventListener('keydown', stop);
    };
  }, [element, key, pathname, field, search]);
  return ref;
}
