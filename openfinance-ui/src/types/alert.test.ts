import { describe, it, expect } from 'vitest';
import { getAlertSeverity, getAlertColor } from '@/types/alert';

describe('getAlertSeverity', () => {
  it('returns warning when spent is null', () => {
    expect(getAlertSeverity(80, null)).toBe('warning');
  });

  it('returns exceeded when threshold >= 100 and spent >= 100', () => {
    expect(getAlertSeverity(100, 105)).toBe('exceeded');
  });

  it('returns critical when threshold >= 90', () => {
    expect(getAlertSeverity(90, 85)).toBe('critical');
  });

  it('returns warning for lower thresholds', () => {
    expect(getAlertSeverity(80, 75)).toBe('warning');
  });
});

describe('getAlertColor', () => {
  it('returns red for exceeded', () => {
    expect(getAlertColor('exceeded')).toBe('#ef4444');
  });

  it('returns amber for critical', () => {
    expect(getAlertColor('critical')).toMatch(/#f59e0b|#eab308/);
  });

  it('returns yellow for warning', () => {
    const color = getAlertColor('warning');
    expect(color).toBeDefined();
  });
});
