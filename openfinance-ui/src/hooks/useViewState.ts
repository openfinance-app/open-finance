import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react';
import { useLocation } from 'react-router';
import { readViewState, useViewStateStore } from '@/stores/viewState';

/** Like useState, but restores each history entry and remembers the last visit to a view.
 * Explicit deep links can start fresh; Back/Forward always restore their own snapshot.
 * Keep dialogs for writes and unsaved forms in ordinary useState.
 */
export function useViewState<T>(
  field: string,
  initial: T | (() => T),
  fresh = false
): [T, Dispatch<SetStateAction<T>>] {
  const { key, pathname } = useLocation();
  const sessionGeneration = useRef(useViewStateStore.getState().generation);
  const initialize = () =>
    readViewState(
      key,
      pathname,
      field,
      () => (typeof initial === 'function' ? (initial as () => T)() : initial),
      fresh
    );
  const [identity, setIdentity] = useState(`${key}:${field}`);
  const [value, setValue] = useState<T>(initialize);
  if (identity !== `${key}:${field}`) {
    setIdentity(`${key}:${field}`);
    setValue(initialize);
  }
  const current = useRef(value);
  const update = useCallback<Dispatch<SetStateAction<T>>>(
    next => {
      if (sessionGeneration.current !== useViewStateStore.getState().generation) return;
      const resolved =
        typeof next === 'function' ? (next as (previous: T) => T)(current.current) : next;
      current.current = resolved;
      useViewStateStore.getState().save(key, pathname, field, resolved);
      setValue(resolved);
    },
    [key, pathname, field]
  );
  useLayoutEffect(() => {
    current.current = value;
    if (sessionGeneration.current === useViewStateStore.getState().generation)
      useViewStateStore.getState().save(key, pathname, field, value);
  }, [key, pathname, field, value]);
  return [value, update];
}
