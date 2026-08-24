/**
 * Test Setup Configuration
 * 
 * Configures the testing environment for all test files.
 * This file is imported automatically by Vitest.
 */

import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import React from 'react';
import { server } from './mocks/server';
import '@testing-library/jest-dom/vitest';

// Mock ResizeObserver for jsdom
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Global mock for recharts to avoid testing errors with JSDOM and ESM imports
vi.mock('recharts', async () => {
  const MockComponent = ({ children, 'data-testid': testId }: { children?: React.ReactNode; 'data-testid'?: string }) => 
    React.createElement('div', { 'data-testid': testId }, children);
  
  return {
    ResponsiveContainer: ({ children }: any) => React.createElement('div', { 'data-testid': 'responsive-container' }, children),
    AreaChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'area-chart' }, children),
    BarChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'bar-chart' }, children),
    LineChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'line-chart' }, children),
    PieChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'pie-chart' }, children),
    Treemap: ({ children }: any) => React.createElement('div', { 'data-testid': 'tree-map' }, children),
    Sankey: ({ children }: any) => React.createElement('div', { 'data-testid': 'sankey' }, children),
    RadarChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'radar-chart' }, children),
    ScatterChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'scatter-chart' }, children),
    ComposedChart: ({ children }: any) => React.createElement('div', { 'data-testid': 'composed-chart' }, children),
    Area: MockComponent,
    XAxis: MockComponent,
    YAxis: MockComponent,
    CartesianGrid: MockComponent,
    Tooltip: MockComponent,
    Legend: MockComponent,
    Bar: MockComponent,
    Line: MockComponent,
    Pie: MockComponent,
    Cell: MockComponent,
    Sector: MockComponent,
    ReferenceLine: MockComponent,
    ReferenceArea: MockComponent,
    ReferenceDot: MockComponent,
    Label: MockComponent,
    LabelList: MockComponent,
  };
});

// Global mock for react-grid-layout to avoid measurement issues in JSDOM
vi.mock('react-grid-layout/legacy', () => ({
  Responsive: ({ children }: any) => React.createElement('div', { 'data-testid': 'responsive-grid-layout' }, children),
  WidthProvider: (component: any) => component,
}));

vi.mock('react-grid-layout', () => ({
  Responsive: ({ children }: any) => React.createElement('div', { 'data-testid': 'responsive-grid-layout' }, children),
  WidthProvider: (component: any) => component,
}));

/**
 * Start MSW server before all tests — bypass unhandled requests silently in tests.
 */
beforeAll(() => {
  server.listen({ onUnhandledRequest: 'bypass' });
});

// Silence noisy console.* that intentionally exercises error branches (e.g.
// useSimulationStorage error handling). Vitest's onConsoleLog also filters
// most of these, but this fallback catches direct console.error calls that
// slip through jsdom's mapping.
const originalConsoleError = console.error;
const originalConsoleWarn = console.warn;
beforeAll(() => {
  vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
    const first = String(args[0] ?? '');
    if (
      first.includes('Failed to load simulations') ||
      first.includes('Failed to save simulation') ||
      first.includes('Failed to delete simulation') ||
      first.includes('Failed to import simulations') ||
      first.includes('Failed to save institution') ||
      first.includes('Failed to delete institution') ||
      first.includes('Failed to save transaction') ||
      first.includes('Failed to delete transaction') ||
      first.includes('Failed to save payee') ||
      first.includes('Failed to load image') ||
      first.includes('Login failed') ||
      first.includes('No response from server') ||
      first.includes('Access forbidden') ||
      first.includes('Server error') ||
      first.includes('is using incorrect casing') ||
      first.includes('unrecognized in this browser') ||
      first.includes('was not wrapped in act') ||
      first.includes('not configured to support act') ||
      first.includes('An update to')
    ) {
      return;
    }
    originalConsoleError(...(args as never[]));
  });
  vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
    const first = String(args[0] ?? '');
    if (
      first.includes('🌐 i18next') ||
      first.includes('browsers data (caniuse-lite)') ||
      first.includes('Browserslist:') ||
      first.includes('[i18n] Missing translation')
    ) {
      return;
    }
    originalConsoleWarn(...(args as never[]));
  });
});

/**
 * Reset handlers after each test to ensure test isolation
 */
afterEach(() => {
  server.resetHandlers();
});

/**
 * Clean up and close server after all tests
 */
afterAll(() => {
  server.close();
});
