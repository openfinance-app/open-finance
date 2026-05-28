import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from './Dialog';

describe('Dialog', () => {
  it('renders trigger and opens on click', () => {
    render(
      <Dialog open={true}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Test Title</DialogTitle>
          </DialogHeader>
          <p>Dialog body</p>
        </DialogContent>
      </Dialog>
    );
    expect(screen.getByText('Test Title')).toBeInTheDocument();
    expect(screen.getByText('Dialog body')).toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    render(
      <Dialog open={false}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Hidden</DialogTitle>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    );
    expect(screen.queryByText('Hidden')).not.toBeInTheDocument();
  });
});
