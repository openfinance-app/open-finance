/**
 * TypeScript types for Transaction Rules feature.
 *
 * Requirement: REQ-TR-6
 */

/**
 * Fields of an imported transaction that can be evaluated by a rule condition.
 * Requirement: REQ-TR-2.1
 */
export type RuleConditionField = 'DESCRIPTION' | 'PAYEE' | 'MEMO' | 'AMOUNT' | 'TRANSACTION_TYPE';

/**
 * Comparison operators for rule conditions.
 * Requirement: REQ-TR-2.2
 */
export type RuleConditionOperator =
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GREATER_THAN'
  | 'LESS_THAN'
  | 'GREATER_OR_EQUAL'
  | 'LESS_OR_EQUAL';

/**
 * Types of actions that can be applied when a rule matches.
 * Requirement: REQ-TR-3.2
 */
export type RuleActionType =
  | 'SET_CATEGORY'
  | 'SET_PAYEE'
  | 'ADD_TAG'
  | 'SET_DESCRIPTION'
  | 'SET_AMOUNT'
  | 'ADD_SPLIT'
  | 'SKIP_TRANSACTION';

/**
 * A single condition in a transaction rule.
 * Requirement: REQ-TR-2
 */
export interface RuleCondition {
  id?: number;
  field: RuleConditionField;
  operator: RuleConditionOperator;
  value: string;
  sortOrder: number;
}

/**
 * A single action in a transaction rule.
 * Requirement: REQ-TR-3
 */
export interface RuleAction {
  id?: number;
  actionType: RuleActionType;
  actionValue?: string;
  actionValue2?: string;
  actionValue3?: string;
  sortOrder: number;
}

/**
 * A fully populated transaction rule returned by the API.
 * Requirement: REQ-TR-1.1
 */
export interface TransactionRule {
  id: number;
  name: string;
  priority: number;
  isEnabled: boolean;
  conditionMatch: 'AND' | 'OR';
  conditions: RuleCondition[];
  actions: RuleAction[];
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Request payload for creating or updating a transaction rule.
 * Requirement: REQ-TR-5.1
 */
export interface TransactionRuleRequest {
  name: string;
  priority: number;
  isEnabled: boolean;
  conditionMatch: 'AND' | 'OR';
  conditions: Omit<RuleCondition, 'id'>[];
  actions: Omit<RuleAction, 'id'>[];
}
