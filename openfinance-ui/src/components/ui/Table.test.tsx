import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
} from './Table';

describe('Table', () => {
  function renderTable() {
    return render(
      <Table>
        <TableCaption>Test Caption</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Value</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>Item 1</TableCell>
            <TableCell>100</TableCell>
          </TableRow>
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell>Total</TableCell>
            <TableCell>100</TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    );
  }

  it('renders a table element', () => {
    renderTable();
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('renders header cells', () => {
    renderTable();
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Value')).toBeInTheDocument();
  });

  it('renders body cells', () => {
    renderTable();
    expect(screen.getByText('Item 1')).toBeInTheDocument();
  });

  it('renders caption', () => {
    renderTable();
    expect(screen.getByText('Test Caption')).toBeInTheDocument();
  });

  it('renders footer', () => {
    renderTable();
    expect(screen.getByText('Total')).toBeInTheDocument();
  });

  it('applies className to Table', () => {
    const { container } = render(
      <Table className="custom-table">
        <TableBody>
          <TableRow>
            <TableCell>A</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    );
    const table = container.querySelector('table');
    expect(table?.className).toContain('custom-table');
  });

  it('forwards ref on TableRow', () => {
    const ref = { current: null as HTMLTableRowElement | null };
    render(
      <Table>
        <TableBody>
          <TableRow ref={ref}>
            <TableCell>A</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    );
    expect(ref.current).toBeInstanceOf(HTMLTableRowElement);
  });
});
