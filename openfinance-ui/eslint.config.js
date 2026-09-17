import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results']),

  // ── Main source and test files ───────────────────────────────────────────────
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      // ── New react-hooks v7 opinionated rules: downgrade to warn ───────────────
      // set-state-in-effect: legitimate use in reset-on-prop-change patterns
      'react-hooks/set-state-in-effect': 'warn',
      // preserve-manual-memoization / incompatible-library: React Compiler rules,
      // not applicable to this project (no React Compiler in use)
      'react-hooks/preserve-manual-memoization': 'warn',
      'react-hooks/incompatible-library': 'warn',
      // static-components: creating components inside render is occasionally needed
      'react-hooks/static-components': 'warn',
      // use-memo: new rule about useMemo argument form, not always applicable
      'react-hooks/use-memo': 'warn',
      // immutability: reassigning variables in async functions — used in hooks
      'react-hooks/immutability': 'warn',

      // ── react-refresh: context files legitimately export providers + hooks ─────
      // Downgrade from error to warn; these co-exports are intentional.
      'react-refresh/only-export-components': 'warn',

      // ── TypeScript strictness: warn, not error ────────────────────────────────
      // any is legitimately needed in Chart.js callbacks and test mocks
      '@typescript-eslint/no-explicit-any': 'warn',
      // Unused vars: warn with standard ignore patterns for _-prefixed names
      '@typescript-eslint/no-unused-vars': [
        'warn',
        {
          varsIgnorePattern: '^_',
          argsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      // Empty interfaces used in shadcn/ui components for extensibility
      '@typescript-eslint/no-empty-object-type': 'warn',
    },
  },

  // ── Playwright e2e specs and root-level spec files ───────────────────────────
  // Playwright tests use different patterns (unused locators for future steps,
  // let vs const for reassignable locators) that don't apply to production code.
  {
    files: ['e2e/**/*.{ts,tsx}', '*.spec.ts'],
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
      'prefer-const': 'off',
    },
  },
])
