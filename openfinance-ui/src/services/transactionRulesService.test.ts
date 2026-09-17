/**
 * Tests for Transaction Rules Service
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from './apiClient';
import {
  getRules,
  getRule,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
} from './transactionRulesService';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

import apiClient from './apiClient';

// Mock apiClient
vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('transactionRulesService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockRule: TransactionRule = {
    id: 1,
    name: 'Test Rule',
    priority: 0,
    isEnabled: true,
    conditions: [],
    actions: [],
  };

  const mockRuleRequest: TransactionRuleRequest = {
    name: 'Test Rule',
    priority: 0,
    isEnabled: true,
    conditions: [],
    actions: [],
  };

  describe('getRules', () => {
    it('should fetch all rules successfully', async () => {
      const mockResponse = { data: [mockRule] };
      mockApiClient.get.mockResolvedValue(mockResponse);

      const result = await getRules();

      expect(mockApiClient.get).toHaveBeenCalledWith('/transaction-rules');
      expect(result).toEqual([mockRule]);
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.get.mockRejectedValue(error);

      await expect(getRules()).rejects.toThrow('API Error');
    });
  });

  describe('getRule', () => {
    it('should fetch a single rule by ID', async () => {
      const mockResponse = { data: mockRule };
      mockApiClient.get.mockResolvedValue(mockResponse);

      const result = await getRule(1);

      expect(mockApiClient.get).toHaveBeenCalledWith('/transaction-rules/1');
      expect(result).toEqual(mockRule);
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.get.mockRejectedValue(error);

      await expect(getRule(1)).rejects.toThrow('API Error');
    });
  });

  describe('createRule', () => {
    it('should create a new rule', async () => {
      const mockResponse = { data: mockRule };
      mockApiClient.post.mockResolvedValue(mockResponse);

      const result = await createRule(mockRuleRequest);

      expect(mockApiClient.post).toHaveBeenCalledWith('/transaction-rules', mockRuleRequest);
      expect(result).toEqual(mockRule);
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.post.mockRejectedValue(error);

      await expect(createRule(mockRuleRequest)).rejects.toThrow('API Error');
    });
  });

  describe('updateRule', () => {
    it('should update an existing rule', async () => {
      const mockResponse = { data: mockRule };
      mockApiClient.put.mockResolvedValue(mockResponse);

      const result = await updateRule(1, mockRuleRequest);

      expect(mockApiClient.put).toHaveBeenCalledWith('/transaction-rules/1', mockRuleRequest);
      expect(result).toEqual(mockRule);
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.put.mockRejectedValue(error);

      await expect(updateRule(1, mockRuleRequest)).rejects.toThrow('API Error');
    });
  });

  describe('deleteRule', () => {
    it('should delete a rule by ID', async () => {
      mockApiClient.delete.mockResolvedValue(undefined);

      await expect(deleteRule(1)).resolves.toBeUndefined();

      expect(mockApiClient.delete).toHaveBeenCalledWith('/transaction-rules/1');
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.delete.mockRejectedValue(error);

      await expect(deleteRule(1)).rejects.toThrow('API Error');
    });
  });

  describe('toggleRule', () => {
    it("should toggle a rule's enabled state", async () => {
      const mockResponse = { data: mockRule };
      mockApiClient.patch.mockResolvedValue(mockResponse);

      const result = await toggleRule(1);

      expect(mockApiClient.patch).toHaveBeenCalledWith('/transaction-rules/1/toggle');
      expect(result).toEqual(mockRule);
    });

    it('should handle API errors', async () => {
      const error = new Error('API Error');
      mockApiClient.patch.mockRejectedValue(error);

      await expect(toggleRule(1)).rejects.toThrow('API Error');
    });
  });
});
