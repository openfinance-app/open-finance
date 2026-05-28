import { describe, it, expect } from 'vitest';
import {
  DURATIONS,
  EASINGS,
  ANIMATIONS,
  pageTransition,
  modalTransition,
  dropdownTransition,
  toastTransition,
  cardHover,
  buttonPress,
  createTransition,
  staggerChildren,
  animationClasses,
} from '@/utils/animations';

describe('DURATIONS', () => {
  it('has expected preset values', () => {
    expect(DURATIONS.fast).toBe(150);
    expect(DURATIONS.normal).toBe(200);
    expect(DURATIONS.slow).toBe(300);
    expect(DURATIONS.verySlow).toBe(500);
  });
});

describe('EASINGS', () => {
  it('has standard CSS easing values', () => {
    expect(EASINGS.ease).toBe('ease');
    expect(EASINGS.linear).toBe('linear');
    expect(EASINGS.spring).toContain('cubic-bezier');
  });
});

describe('ANIMATIONS', () => {
  it('has animation class names', () => {
    expect(ANIMATIONS.fadeIn).toBe('animate-fade-in');
    expect(ANIMATIONS.spin).toBe('animate-spin');
    expect(ANIMATIONS.shimmer).toBe('animate-shimmer');
  });
});

describe('transition presets', () => {
  it('pageTransition has initial, animate, exit', () => {
    expect(pageTransition.initial).toHaveProperty('opacity', 0);
    expect(pageTransition.animate).toHaveProperty('opacity', 1);
    expect(pageTransition.exit).toHaveProperty('opacity', 0);
  });

  it('modalTransition includes scale', () => {
    expect(modalTransition.initial).toHaveProperty('scale', 0.95);
    expect(modalTransition.animate).toHaveProperty('scale', 1);
  });

  it('dropdownTransition uses y offset', () => {
    expect(dropdownTransition.initial).toHaveProperty('y', -10);
    expect(dropdownTransition.animate).toHaveProperty('y', 0);
  });

  it('toastTransition uses x offset', () => {
    expect(toastTransition.initial).toHaveProperty('x', 100);
    expect(toastTransition.animate).toHaveProperty('x', 0);
  });
});

describe('cardHover', () => {
  it('scales up slightly', () => {
    expect(cardHover.scale).toBe(1.02);
  });
});

describe('buttonPress', () => {
  it('scales down slightly', () => {
    expect(buttonPress.scale).toBe(0.95);
  });
});

describe('createTransition', () => {
  it('uses defaults', () => {
    const t = createTransition();
    expect(t.duration).toBe(DURATIONS.normal / 1000);
    expect(t.ease).toBe(EASINGS.easeInOut);
  });

  it('accepts custom values', () => {
    const t = createTransition(500, 'ease-in');
    expect(t.duration).toBe(0.5);
    expect(t.ease).toBe('ease-in');
  });
});

describe('staggerChildren', () => {
  it('returns stagger config with default delay', () => {
    const config = staggerChildren();
    expect(config.animate.transition.staggerChildren).toBe(0.1);
  });

  it('accepts custom delay', () => {
    const config = staggerChildren(0.2);
    expect(config.animate.transition.staggerChildren).toBe(0.2);
  });
});

describe('animationClasses', () => {
  it('contains expected class strings', () => {
    expect(animationClasses.fadeIn).toContain('opacity-0');
    expect(animationClasses.shimmer).toContain('shimmer');
    expect(animationClasses.spin).toContain('spin');
    expect(animationClasses.hoverLift).toContain('hover:scale');
    expect(animationClasses.cardHover).toContain('hover:shadow');
  });
});
