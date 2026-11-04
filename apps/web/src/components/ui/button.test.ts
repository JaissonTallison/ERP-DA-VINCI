import { describe, expect, it } from 'vitest';
import { buttonClasses } from './button';

describe('buttonClasses', () => {
  it('usa a variante primária por padrão, com altura e cantos do Design System', () => {
    const classes = buttonClasses();
    expect(classes).toContain('bg-primary');
    expect(classes).toContain('h-field');
    expect(classes).toContain('rounded-md');
  });

  it('variante secundária tem borda e fundo de superfície', () => {
    expect(buttonClasses('secondary')).toContain('border-border');
    expect(buttonClasses('secondary')).not.toContain('bg-primary');
  });
});
