import { create } from 'zustand';

type Snapshot = Record<string, unknown>;

interface ViewStateStore {
  generation: number;
  entries: Record<string, Snapshot>;
  latest: Record<string, Snapshot>;
  save: (entry: string, path: string, field: string, value: unknown) => void;
  clear: () => void;
}

/** Session-only UI state. Never persist financial filters or server objects to disk. */
export const useViewStateStore = create<ViewStateStore>(set => ({
  generation: 0,
  entries: {},
  latest: {},
  save: (entry, path, field, value) =>
    set(state => {
      const snapshot = { ...state.entries[entry], [field]: value };
      const entries = { ...state.entries, [entry]: snapshot };
      const keys = Object.keys(entries);
      if (keys.length > 100) delete entries[keys[0]];
      return {
        entries,
        latest: { ...state.latest, [path]: { ...state.latest[path], [field]: value } },
      };
    }),
  clear: () => set(state => ({ entries: {}, latest: {}, generation: state.generation + 1 })),
}));

export function readViewState<T>(
  entry: string,
  path: string,
  field: string,
  initial: () => T,
  fresh = false
): T {
  const { entries, latest } = useViewStateStore.getState();
  const snapshot = entries[entry];
  if (snapshot && Object.hasOwn(snapshot, field)) return snapshot[field] as T;
  if (!fresh && latest[path] && Object.hasOwn(latest[path], field)) return latest[path][field] as T;
  return initial();
}
