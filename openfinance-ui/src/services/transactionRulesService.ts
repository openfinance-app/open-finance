/**
 * Transaction Rules Service
 *
 * API client functions for transaction rule CRUD operations.
 * Requirement: REQ-TR-5
 */
import apiClient from './apiClient';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

const BASE_URL = '/transaction-rules';

/**
 * Fetch all transaction rules for the authenticated user.
 */
export async function getRules(): Promise<TransactionRule[]> {
  const response = await apiClient.get<TransactionRule[]>(BASE_URL);
  return response.data;
}

/**
 * Fetch a single transaction rule by ID.
 */
export async function getRule(id: number): Promise<TransactionRule> {
  const response = await apiClient.get<TransactionRule>(`${BASE_URL}/${id}`);
  return response.data;
}

/**
 * Create a new transaction rule.
 */
export async function createRule(data: TransactionRuleRequest): Promise<TransactionRule> {
  const response = await apiClient.post<TransactionRule>(BASE_URL, data);
  return response.data;
}

/**
 * Update an existing transaction rule.
 */
export async function updateRule(
  id: number,
  data: TransactionRuleRequest
): Promise<TransactionRule> {
  const response = await apiClient.put<TransactionRule>(`${BASE_URL}/${id}`, data);
  return response.data;
}

/**
 * Delete a transaction rule by ID.
 */
export async function deleteRule(id: number): Promise<void> {
  await apiClient.delete(`${BASE_URL}/${id}`);
}

/**
 * Toggle the enabled/disabled state of a transaction rule.
 */
export async function toggleRule(id: number): Promise<TransactionRule> {
  const response = await apiClient.patch<TransactionRule>(`${BASE_URL}/${id}/toggle`);
  return response.data;
}
