import nextCoreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';
import { base } from '@davinci/eslint-config/base';
import { designRules } from '@davinci/eslint-config/design-rules';

const config = [
  { ignores: ['.next/**', 'next-env.d.ts', 'coverage/**'] },
  ...nextCoreWebVitals,
  ...nextTypescript,
  ...base,
  ...designRules,
];

export default config;
