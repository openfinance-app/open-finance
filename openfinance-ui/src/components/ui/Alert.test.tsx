import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Alert, AlertDescription } from './Alert';

describe('Alert', () => {
  it('renders children with alert role', () => {
    render(<Alert>Alert message</Alert>);
    expect(screen.getByRole('alert')).toHaveTextContent('Alert message');
  });

  it('applies default variant', () => {
    render(<Alert>Default</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('bg-background');
  });

  it('applies destructive variant', () => {
    render(<Alert variant="destructive">Error!</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('text-destructive');
  });

  it('applies success variant', () => {
    render(<Alert variant="success">OK</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('text-green-600');
  });

  it('applies warning variant', () => {
    render(<Alert variant="warning">Warn</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('text-yellow-600');
  });

  it('applies info variant', () => {
    render(<Alert variant="info">Info</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('text-blue-500');
  });

  it('passes through className', () => {
    render(<Alert className="custom-alert">Text</Alert>);
    expect(screen.getByRole('alert').className).toContain('custom-alert');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Alert ref={ref}>Ref</Alert>);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

describe('AlertDescription', () => {
  it('renders content', () => {
    render(<AlertDescription>Description text</AlertDescription>);
    expect(screen.getByText('Description text')).toBeInTheDocument();
  });

  it('passes through className', () => {
    const { container } = render(
      <AlertDescription className="desc-class">Desc</AlertDescription>
    );
    expect((container.firstChild as HTMLElement).className).toContain('desc-class');
  });
});
