import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    pool: 'vmThreads',
    execArgv: ['--experimental-require-module'],
    setupFiles: ['./src/test/globals-polyfill.ts', './src/test/setup.ts'],
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
