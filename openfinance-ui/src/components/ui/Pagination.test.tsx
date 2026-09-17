import { screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  const defaultProps = {
    currentPage: 0,
    totalPages: 5,
    pageSize: 10,
    totalElements: 50,
    onPageChange: vi.fn(),
    onPageSizeChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders nothing when totalElements is 0', () => {
      renderWithProviders(<Pagination {...defaultProps} totalElements={0} />);
      expect(screen.queryByText(/Showing/)).not.toBeInTheDocument();
    });

    it('renders pagination controls when there are elements', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      const showingText = screen.getByText(/Showing/);
      expect(showingText).toBeInTheDocument();
      // Verify the page numbers exist
      expect(screen.getByLabelText('Page 1 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 2 of 5')).toBeInTheDocument();
    });

    it('displays correct showing range for first page', () => {
      const { container } = renderWithProviders(<Pagination {...defaultProps} />);
      const showingDiv = container.querySelector('.text-text-secondary');
      expect(showingDiv).toBeTruthy();
      expect(showingDiv?.textContent).toContain('Showing');
      expect(showingDiv?.textContent).toContain('1');
      expect(showingDiv?.textContent).toContain('10');
      expect(showingDiv?.textContent).toContain('50');
    });

    it('displays correct showing range for middle page', () => {
      const { container } = renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      const showingDiv = container.querySelector('.text-text-secondary');
      expect(showingDiv?.textContent).toContain('21');
      expect(showingDiv?.textContent).toContain('30');
      expect(showingDiv?.textContent).toContain('50');
    });

    it('displays correct showing range for last page', () => {
      const { container } = renderWithProviders(<Pagination {...defaultProps} currentPage={4} />);
      const showingDiv = container.querySelector('.text-text-secondary');
      expect(showingDiv?.textContent).toContain('41');
      expect(showingDiv?.textContent).toContain('50');
    });

    it('displays correct showing range when last page has fewer items', () => {
      const { container } = renderWithProviders(
        <Pagination {...defaultProps} totalElements={45} />
      );
      const showingDiv = container.querySelector('.text-text-secondary');
      expect(showingDiv?.textContent).toContain('1');
      expect(showingDiv?.textContent).toContain('10');
      expect(showingDiv?.textContent).toContain('45');
    });
  });

  describe('Page Navigation', () => {
    it('renders all page numbers when total pages <= 7', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      expect(screen.getByLabelText('Page 1 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 2 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 3 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 4 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 5 of 5')).toBeInTheDocument();
    });

    it('renders ellipsis for many pages', () => {
      renderWithProviders(<Pagination {...defaultProps} totalPages={10} currentPage={5} />);
      const ellipses = screen.getAllByText('...');
      expect(ellipses.length).toBeGreaterThan(0);
    });

    it('calls onPageChange when page number is clicked', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      fireEvent.click(screen.getByLabelText('Page 3 of 5'));
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(2);
    });

    it('calls onPageChange when first page button is clicked', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      fireEvent.click(screen.getByLabelText('First page'));
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(0);
    });

    it('calls onPageChange when previous page button is clicked', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      fireEvent.click(screen.getByLabelText('Previous page'));
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(1);
    });

    it('calls onPageChange when next page button is clicked', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      fireEvent.click(screen.getByLabelText('Next page'));
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(3);
    });

    it('calls onPageChange when last page button is clicked', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      fireEvent.click(screen.getByLabelText('Last page'));
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(4);
    });
  });

  describe('Page Size Selection', () => {
    it('renders page size selector with default options', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      const select = screen.getByLabelText('Rows per page');
      expect(select).toBeInTheDocument();
      expect(select).toHaveValue('10');
    });

    it('renders custom page size options', () => {
      renderWithProviders(<Pagination {...defaultProps} pageSizeOptions={[5, 15, 25]} />);
      const select = screen.getByLabelText('Rows per page') as HTMLSelectElement;
      expect(select).toBeInTheDocument();
      // Check that the select has the correct options
      const options = Array.from(select.options).map(opt => opt.value);
      expect(options).toContain('5');
      expect(options).toContain('15');
      expect(options).toContain('25');
    });

    it('calls onPageSizeChange when page size is changed', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      const select = screen.getByLabelText('Rows per page');
      fireEvent.change(select, { target: { value: '20' } });
      expect(defaultProps.onPageSizeChange).toHaveBeenCalledWith(20);
    });
  });

  describe('Button States', () => {
    it('disables first page button on first page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={0} />);
      expect(screen.getByLabelText('First page')).toBeDisabled();
    });

    it('disables previous page button on first page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={0} />);
      expect(screen.getByLabelText('Previous page')).toBeDisabled();
    });

    it('disables next page button on last page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={4} />);
      expect(screen.getByLabelText('Next page')).toBeDisabled();
    });

    it('disables last page button on last page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={4} />);
      expect(screen.getByLabelText('Last page')).toBeDisabled();
    });

    it('enables all buttons on middle page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      expect(screen.getByLabelText('First page')).not.toBeDisabled();
      expect(screen.getByLabelText('Previous page')).not.toBeDisabled();
      expect(screen.getByLabelText('Next page')).not.toBeDisabled();
      expect(screen.getByLabelText('Last page')).not.toBeDisabled();
    });
  });

  describe('Accessibility', () => {
    it('has correct ARIA labels for navigation buttons', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      expect(screen.getByLabelText('First page')).toBeInTheDocument();
      expect(screen.getByLabelText('Previous page')).toBeInTheDocument();
      expect(screen.getByLabelText('Next page')).toBeInTheDocument();
      expect(screen.getByLabelText('Last page')).toBeInTheDocument();
      expect(screen.getByLabelText('Rows per page')).toBeInTheDocument();
    });

    it('has correct ARIA labels for page numbers', () => {
      renderWithProviders(<Pagination {...defaultProps} />);
      expect(screen.getByLabelText('Page 1 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 2 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 3 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 4 of 5')).toBeInTheDocument();
      expect(screen.getByLabelText('Page 5 of 5')).toBeInTheDocument();
    });

    it('sets aria-current on current page', () => {
      renderWithProviders(<Pagination {...defaultProps} currentPage={2} />);
      expect(screen.getByLabelText('Page 3 of 5')).toHaveAttribute('aria-current', 'page');
      expect(screen.getByLabelText('Page 1 of 5')).not.toHaveAttribute('aria-current');
    });
  });

  describe('Edge Cases', () => {
    it('handles single page correctly', () => {
      renderWithProviders(<Pagination {...defaultProps} totalPages={1} />);
      expect(screen.getByLabelText('First page')).toBeDisabled();
      expect(screen.getByLabelText('Previous page')).toBeDisabled();
      expect(screen.getByLabelText('Next page')).toBeDisabled();
      expect(screen.getByLabelText('Last page')).toBeDisabled();
    });

    it('handles large number of pages', () => {
      renderWithProviders(<Pagination {...defaultProps} totalPages={100} currentPage={50} />);
      // Should show ellipsis and limited page numbers
      const ellipses = screen.getAllByText('...');
      expect(ellipses.length).toBeGreaterThan(0);
      expect(screen.getByLabelText('Page 1 of 100')).toBeInTheDocument(); // First page
      expect(screen.getByLabelText('Page 100 of 100')).toBeInTheDocument(); // Last page
    });

    it('shows correct range when page size changes', () => {
      renderWithProviders(<Pagination {...defaultProps} pageSize={20} currentPage={1} />);
      expect(screen.getByText(/Showing/)).toBeInTheDocument();
      expect(screen.getByText('21')).toBeInTheDocument();
      expect(screen.getByText('40')).toBeInTheDocument();
    });
  });
});
