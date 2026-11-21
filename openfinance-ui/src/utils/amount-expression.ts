import type Decimal from 'decimal.js';
import { ExactDecimal } from '@/utils/exact-decimal';

/** Evaluate the four arithmetic operators with the same number notation as the field. */
export function evaluateAmountExpression(
  input: string,
  parseOperand: (text: string) => string | null
): string | null {
  // Bound both work and parenthesis nesting for pasted expressions.
  if (input.length > 512) return null;
  const tokens = input
    .split(/([+*/()-])/)
    .map(token => token.trim())
    .filter(Boolean);
  let index = 0;

  function factor(): Decimal {
    const sign = tokens[index];
    if (sign === '+' || sign === '-') index++;
    const token = tokens[index++];
    let value: Decimal;
    if (token === '(') {
      value = sum();
      if (tokens[index++] !== ')') throw new Error('Missing closing parenthesis');
    } else {
      const operand = token && parseOperand(token);
      if (!operand) throw new Error('Expected a number');
      value = new ExactDecimal(operand);
    }
    return sign === '-' ? value.negated() : value;
  }

  function product(): Decimal {
    let value = factor();
    while (tokens[index] === '*' || tokens[index] === '/') {
      const operator = tokens[index++];
      const operand = factor();
      if (operator === '/' && operand.isZero()) throw new Error('Division by zero');
      value = operator === '*' ? value.times(operand) : value.dividedBy(operand);
    }
    return value;
  }

  function sum(): Decimal {
    let value = product();
    while (tokens[index] === '+' || tokens[index] === '-') {
      const operator = tokens[index++];
      const operand = product();
      value = operator === '+' ? value.plus(operand) : value.minus(operand);
    }
    return value;
  }

  try {
    const result = sum();
    if (index !== tokens.length || !result.isFinite()) return null;
    return result.toDecimalPlaces(18).toFixed();
  } catch {
    return null;
  }
}
