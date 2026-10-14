import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Maximize2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const DEFAULT_VIEW = { scale: 1, x: 0, y: 0 };
const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;

/** Wheel zoom is anchored at the pointer; dragging pans the enlarged diagram. */
export function SankeyViewport({ children }: { children: ReactNode }) {
  const { t } = useTranslation('dashboard');
  const containerRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState(DEFAULT_VIEW);
  const drag = useRef<{ x: number; y: number; origin: typeof DEFAULT_VIEW } | null>(null);
  const dragged = useRef(false);

  const zoomAt = useCallback((factor: number, x: number, y: number) => {
    setView(previous => {
      const scale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, previous.scale * factor));
      const ratio = scale / previous.scale;
      return { scale, x: x - (x - previous.x) * ratio, y: y - (y - previous.y) * ratio };
    });
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const onWheel = (event: WheelEvent) => {
      if (event.deltaY === 0) return;
      event.preventDefault();
      const bounds = container.getBoundingClientRect();
      const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? bounds.height : 1;
      zoomAt(
        2 ** (-event.deltaY * unit * 0.002),
        event.clientX - bounds.left,
        event.clientY - bounds.top
      );
    };
    // React's wheel listeners are passive; a native listener prevents page scrolling while zooming.
    container.addEventListener('wheel', onWheel, { passive: false });
    return () => container.removeEventListener('wheel', onWheel);
  }, [zoomAt]);

  return (
    <div className="relative flex-1 min-h-0">
      <div
        ref={containerRef}
        role="group"
        aria-label={t('cashflowSankey.chartLabel')}
        tabIndex={0}
        title={t('cashflowSankey.zoomHint')}
        className="h-full overflow-hidden cursor-grab active:cursor-grabbing touch-none outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
        onKeyDown={event => {
          if (event.target !== event.currentTarget) return;
          if (event.key === 'Home' || event.key === '0') {
            event.preventDefault();
            setView(DEFAULT_VIEW);
          } else if (['+', '=', '-'].includes(event.key)) {
            event.preventDefault();
            const bounds = event.currentTarget.getBoundingClientRect();
            zoomAt(event.key === '-' ? 1 / 1.25 : 1.25, bounds.width / 2, bounds.height / 2);
          }
        }}
        onPointerDown={event => {
          if (event.button !== 0) return;
          dragged.current = false;
          drag.current = { x: event.clientX, y: event.clientY, origin: view };
        }}
        onPointerMove={event => {
          if (!drag.current) return;
          const x = event.clientX - drag.current.x;
          const y = event.clientY - drag.current.y;
          if (!dragged.current && Math.hypot(x, y) < 4) return;
          dragged.current = true;
          event.currentTarget.setPointerCapture(event.pointerId);
          setView({
            ...drag.current.origin,
            x: drag.current.origin.x + x,
            y: drag.current.origin.y + y,
          });
        }}
        onPointerUp={() => {
          drag.current = null;
        }}
        onPointerCancel={() => {
          drag.current = null;
        }}
        onPointerLeave={() => {
          if (!dragged.current) drag.current = null;
        }}
        onClickCapture={event => {
          if (dragged.current) {
            event.preventDefault();
            event.stopPropagation();
            dragged.current = false;
          }
        }}
      >
        <div
          className="h-full w-full origin-top-left select-none"
          style={{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.scale})` }}
        >
          {children}
        </div>
      </div>
      <button
        type="button"
        onClick={() => setView(DEFAULT_VIEW)}
        disabled={view.scale === 1 && view.x === 0 && view.y === 0}
        className="absolute bottom-2 left-2 flex items-center gap-1 bg-surface-elevated/90 rounded border border-border p-1 text-xs text-text-secondary hover:text-text-primary disabled:opacity-40"
        aria-label={t('cashflowSankey.resetZoom')}
        title={t('cashflowSankey.resetZoom')}
      >
        <Maximize2 className="w-3.5 h-3.5" />
        {Math.round(view.scale * 100)}%
      </button>
    </div>
  );
}
