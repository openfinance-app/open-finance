/**
 * Import hooks
 * Task 7.4.13: Create import service hooks
 *
 * Provides React Query hooks for import operations
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import { importService } from '@/services/importService';
import type {
  ImportProcessRequest,
  ImportSessionResponse,
  ImportSessionStatus,
  ImportTransactionDTO,
} from '@/types/import';

/**
 * Session statuses that indicate the import is done and the /review endpoint
 * is no longer accessible. Querying it in these states returns HTTP 400.
 */
const TERMINAL_STATUSES: ImportSessionStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED'];

/**
 * Start import from uploaded file
 *
 * @example
 * const startImport = useStartImport();
 * startImport.mutate({ uploadId: 'abc123', accountId: 1 });
 */
export function useStartImport(): UseMutationResult<
  ImportSessionResponse,
  Error,
  ImportProcessRequest
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, ImportProcessRequest>({
    mutationFn: data => importService.startImport(data, encryptionEnabled),
    onSuccess: data => {
      // Cache the session data
      queryClient.setQueryData(['import-sessions', data.id], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
  });
}

/**
 * Get import session by ID with optional polling
 *
 * @param sessionId - Import session ID
 * @param options - Query options including poll interval
 *
 * @example
 * // Poll every 2 seconds while status is pending
 * const { data: session } = useImportSession(sessionId, { pollInterval: 2000 });
 */
export function useImportSession(
  sessionId: number | null,
  options?: { pollInterval?: number }
): UseQueryResult<ImportSessionResponse> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportSessionResponse>({
    queryKey: ['import-sessions', sessionId, encryptionEnabled],
    queryFn: () => {
      if (!sessionId) throw new Error('Session ID is required');
      return importService.getSession(sessionId, encryptionEnabled);
    },
    enabled: !!sessionId,
    // Poll if status is PENDING or PARSING
    refetchInterval: query => {
      const data = query.state.data;
      if (!data) return false;
      const isPending = ['PENDING', 'PARSING', 'IMPORTING'].includes(data.status);
      return isPending ? options?.pollInterval || 2000 : false;
    },
    refetchIntervalInBackground: false,
  });
}

/**
 * Get transactions for review
 *
 * The /review endpoint is only available while the session has NOT yet reached
 * a terminal state (COMPLETED, FAILED, CANCELLED). Passing the current session
 * status lets this hook disable itself automatically so it never fires a 400.
 *
 * @param sessionId     - Import session ID
 * @param sessionStatus - Current session status (used to gate fetching)
 *
 * @example
 * const { data: transactions } = useImportTransactions(sessionId, session?.status);
 */
export function useImportTransactions(
  sessionId: number | null,
  sessionStatus?: ImportSessionStatus
): UseQueryResult<ImportTransactionDTO[]> {
  const isTerminal = !!sessionStatus && TERMINAL_STATUSES.includes(sessionStatus);
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportTransactionDTO[]>({
    queryKey: ['import-transactions', sessionId, encryptionEnabled],
    queryFn: () => {
      if (!sessionId) throw new Error('Session ID is required');
      return importService.getTransactions(sessionId, encryptionEnabled);
    },
    // Do not fetch if there is no session yet OR the session is in a terminal
    // state — the backend rejects /review requests for those sessions with 400.
    enabled: !!sessionId && !isTerminal,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: 1, // AI categorization is slow; avoid aggressive retries
  });
}

/**
 * Confirm import with category mappings
 *
 * @example
 * const confirmImport = useConfirmImport();
 * confirmImport.mutate({
 *   sessionId: 123,
 *   accountId: 1,
 *   categoryMappings: { 'Groceries': 5 },
 *   skipDuplicates: true
 * });
 */
export function useConfirmImport(): UseMutationResult<
  ImportSessionResponse,
  Error,
  {
    sessionId: number;
    accountId: number | null;
    categoryMappings: Record<string, number>;
    skipDuplicates: boolean;
  }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<
    ImportSessionResponse,
    Error,
    {
      sessionId: number;
      accountId: number | null;
      categoryMappings: Record<string, number>;
      skipDuplicates: boolean;
    }
  >({
    mutationFn: ({ sessionId, ...data }) =>
      importService.confirmImport(sessionId, data, encryptionEnabled),
    onSuccess: (data, variables) => {
      // Update session cache — this also causes useImportTransactions to become
      // disabled (session is now COMPLETED/FAILED), so no stale /review refetch.
      queryClient.setQueryData(['import-sessions', variables.sessionId], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
      // Do NOT invalidate import-transactions here — the session is now in a
      // terminal state, and a refetch of /review would return HTTP 400.
      // Invalidate the broader transaction & account lists so the UI reflects
      // the newly-imported data.
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });
}

/**
 * Update the account for an import session
 */
export function useUpdateAccount(): UseMutationResult<
  ImportSessionResponse,
  Error,
  { sessionId: number; accountId: number }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, { sessionId: number; accountId: number }>({
    mutationFn: ({ sessionId, accountId }) =>
      importService.updateAccount(sessionId, accountId, encryptionEnabled),
    onSuccess: (data, variables) => {
      queryClient.setQueryData(['import-sessions', variables.sessionId], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
  });
}

/**
 * Update modifying transactions back to the session
 */
export function useUpdateTransactions(): UseMutationResult<
  ImportSessionResponse,
  Error,
  { sessionId: number; transactions: ImportTransactionDTO[] }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<
    ImportSessionResponse,
    Error,
    { sessionId: number; transactions: ImportTransactionDTO[] }
  >({
    mutationFn: ({ sessionId, transactions }) =>
      importService.updateTransactions(sessionId, transactions, encryptionEnabled),
    onSuccess: (data, variables) => {
      queryClient.setQueryData(['import-sessions', variables.sessionId], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
      queryClient.invalidateQueries({ queryKey: ['import-transactions', variables.sessionId] });
    },
  });
}

/**
 * Cancel import session
 *
 * @example
 * const cancelImport = useCancelImport();
 * cancelImport.mutate(sessionId);
 */
export function useCancelImport(): UseMutationResult<ImportSessionResponse, Error, number> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, number>({
    mutationFn: sessionId => importService.cancelImport(sessionId, encryptionEnabled),
    onSuccess: (data, sessionId) => {
      // Update session cache
      queryClient.setQueryData(['import-sessions', sessionId], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
  });
}

/**
 * List all import sessions for current user
 *
 * @example
 * const { data: sessions } = useImportSessions();
 */
export function useImportSessions(): UseQueryResult<ImportSessionResponse[]> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportSessionResponse[]>({
    queryKey: ['import-sessions', encryptionEnabled],
    queryFn: () => importService.listSessions(encryptionEnabled),
    staleTime: 30 * 1000, // 30 seconds
  });
}
