import { describe, it, expect } from 'vitest';
import { lazyImageProps, generateSrcSet } from '@/utils/imageUtils';

describe('lazyImageProps', () => {
  it('has loading lazy', () => {
    expect(lazyImageProps.loading).toBe('lazy');
  });

  it('has decoding async', () => {
    expect(lazyImageProps.decoding).toBe('async');
  });
});

describe('generateSrcSet', () => {
  it('generates srcset from default widths', () => {
    const result = generateSrcSet('/images/photo.jpg');
    expect(result).toContain('320w');
    expect(result).toContain('640w');
    expect(result).toContain('960w');
    expect(result).toContain('1280w');
  });

  it('generates srcset from custom widths', () => {
    const result = generateSrcSet('/images/photo.jpg', [100, 200]);
    expect(result).toContain('100w');
    expect(result).toContain('200w');
    expect(result).not.toContain('320w');
  });

  it('includes source URL with width param', () => {
    const result = generateSrcSet('/images/photo.jpg', [320]);
    expect(result).toContain('/images/photo.jpg');
  });
});
