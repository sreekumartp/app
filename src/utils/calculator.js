import * as math from 'mathjs';

// Configure mathjs
const mathConfig = {
  number: 'BigNumber',
  precision: 14
};

const mathInstance = math.create(math.all, mathConfig);

// Factorial function with limit
const factorial = (n) => {
  if (n < 0) throw new Error('Factorial of negative number');
  if (n > 170) throw new Error('Factorial overflow (max 170)');
  if (n === 0 || n === 1) return 1;

  let result = 1;
  for (let i = 2; i <= n; i++) {
    result *= i;
  }
  return result;
};

// Convert degrees to radians
const toRadians = (degrees) => {
  return degrees * (Math.PI / 180);
};

// Convert radians to degrees
const toDegrees = (radians) => {
  return radians * (180 / Math.PI);
};

// Process expression to handle custom functions
const preprocessExpression = (expr, isDegree) => {
  let processed = expr;

  // Replace custom operators
  processed = processed.replace(/×/g, '*');
  processed = processed.replace(/÷/g, '/');

  // Handle percentage
  processed = processed.replace(/(\d+\.?\d*)%/g, '($1/100)');

  // Handle factorial
  processed = processed.replace(/factorial\(/g, 'fact(');

  // Handle square root
  processed = processed.replace(/sqrt\(/g, 'sqrt(');

  // Handle absolute value
  processed = processed.replace(/abs\(/g, 'abs(');

  // Handle natural log
  processed = processed.replace(/ln\(/g, 'log(');

  // Handle log base 10
  processed = processed.replace(/log\(/g, 'log10(');

  // Handle constants
  processed = processed.replace(/pi/g, 'PI');
  processed = processed.replace(/e(?![a-z])/g, 'E');

  // Handle trigonometric functions with degree/radian conversion
  if (isDegree) {
    processed = processed.replace(/sin\(/g, 'sin_deg(');
    processed = processed.replace(/cos\(/g, 'cos_deg(');
    processed = processed.replace(/tan\(/g, 'tan_deg(');
    processed = processed.replace(/asin\(/g, 'asin_deg(');
    processed = processed.replace(/acos\(/g, 'acos_deg(');
    processed = processed.replace(/atan\(/g, 'atan_deg(');
  }

  return processed;
};

// Evaluate the expression
export const evaluateExpression = (expression, isDegree = true) => {
  if (!expression || expression.trim() === '') {
    throw new Error('Empty expression');
  }

  try {
    const processed = preprocessExpression(expression, isDegree);

    // Create custom functions scope
    const scope = {
      PI: Math.PI,
      E: Math.E,

      // Factorial
      fact: factorial,

      // Trigonometric functions in degrees
      sin_deg: (x) => Math.sin(toRadians(x)),
      cos_deg: (x) => Math.cos(toRadians(x)),
      tan_deg: (x) => Math.tan(toRadians(x)),
      asin_deg: (x) => toDegrees(Math.asin(x)),
      acos_deg: (x) => toDegrees(Math.acos(x)),
      atan_deg: (x) => toDegrees(Math.atan(x)),

      // Standard trig functions (radians)
      sin: Math.sin,
      cos: Math.cos,
      tan: Math.tan,
      asin: Math.asin,
      acos: Math.acos,
      atan: Math.atan,

      // Hyperbolic functions
      sinh: Math.sinh,
      cosh: Math.cosh,
      tanh: Math.tanh,

      // Other functions
      sqrt: Math.sqrt,
      abs: Math.abs,
      log: Math.log,
      log10: Math.log10,
      exp: Math.exp,
      pow: Math.pow,
    };

    // Evaluate using mathjs
    const result = mathInstance.evaluate(processed, scope);

    // Convert BigNumber to regular number for display
    const numResult = typeof result === 'object' && result.toNumber ? result.toNumber() : result;

    // Handle special cases
    if (isNaN(numResult)) {
      throw new Error('Invalid calculation');
    }

    if (!isFinite(numResult)) {
      throw new Error('Result is infinity');
    }

    // Format the result
    let formattedResult = numResult;

    // Use scientific notation for very large or very small numbers
    if (Math.abs(numResult) > 1e10 || (Math.abs(numResult) < 1e-6 && numResult !== 0)) {
      formattedResult = numResult.toExponential(6);
    } else {
      // Round to avoid floating point errors
      formattedResult = Math.round(numResult * 1e10) / 1e10;

      // Remove trailing zeros
      formattedResult = parseFloat(formattedResult.toPrecision(12));
    }

    return formattedResult.toString();
  } catch (error) {
    // Handle specific errors
    if (error.message.includes('Undefined symbol')) {
      throw new Error('Invalid expression');
    }
    if (error.message.includes('Unexpected')) {
      throw new Error('Syntax error');
    }
    if (error.message.includes('Division by zero')) {
      throw new Error('Cannot divide by zero');
    }

    throw error;
  }
};

// Validate if expression can be evaluated
export const validateExpression = (expression) => {
  try {
    evaluateExpression(expression);
    return true;
  } catch {
    return false;
  }
};

// Format number for display
export const formatNumber = (num) => {
  if (typeof num !== 'number') {
    return num;
  }

  if (Math.abs(num) > 1e10 || (Math.abs(num) < 1e-6 && num !== 0)) {
    return num.toExponential(6);
  }

  return parseFloat(num.toPrecision(12)).toString();
};
