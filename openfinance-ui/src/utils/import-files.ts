import type { ImportSourceFile } from '@/types/import';

export function readImportFiles(metadata?: string): ImportSourceFile[] {
  try {
    const parsed: unknown = JSON.parse(metadata || '{}');
    if (!parsed || typeof parsed !== 'object' || !('importFiles' in parsed)) return [];
    if (!Array.isArray(parsed.importFiles)) return [];
    return parsed.importFiles.filter(
      (file): file is ImportSourceFile =>
        file && typeof file.uploadId === 'string' && typeof file.fileName === 'string'
    );
  } catch {
    return [];
  }
}
