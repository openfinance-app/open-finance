import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    // Default 5s per-test timeout is too tight for heavy integration renders
    // (e.g. DashboardPage) when the full suite runs in parallel and CPU is
    // contended, causing sporadic "Test timed out in 5000ms" flakes.
    testTimeout: 15000,
    pool: 'vmThreads',
    execArgv: ['--experimental-require-module'],
    setupFiles: ['./src/test/globals-polyfill.ts', './src/test/setup.ts'],
    // Keep test output minimal: suppress noisy console.* that is expected
    // (i18next banner, MSW unhandled warnings, intentional error-path logs).
    // Vitest forwards every console.* call here regardless of level.
    onConsoleLog(log) {
      if (log.includes('🌐 i18next')) return false;
      if (log.includes('browsers data (caniuse-lite) is')) return false;
      if (log.includes('Browserslist:')) return false;
      if (log.includes('is using incorrect casing')) return false;
      if (log.includes('unrecognized in this browser')) return false;
      if (log.includes('No response from server:')) return false;
      if (log.includes('Access forbidden:')) return false;
      if (log.includes('Server error:')) return false;
      if (log.includes('Failed to load simulations')) return false;
      if (log.includes('Failed to save simulation')) return false;
      if (log.includes('Failed to delete simulation')) return false;
      if (log.includes('Failed to import simulations')) return false;
      if (log.includes('Failed to save institution')) return false;
      if (log.includes('Failed to delete institution')) return false;
      if (log.includes('Failed to save transaction')) return false;
      if (log.includes('Failed to delete transaction')) return false;
      if (log.includes('Failed to save payee')) return false;
      if (log.includes('Failed to load image')) return false;
      if (log.includes('Login failed')) return false;
      if (log.includes('was not wrapped in act')) return false;
      if (log.includes('not configured to support act')) return false;
      if (log.includes('An update to')) return false;
      // allow other logs
    },
    exclude: ['node_modules/**', 'dist/**', 'e2e/**', '**/*.spec.ts', '**/*.spec.tsx'],
    deps: {
      optimizer: {
        ssr: {
          include: ['react-router', 'react-router-dom', '@reduxjs/toolkit', 'recharts'],
        },
      },
    },
    server: {
      deps: {
        inline: [/react-router/, '@reduxjs/toolkit', /recharts/],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'src/test/',
        'src/tests/',
        '**/*.d.ts',
        '**/*.config.*',
        '**/mockData',
        'dist/',
        'public/**'
      ]
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './src')
    }
  }
});
