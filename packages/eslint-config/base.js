import js from '@eslint/js';
import tseslint from 'typescript-eslint';

/** Regras base para todo código TypeScript do monorepo. */
export const base = tseslint.config(js.configs.recommended, ...tseslint.configs.strict, {
  rules: {
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/ban-ts-comment': ['error', { 'ts-ignore': true, 'ts-nocheck': true }],
    '@typescript-eslint/consistent-type-imports': 'error',
    'no-console': ['error', { allow: ['warn', 'error'] }],
  },
});

export default base;
