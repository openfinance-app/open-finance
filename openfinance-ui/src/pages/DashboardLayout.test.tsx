import { useEffect, type ReactNode } from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import DashboardPage from '@/pages/DashboardPage';

interface Item {
  i: string;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
}
interface GridProps {
  children: ReactNode;
  layouts: Record<string, Item[]>;
  onLayoutChange?: (current: Item[], all: Record<string, Item[]>) => void;
  onBreakpointChange: (breakpoint: string) => void;
  onDragStop: (layout: Item[]) => void;
  onResizeStop: (layout: Item[]) => void;
}

const grid = vi.hoisted(() => ({ current: undefined as GridProps | undefined }));

vi.mock('react-grid-layout/legacy', () => ({
  WidthProvider: (component: unknown) => component,
  Responsive: (props: GridProps) => {
    grid.current = props;
    const { children, layouts, onLayoutChange, onBreakpointChange, onDragStop, onResizeStop } =
      props;
    // A controlled widget may echo equivalent automatic layouts after receiving props.
    useEffect(() => {
      const current = layouts.lg.map(item => ({ ...item }));
      onLayoutChange?.(current, { ...layouts, lg: current });
    }, [layouts, onLayoutChange]);
    return (
      <div>
        <button onClick={() => onBreakpointChange('xs')}>Use phone layout</button>
        <button onClick={() => onBreakpointChange('md')}>Use tablet layout</button>
        <button onClick={() => onDragStop([])}>Finish empty move</button>
        <button onClick={() => onDragStop(layouts.lg)}>Finish unchanged move</button>
        <button onClick={() => onResizeStop([{ i: 'netWorth', x: 0, y: 1, w: 6, h: 8 }])}>
          Complete card resize
        </button>
        <button onClick={() => onDragStop([{ i: 'new-card', x: 0, y: 20, w: 6, h: 6 }])}>
          Append new card
        </button>
        <button
          onClick={() => {
            onBreakpointChange('lg');
            onDragStop([{ i: 'netWorth', x: 2, y: 0, w: 4, h: 5 }]);
          }}
        >
          Complete card move
        </button>
        {children}
      </div>
    );
  },
}));

beforeEach(() => {
  localStorage.clear();
  sessionStorage.removeItem('dashboard_period');
  grid.current = undefined;
  mockAuthentication();
});

it.each(['{broken', 'null', '[]', '{}', '{"lg":{}}', '{"lg":[],"xs":5}'])(
  'recovers safely from malformed saved layout %s',
  async saved => {
    localStorage.setItem('open_finance_dashboard_layouts', saved);
    renderWithProviders(<DashboardPage />);
    await screen.findByRole('button', { name: 'Complete card move' });
    expect(grid.current?.layouts.lg.some(item => item.i === 'netWorth')).toBe(true);
    expect(Array.isArray(grid.current?.layouts.xs)).toBe(true);
  }
);

it('repairs missing and collapsed cards while retaining valid desktop positions', async () => {
  localStorage.setItem(
    'open_finance_dashboard_layouts',
    JSON.stringify({
      lg: [
        { i: 'netWorth', x: 2, y: 3, w: 4, h: 5 },
        { i: 'cashFlow', x: 0, y: 0, w: 1, h: 1 },
      ],
      md: [{ i: 'netWorth', x: 0, y: 0, w: 1, h: 1 }],
    })
  );
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  const saved = JSON.parse(localStorage.getItem('open_finance_dashboard_layouts')!);
  expect(saved.lg.find((item: Item) => item.i === 'netWorth')).toMatchObject({
    x: 2,
    y: 3,
    w: 4,
    h: 5,
  });
  expect(saved.lg.find((item: Item) => item.i === 'cashFlow')).toMatchObject({ w: 12, h: 7 });
  expect(saved.lg.length).toBeGreaterThan(10);
  expect(saved.md).toBeUndefined();
  expect(grid.current?.layouts.xs.find(item => item.i === 'netWorth')).toMatchObject({
    x: 0,
    y: 0,
    h: 7,
    minH: 7,
  });
});

it('rebuilds a collapsed non-desktop breakpoint even when the desktop layout is complete', async () => {
  const first = renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  const layout = grid.current!.layouts.lg;
  first.unmount();
  localStorage.setItem(
    'open_finance_dashboard_layouts',
    JSON.stringify({ lg: layout, md: [{ i: 'cashFlow', x: 0, y: 0, w: 1, h: 1 }] })
  );
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  expect(JSON.parse(localStorage.getItem('open_finance_dashboard_layouts')!).md).toBeUndefined();
});

it('enforces minimum card dimensions while retaining a usable saved phone layout', async () => {
  const first = renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  const layout = grid.current!.layouts.lg.map(item =>
    item.i === 'netWorth' ? { ...item, w: 2, h: 2 } : item
  );
  first.unmount();
  localStorage.setItem(
    'open_finance_dashboard_layouts',
    JSON.stringify({ lg: layout, xs: [{ i: 'netWorth', x: 0, y: 0, w: 2, h: 3 }] })
  );
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  expect(grid.current?.layouts.lg.find(item => item.i === 'netWorth')).toMatchObject({
    w: 3,
    h: 4,
  });
  expect(grid.current?.layouts.xs[0]).toMatchObject({ h: 7, minH: 7, w: 2 });
});

it('ignores empty and unchanged gestures without writing storage', async () => {
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  fireEvent.click(screen.getByRole('button', { name: 'Finish empty move' }));
  fireEvent.click(screen.getByRole('button', { name: 'Finish unchanged move' }));
  expect(localStorage.getItem('open_finance_dashboard_layouts')).toBeNull();
});

it('saves resize gestures in their active breakpoint and preserves desktop layout', async () => {
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  const original = grid.current!.layouts.lg;
  fireEvent.click(screen.getByRole('button', { name: 'Use tablet layout' }));
  fireEvent.click(screen.getByRole('button', { name: 'Complete card resize' }));
  const saved = JSON.parse(localStorage.getItem('open_finance_dashboard_layouts')!);
  expect(saved.md.find((item: Item) => item.i === 'netWorth')).toMatchObject({ y: 1, w: 6, h: 8 });
  expect(saved.lg).toEqual(original);
});

it('appends a newly available card without discarding existing geometry', async () => {
  renderWithProviders(<DashboardPage />);
  await screen.findByRole('button', { name: 'Complete card move' });
  fireEvent.click(screen.getByRole('button', { name: 'Append new card' }));
  const saved = JSON.parse(localStorage.getItem('open_finance_dashboard_layouts')!);
  expect(saved.lg.find((item: Item) => item.i === 'new-card')).toMatchObject({ y: 20, h: 6 });
  expect(saved.lg.some((item: Item) => item.i === 'netWorth')).toBe(true);
});

it('ignores automatic layout echoes and preserves other cards when saving a completed move', async () => {
  renderWithProviders(<DashboardPage />);
  const move = await screen.findByRole('button', { name: 'Complete card move' });
  expect(localStorage.getItem('open_finance_dashboard_layouts')).toBeNull();
  fireEvent.click(move);
  await waitFor(() => {
    const saved = JSON.parse(localStorage.getItem('open_finance_dashboard_layouts') ?? '{}');
    expect(saved.lg.find((item: Item) => item.i === 'netWorth').x).toBe(2);
    expect(saved.lg.some((item: Item) => item.i === 'cashFlow')).toBe(true);
  });
});
