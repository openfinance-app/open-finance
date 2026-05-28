import { describe, it, expect } from 'vitest';
import {
    getAllowedExtensions,
    getAllowedMimeTypes,
    isFileTypeAllowed,
    formatFileSize,
    getFileIcon,
    ALLOWED_FILE_TYPES,
    MAX_FILE_SIZE,
    AttachmentEntityType,
} from './attachment';
import type { Attachment } from './attachment';

describe('attachment utilities', () => {
    describe('getAllowedExtensions', () => {
        it('returns flat array of all extensions', () => {
            const exts = getAllowedExtensions();
            expect(exts).toContain('.jpg');
            expect(exts).toContain('.pdf');
            expect(exts).toContain('.csv');
            expect(exts.length).toBeGreaterThan(5);
        });
    });

    describe('getAllowedMimeTypes', () => {
        it('returns all MIME type keys', () => {
            const mimes = getAllowedMimeTypes();
            expect(mimes).toContain('image/jpeg');
            expect(mimes).toContain('application/pdf');
            expect(mimes).toContain('text/csv');
        });
    });

    describe('isFileTypeAllowed', () => {
        it('returns true for allowed types', () => {
            expect(isFileTypeAllowed('image/png')).toBe(true);
            expect(isFileTypeAllowed('application/pdf')).toBe(true);
        });

        it('returns false for disallowed types', () => {
            expect(isFileTypeAllowed('application/zip')).toBe(false);
            expect(isFileTypeAllowed('video/mp4')).toBe(false);
        });
    });

    describe('formatFileSize', () => {
        it('returns 0 bytes for zero', () => {
            expect(formatFileSize(0)).toBe('0 bytes');
        });

        it('formats bytes', () => {
            expect(formatFileSize(500)).toBe('500.00 bytes');
        });

        it('formats KB', () => {
            expect(formatFileSize(1024)).toBe('1.00 KB');
        });

        it('formats MB', () => {
            expect(formatFileSize(1024 * 1024)).toBe('1.00 MB');
        });

        it('formats GB', () => {
            expect(formatFileSize(1024 * 1024 * 1024)).toBe('1.00 GB');
        });
    });

    describe('getFileIcon', () => {
        const makeAttachment = (overrides: Partial<Attachment>): Attachment => ({
            id: 1,
            entityType: AttachmentEntityType.TRANSACTION,
            entityId: 1,
            fileName: 'test.txt',
            fileType: 'text/plain',
            fileSize: 100,
            image: false,
            pdf: false,
            createdAt: '2024-01-01',
            ...overrides,
        });

        it('returns Image for images', () => {
            expect(getFileIcon(makeAttachment({ image: true }))).toBe('Image');
        });

        it('returns FileText for PDFs', () => {
            expect(getFileIcon(makeAttachment({ pdf: true }))).toBe('FileText');
        });

        it('returns FileText for word docs', () => {
            expect(getFileIcon(makeAttachment({ fileType: 'application/msword' }))).toBe('FileText');
        });

        it('returns Table for excel', () => {
            expect(getFileIcon(makeAttachment({ fileType: 'application/vnd.ms-excel' }))).toBe('Table');
        });

        it('returns Table for spreadsheet', () => {
            expect(getFileIcon(makeAttachment({ fileType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))).toBe('Table');
        });

        it('returns Table for csv', () => {
            expect(getFileIcon(makeAttachment({ fileType: 'text/csv' }))).toBe('Table');
        });

        it('returns File for unknown types', () => {
            expect(getFileIcon(makeAttachment({ fileType: 'text/plain' }))).toBe('File');
        });
    });

    describe('constants', () => {
        it('MAX_FILE_SIZE is 10MB', () => {
            expect(MAX_FILE_SIZE).toBe(10 * 1024 * 1024);
        });
    });
});
