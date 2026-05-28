import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AppLogo } from './AppLogo';

describe('AppLogo', () => {
  it('renders the SVG logo', () => {
    render(<AppLogo />);
    expect(screen.getByRole('img', { name: /open finance logo/i })).toBeInTheDocument();
  });

  it('renders wordmark text by default', () => {
    render(<AppLogo />);
    expect(screen.getByText('Open')).toBeInTheDocument();
    expect(screen.getByText('Finance')).toBeInTheDocument();
  });

  it('hides wordmark when showText=false', () => {
    render(<AppLogo showText={false} />);
    expect(screen.queryByText('Open')).not.toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = render(<AppLogo className="custom-logo" />);
    expect((container.firstChild as HTMLElement).className).toContain('custom-logo');
  });

  it('renders SVG with custom size', () => {
    render(<AppLogo size={48} />);
    const svg = screen.getByRole('img');
    expect(svg).toHaveAttribute('width', '48');
    expect(svg).toHaveAttribute('height', '48');
  });
});
