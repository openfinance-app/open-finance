/**
 * Transaction Rules React Query Hooks
 *
 * Provides hooks for all transaction rule CRUD operations using React Query.
 * Requirement: REQ-TR-6
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getRules,
  getRule,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
} from '@/services/transactionRulesService';
import type { TransactionRuleRequest } from '@/types/transactionRules';

/** Query key constants for cache management */
const RULES_KEY = ['transaction-rules'] as const;
const ruleKey = (id: number) => [...RULES_KEY, id] as const;

/**
 * Fetch all transaction rules for the authenticated user.
 * Requirement: REQ-TR-6
 */
export function useTransactionRules() {
  return useQuery({
    queryKey: RULES_KEY,
    queryFn: getRules,
  });
}

/**
 * Fetch a single transaction rule by ID.
 * Requirement: REQ-TR-6
 */
export function useTransactionRule(id: number) {
  return useQuery({
    queryKey: ruleKey(id),
    queryFn: () => getRule(id),
    enabled: id > 0,
  });
}

/**
 * Create a new transaction rule.
 * Invalidates the rules list on success.
 * Requirement: REQ-TR-6
 */
export function useCreateRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: TransactionRuleRequest) => createRule(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['history'] });
      queryClient.invalidateQueries({ queryKey: ['session-history-exists'] });
      queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

/**
 * Update an existing transaction rule.
 * Invalidates both the list and the individual rule cache on success.
 * Requirement: REQ-TR-6
 */
export function useUpdateRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: TransactionRuleRequest }) =>
      updateRule(id, data),
    onSuccess: (_result, { id }) => {
      queryClient.invalidateQueries({ queryKey: RULES_KEY });
      queryClient.invalidateQueries({ queryKey: ruleKey(id) });
    },
  });
}

/**
 * Delete a transaction rule by ID.
 * Invalidates the rules list on success.
 * Requirement: REQ-TR-6
 */
export function useDeleteRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteRule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

/**
 * Toggle the enabled/disabled state of a transaction rule.
 * Invalidates the rules list on success.
 * Requirement: REQ-TR-6
 */
export function useToggleRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => toggleRule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}
