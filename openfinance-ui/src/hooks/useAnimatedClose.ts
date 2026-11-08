import { useCallback, useEffect, useRef, useState } from 'react';
import type { AnimationEventHandler } from 'react';

/** Retain a conditionally mounted detail panel until its exit animation finishes. */
export function useAnimatedClose(onClosed: () => void): {
  onClose: () => void;
  closing: boolean;
  onAnimationEnd: AnimationEventHandler<HTMLElement>;
} {
  const [closing, setClosing] = useState(false);
  const finished = useRef(false);
  const finish = useCallback(() => {
    if (finished.current) return;
    finished.current = true;
    onClosed();
  }, [onClosed]);

  useEffect(() => {
    if (!closing) return;
    // Also dismiss if animations are disabled or the browser drops animationend.
    const timer = window.setTimeout(finish, 240);
    return () => window.clearTimeout(timer);
  }, [closing, finish]);

  return {
    onClose: useCallback(() => setClosing(true), []),
    closing,
    onAnimationEnd: event => {
      if (closing && event.target === event.currentTarget) finish();
    },
  };
}
