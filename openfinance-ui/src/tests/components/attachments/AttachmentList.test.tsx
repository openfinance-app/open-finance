import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AttachmentList } from '@/components/attachments/AttachmentList';
import type { Attachment } from '@/types/attachment';

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
const mockUseAttachments = vi.fn();
const mockDownloadAttachment = vi.fn();
const mockDeleteAttachment = vi.fn();

vi.mock('@/hooks/useAttachments', () => ({
  useAttachments: () => mockUseAttachments(),
  useDownloadAttachment: () => ({
    downloadAttachment: mockDownloadAttachment,
    isDownloading: false,
  }),
  useDeleteAttachment: () => ({
    mutate: mockDeleteAttachment,
    isPending: false,
  }),
  useFetchAttachmentBlob: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const mockImageAttachment: Attachment = {
  id: 1,
  userId: 1,
  entityType: 'TRANSACTION',
  entityId: 10,
  fileName: 'receipt.jpg',
  fileType: 'image/jpeg',
  fileSize: 204800,
  filePath: '/attachments/receipt.jpg',
  uploadedAt: '2026-01-15T10:00:00Z',
  fileExtension: 'jpg',
  formattedFileSize: '200 KB',
  image: true,
  pdf: false,
  document: false,
};

const mockPdfAttachment: Attachment = {
  id: 2,
  userId: 1,
  entityType: 'TRANSACTION',
  entityId: 10,
  fileName: 'invoice.pdf',
  fileType: 'application/pdf',
  fileSize: 512000,
  filePath: '/attachments/invoice.pdf',
  uploadedAt: '2026-01-10T08:00:00Z',
  fileExtension: 'pdf',
  formattedFileSize: '500 KB',
  image: false,
  pdf: true,
  document: false,
};

const mockDocAttachment: Attachment = {
  id: 3,
  userId: 1,
  entityType: 'TRANSACTION',
  entityId: 10,
  fileName: 'report.docx',
  fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  fileSize: 102400,
  filePath: '/attachments/report.docx',
  uploadedAt: '2026-01-05T12:00:00Z',
  fileExtension: 'docx',
  formattedFileSize: '100 KB',
  image: false,
  pdf: false,
  document: true,
};

describe('AttachmentList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  describe('Loading state', () => {
    it('should render a loading spinner while fetching', () => {
      mockUseAttachments.mockReturnValue({ data: undefined, isLoading: true });
      renderWithProviders(
        <AttachmentList entityType="TRANSACTION" entityId={10} />
      );
      // The spinner uses Loader2; check that empty list is not rendered
      expect(screen.queryByText(/Attachments/)).not.toBeInTheDocument();
    });
  });

  describe('Empty state', () => {
    it('should render nothing when attachments array is empty', () => {
      mockUseAttachments.mockReturnValue({ data: [], isLoading: false });
      const { container } = renderWithProviders(
        <AttachmentList entityType="TRANSACTION" entityId={10} />
      );
      expect(container.firstChild).toBeNull();
    });

    it('should render nothing when data is undefined', () => {
      mockUseAttachments.mockReturnValue({ data: undefined, isLoading: false });
      const { container } = renderWithProviders(
        <AttachmentList entityType="TRANSACTION" entityId={10} />
      );
      expect(container.firstChild).toBeNull();
    });
  });

  describe('With attachments', () => {
    beforeEach(() => {
      mockUseAttachments.mockReturnValue({
        data: [mockImageAttachment, mockPdfAttachment, mockDocAttachment],
        isLoading: false,
      });
    });

    it('should display attachment count in heading', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('Attachments (3)')).toBeInTheDocument();
    });

    it('should render each attachment file name', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('receipt.jpg')).toBeInTheDocument();
      expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
      expect(screen.getByText('report.docx')).toBeInTheDocument();
    });

    it('should render formatted file sizes', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('200 KB')).toBeInTheDocument();
      expect(screen.getByText('500 KB')).toBeInTheDocument();
      expect(screen.getByText('100 KB')).toBeInTheDocument();
    });

    it('should display Image badge for image attachments', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('Image')).toBeInTheDocument();
    });

    it('should display PDF badge for pdf attachments', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('PDF')).toBeInTheDocument();
    });

    it('should show upload date when showUploadDate is true (default)', () => {
      renderWithProviders(
        <AttachmentList entityType="TRANSACTION" entityId={10} showUploadDate={true} />
      );
      // formatDistanceToNow will produce some relative time text
      const dateElements = document.querySelectorAll('[class*="text-xs text-text-secondary"]');
      expect(dateElements.length).toBeGreaterThan(0);
    });

    it('should render description when present', () => {
      const withDescription: Attachment = {
        ...mockDocAttachment,
        id: 4,
        description: 'Important document',
      };
      mockUseAttachments.mockReturnValue({
        data: [withDescription],
        isLoading: false,
      });
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByText('Important document')).toBeInTheDocument();
    });
  });

  describe('Download', () => {
    beforeEach(() => {
      mockUseAttachments.mockReturnValue({
        data: [mockPdfAttachment],
        isLoading: false,
      });
    });

    it('should call downloadAttachment with correct args when download button is clicked', async () => {
      mockDownloadAttachment.mockResolvedValue(undefined);
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);

      const downloadButtons = screen.getAllByTitle('Download');
      fireEvent.click(downloadButtons[0]);

      await waitFor(() => {
        expect(mockDownloadAttachment).toHaveBeenCalledWith(2, 'invoice.pdf');
      });
    });
  });

  describe('Delete', () => {
    beforeEach(() => {
      mockUseAttachments.mockReturnValue({
        data: [mockDocAttachment],
        isLoading: false,
      });
    });

    it('should open confirmation dialog when delete button is clicked', async () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);

      const deleteButtons = screen.getAllByTitle('Delete');
      fireEvent.click(deleteButtons[0]);

      await waitFor(() => {
        // Confirmation dialog should appear
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
    });

    it('should call deleteAttachment mutation after confirming delete', async () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);

      const deleteButtons = screen.getAllByTitle('Delete');
      fireEvent.click(deleteButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Find and click the confirm button in the dialog
      const confirmButton = screen.getByRole('button', { name: /delete|confirm|yes/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        expect(mockDeleteAttachment).toHaveBeenCalledWith(3);
      });
    });
  });

  describe('Image preview', () => {
    beforeEach(() => {
      mockUseAttachments.mockReturnValue({
        data: [mockImageAttachment],
        isLoading: false,
      });
    });

    it('should show Eye preview button for image attachments', () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);
      expect(screen.getByTitle('Preview image')).toBeInTheDocument();
    });

    it('should open image preview modal when Eye button is clicked', async () => {
      renderWithProviders(<AttachmentList entityType="TRANSACTION" entityId={10} />);

      const previewButton = screen.getByTitle('Preview image');
      fireEvent.click(previewButton);

      await waitFor(() => {
        // The ImagePreviewModal should be mounted
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
    });
  });

  describe('Custom class name', () => {
    it('should apply custom className to the card', () => {
      mockUseAttachments.mockReturnValue({
        data: [mockDocAttachment],
        isLoading: false,
      });
      const { container } = renderWithProviders(
        <AttachmentList entityType="TRANSACTION" entityId={10} className="custom-class" />
      );
      expect(container.querySelector('.custom-class')).toBeInTheDocument();
    });
  });
});
