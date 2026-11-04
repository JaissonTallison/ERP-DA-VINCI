import type { ButtonHTMLAttributes } from 'react';

/** Variantes de botão do Design System (docs/development/DESIGN_SYSTEM.md): 36 px de altura, cantos de 8 px. */
const VARIANTS = {
  primary: 'bg-primary text-surface hover:bg-primary-hover',
  secondary: 'border border-border bg-surface text-brand hover:bg-canvas',
} as const;

export type ButtonVariant = keyof typeof VARIANTS;

/** Classes do botão, para usar também em links com aparência de botão. */
export function buttonClasses(variant: ButtonVariant = 'primary'): string {
  return `inline-flex h-field items-center justify-center rounded-md px-4 text-sm font-semibold ${VARIANTS[variant]}`;
}

export function Button({
  variant = 'primary',
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return <button className={`${buttonClasses(variant)} ${className ?? ''}`.trim()} {...props} />;
}
