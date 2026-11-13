/**
 * Payee-related types
 */

export interface Payee {
  id: number;
  name: string;
  logo?: string;
  category?: string;
  categoryId?: number;
  categoryName?: string;
  isSystem: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
  transactionCount?: number;
  totalAmount?: number;
}

export interface PayeeRequest {
  name: string;
  logo?: string;
  categoryId?: number | null;
}
