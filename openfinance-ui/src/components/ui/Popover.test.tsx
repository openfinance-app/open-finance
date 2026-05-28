import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Popover, PopoverTrigger, PopoverContent } from './Popover';

describe('Popover', () => {
  it('renders trigger', () => {
    render(
      <Popover>
        <PopoverTrigger>Click me</PopoverTrigger>
        <PopoverContent>Content</PopoverContent>
      </Popover>
    );
    expect(screen.getByText('Click me')).toBeInTheDocument();
  });

  it('shows content when open', () => {
    render(
      <Popover open={true}>
        <PopoverTrigger>Trigger</PopoverTrigger>
        <PopoverContent>Popover body</PopoverContent>
      </Popover>
    );
    expect(screen.getByText('Popover body')).toBeInTheDocument();
  });
});
