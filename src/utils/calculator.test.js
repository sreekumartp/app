import { evaluateExpression, validateExpression, formatNumber } from './calculator';

describe('Calculator Engine Tests', () => {
  describe('Basic Arithmetic', () => {
    test('addition', () => {
      expect(evaluateExpression('2+2')).toBe('4');
      expect(evaluateExpression('10+5')).toBe('15');
    });

    test('subtraction', () => {
      expect(evaluateExpression('10-5')).toBe('5');
      expect(evaluateExpression('5-10')).toBe('-5');
    });

    test('multiplication', () => {
      expect(evaluateExpression('3×4')).toBe('12');
      expect(evaluateExpression('5*6')).toBe('30');
    });

    test('division', () => {
      expect(evaluateExpression('10÷2')).toBe('5');
      expect(evaluateExpression('15/3')).toBe('5');
    });

    test('division by zero', () => {
      expect(() => evaluateExpression('5÷0')).toThrow();
    });
  });

  describe('Order of Operations', () => {
    test('follows PEMDAS', () => {
      expect(evaluateExpression('2+3×4')).toBe('14');
      expect(evaluateExpression('(2+3)×4')).toBe('20');
      expect(evaluateExpression('10-2×3')).toBe('4');
    });

    test('handles parentheses', () => {
      expect(evaluateExpression('(5+3)×(4-2)')).toBe('16');
      expect(evaluateExpression('((2+3)×4)-5')).toBe('15');
    });
  });

  describe('Trigonometric Functions', () => {
    test('sin in degrees', () => {
      const result = parseFloat(evaluateExpression('sin(30)', true));
      expect(result).toBeCloseTo(0.5, 5);
    });

    test('cos in degrees', () => {
      const result = parseFloat(evaluateExpression('cos(60)', true));
      expect(result).toBeCloseTo(0.5, 5);
    });

    test('tan in degrees', () => {
      const result = parseFloat(evaluateExpression('tan(45)', true));
      expect(result).toBeCloseTo(1, 5);
    });

    test('sin in radians', () => {
      const result = parseFloat(evaluateExpression('sin(pi/2)', false));
      expect(result).toBeCloseTo(1, 5);
    });
  });

  describe('Scientific Functions', () => {
    test('square root', () => {
      expect(evaluateExpression('sqrt(16)')).toBe('4');
      expect(evaluateExpression('sqrt(25)')).toBe('5');
    });

    test('logarithm', () => {
      const result = parseFloat(evaluateExpression('log(100)'));
      expect(result).toBeCloseTo(2, 5);
    });

    test('natural log', () => {
      const result = parseFloat(evaluateExpression('ln(e)'));
      expect(result).toBeCloseTo(1, 5);
    });

    test('absolute value', () => {
      expect(evaluateExpression('abs(-5)')).toBe('5');
      expect(evaluateExpression('abs(5)')).toBe('5');
    });

    test('factorial', () => {
      expect(evaluateExpression('factorial(5)')).toBe('120');
      expect(evaluateExpression('factorial(0)')).toBe('1');
    });

    test('factorial error for negative', () => {
      expect(() => evaluateExpression('factorial(-5)')).toThrow();
    });
  });

  describe('Constants', () => {
    test('pi constant', () => {
      const result = parseFloat(evaluateExpression('pi'));
      expect(result).toBeCloseTo(Math.PI, 10);
    });

    test('e constant', () => {
      const result = parseFloat(evaluateExpression('e'));
      expect(result).toBeCloseTo(Math.E, 10);
    });
  });

  describe('Percentage', () => {
    test('percentage calculation', () => {
      expect(evaluateExpression('50%')).toBe('0.5');
      expect(evaluateExpression('100×25%')).toBe('25');
    });
  });

  describe('Power Operations', () => {
    test('power', () => {
      expect(evaluateExpression('2^3')).toBe('8');
      expect(evaluateExpression('10^2')).toBe('100');
    });
  });

  describe('Error Handling', () => {
    test('empty expression', () => {
      expect(() => evaluateExpression('')).toThrow('Empty expression');
    });

    test('invalid expression', () => {
      expect(() => evaluateExpression('2++3')).toThrow();
    });

    test('unmatched parentheses', () => {
      expect(() => evaluateExpression('(2+3')).toThrow();
    });
  });

  describe('Validation', () => {
    test('validates correct expressions', () => {
      expect(validateExpression('2+2')).toBe(true);
      expect(validateExpression('sin(30)')).toBe(true);
    });

    test('invalidates incorrect expressions', () => {
      expect(validateExpression('2++')).toBe(false);
      expect(validateExpression('')).toBe(false);
    });
  });

  describe('Number Formatting', () => {
    test('formats large numbers', () => {
      const result = formatNumber(123456789012);
      expect(result).toContain('e');
    });

    test('formats small numbers', () => {
      const result = formatNumber(0.0000001);
      expect(result).toContain('e');
    });

    test('formats regular numbers', () => {
      expect(formatNumber(123.456)).toBe('123.456');
    });
  });

  describe('Complex Expressions', () => {
    test('complex calculation 1', () => {
      const result = parseFloat(evaluateExpression('(2+3)×sqrt(16)÷2'));
      expect(result).toBeCloseTo(10, 5);
    });

    test('complex calculation 2', () => {
      const result = parseFloat(evaluateExpression('sin(30)+cos(60)', true));
      expect(result).toBeCloseTo(1, 5);
    });

    test('complex calculation 3', () => {
      expect(evaluateExpression('factorial(5)÷10')).toBe('12');
    });
  });

  describe('Hyperbolic Functions', () => {
    test('sinh', () => {
      const result = parseFloat(evaluateExpression('sinh(0)'));
      expect(result).toBeCloseTo(0, 5);
    });

    test('cosh', () => {
      const result = parseFloat(evaluateExpression('cosh(0)'));
      expect(result).toBeCloseTo(1, 5);
    });

    test('tanh', () => {
      const result = parseFloat(evaluateExpression('tanh(0)'));
      expect(result).toBeCloseTo(0, 5);
    });
  });

  describe('Inverse Trigonometric Functions', () => {
    test('asin in degrees', () => {
      const result = parseFloat(evaluateExpression('asin(0.5)', true));
      expect(result).toBeCloseTo(30, 5);
    });

    test('acos in degrees', () => {
      const result = parseFloat(evaluateExpression('acos(0.5)', true));
      expect(result).toBeCloseTo(60, 5);
    });

    test('atan in degrees', () => {
      const result = parseFloat(evaluateExpression('atan(1)', true));
      expect(result).toBeCloseTo(45, 5);
    });
  });
});
