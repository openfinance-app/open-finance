import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Requirement TASK-16.1.2: Optimize production build
    chunkSizeWarningLimit: 1000,
    // Minify with esbuild (default, fast) — switch to 'terser' for smaller output
    minify: 'esbuild',
    cssMinify: true,
    // Emit source maps for error tracking (disable if not needed)
    sourcemap: false,
    // Target modern browsers — reduces polyfill overhead
    target: ['es2020', 'chrome90', 'firefox90', 'safari14'],
    rollupOptions: {
      output: {
        // Content-hash file names for long-lived caching
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
        manualChunks: (id: string) => {
          // vendor-react: core React runtime and routing
          if (
            id.includes('node_modules/react/') ||
            id.includes('node_modules/react-dom/') ||
            id.includes('node_modules/react-router-dom/') ||
            id.includes('node_modules/react-router/')
          ) {
            return 'vendor-react';
          }
          // vendor-charts: charting libraries
          if (
            id.includes('node_modules/recharts/') ||
            id.includes('node_modules/chart.js/') ||
            id.includes('node_modules/react-chartjs-2/')
          ) {
            return 'vendor-charts';
          }
          // vendor-query: TanStack React Query
          if (id.includes('node_modules/@tanstack/')) {
            return 'vendor-query';
          }
          // vendor-ui: Radix UI primitives
          if (id.includes('node_modules/@radix-ui/')) {
            return 'vendor-ui';
          }
          // vendor-forms: form handling and validation
          if (
            id.includes('node_modules/react-hook-form/') ||
            id.includes('node_modules/@hookform/') ||
            id.includes('node_modules/zod/')
          ) {
            return 'vendor-forms';
          }
          // vendor-i18n: internationalisation
          if (id.includes('node_modules/i18next') || id.includes('node_modules/react-i18next')) {
            return 'vendor-i18n';
          }
        },
      },
    },
  },
});
