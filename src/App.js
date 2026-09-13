import React, { useState, useEffect, useCallback } from 'react';
import './App.css';
import Display from './components/Display';
import ButtonGrid from './components/ButtonGrid';
import History from './components/History';
import EMICalculator from './components/EMICalculator';
import MetroPlanner from './components/MetroPlanner';
import { evaluateExpression } from './utils/calculator';

const MODE_TITLES = {
  calc: 'Scientific Calculator',
  emi: 'EMI Calculator',
  metro: 'Namma Metro Planner',
};

function App() {
  const [display, setDisplay] = useState('0');
  const [expression, setExpression] = useState('');
  const [history, setHistory] = useState([]);
  const [memory, setMemory] = useState(0);
  const [isDegree, setIsDegree] = useState(true);
  const [showHistory, setShowHistory] = useState(false);
  const [theme, setTheme] = useState('dark');
  const [error, setError] = useState('');
  const [mode, setMode] = useState('calc');

  useEffect(() => {
    // Load history and memory from localStorage
    const savedHistory = localStorage.getItem('calcHistory');
    const savedMemory = localStorage.getItem('calcMemory');
    if (savedHistory) setHistory(JSON.parse(savedHistory));
    if (savedMemory) setMemory(parseFloat(savedMemory));
  }, []);

  useEffect(() => {
    // Save history to localStorage
    localStorage.setItem('calcHistory', JSON.stringify(history));
  }, [history]);

  useEffect(() => {
    // Save memory to localStorage
    localStorage.setItem('calcMemory', memory.toString());
  }, [memory]);

  const handleInput = useCallback((value) => {
    setError('');

    if (value === '=') {
      try {
        const result = evaluateExpression(expression || display, isDegree);
        setHistory(prev => [{ expression: expression || display, result, timestamp: Date.now() }, ...prev.slice(0, 49)]);
        setDisplay(result);
        setExpression('');
      } catch (err) {
        setError(err.message || 'Error');
        setDisplay('Error');
      }
      return;
    }

    if (value === 'AC') {
      setDisplay('0');
      setExpression('');
      setError('');
      return;
    }

    if (value === 'C') {
      setDisplay('0');
      setExpression('');
      return;
    }

    if (value === 'DEL') {
      if (expression.length > 0) {
        const newExpr = expression.slice(0, -1);
        setExpression(newExpr);
        setDisplay(newExpr || '0');
      } else if (display.length > 1) {
        setDisplay(display.slice(0, -1));
      } else {
        setDisplay('0');
      }
      return;
    }

    if (value === 'M+') {
      const currentValue = parseFloat(display);
      if (!isNaN(currentValue)) {
        setMemory(prev => prev + currentValue);
      }
      return;
    }

    if (value === 'M-') {
      const currentValue = parseFloat(display);
      if (!isNaN(currentValue)) {
        setMemory(prev => prev - currentValue);
      }
      return;
    }

    if (value === 'MR') {
      setDisplay(memory.toString());
      setExpression('');
      return;
    }

    if (value === 'MC') {
      setMemory(0);
      return;
    }

    if (value === 'DEG/RAD') {
      setIsDegree(prev => !prev);
      return;
    }

    // Handle special functions
    const functions = ['sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sinh', 'cosh', 'tanh', 'log', 'ln', 'sqrt', 'abs', 'factorial'];
    if (functions.includes(value)) {
      const newExpr = expression + value + '(';
      setExpression(newExpr);
      setDisplay(newExpr);
      return;
    }

    // Handle constants
    if (value === 'π') {
      const newExpr = expression + 'pi';
      setExpression(newExpr);
      setDisplay(newExpr);
      return;
    }

    if (value === 'e') {
      const newExpr = expression + 'e';
      setExpression(newExpr);
      setDisplay(newExpr);
      return;
    }

    // Handle operators and numbers
    if (display === 'Error' || (display === '0' && !['(', ')'].includes(value))) {
      if (['+', '-', '×', '÷', '^', '%'].includes(value)) {
        const op = value === '×' ? '*' : value === '÷' ? '/' : value;
        const newExpr = expression + op;
        setExpression(newExpr);
        setDisplay(newExpr);
      } else {
        const val = value;
        setExpression(val);
        setDisplay(val);
      }
    } else {
      const op = value === '×' ? '*' : value === '÷' ? '/' : value;
      const newExpr = expression + op;
      setExpression(newExpr);
      setDisplay(newExpr);
    }
  }, [display, expression, isDegree, memory]);

  // Keyboard support
  useEffect(() => {
    const handleKeyPress = (e) => {
      const key = e.key;
      const keyMap = {
        'Enter': '=',
        'Escape': 'AC',
        'Backspace': 'DEL',
        '*': '×',
        '/': '÷',
      };

      if (keyMap[key]) {
        e.preventDefault();
        handleInput(keyMap[key]);
      } else if (/[0-9+\-().^%]/.test(key)) {
        e.preventDefault();
        handleInput(key);
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [handleInput]);

  const handleHistorySelect = (item) => {
    setDisplay(item.result);
    setExpression('');
    setShowHistory(false);
  };

  const clearHistory = () => {
    setHistory([]);
    localStorage.removeItem('calcHistory');
  };

  const isCalculator = mode === 'calc';

  return (
    <div className={`app ${theme} ${isCalculator ? '' : 'wide'}`}>
      <div className="calculator">
        <div className="calculator-header">
          <h1>{MODE_TITLES[mode]}</h1>
          <div className="header-controls">
            <button
              className={`icon-button ${mode === 'emi' ? 'active' : ''}`}
              onClick={() => setMode(mode === 'emi' ? 'calc' : 'emi')}
              title={mode === 'emi' ? 'Switch to Calculator' : 'Switch to EMI Calculator'}
            >
              {mode === 'emi' ? '🔢' : '💰'}
            </button>
            <button
              className={`icon-button ${mode === 'metro' ? 'active' : ''}`}
              onClick={() => setMode(mode === 'metro' ? 'calc' : 'metro')}
              title={mode === 'metro' ? 'Switch to Calculator' : 'Switch to Metro Planner'}
            >
              {mode === 'metro' ? '🔢' : '🚇'}
            </button>
            {isCalculator && (
              <button
                className="icon-button"
                onClick={() => setShowHistory(!showHistory)}
                title="History"
              >
                📋
              </button>
            )}
            <button
              className="icon-button"
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              title="Toggle Theme"
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>
          </div>
        </div>

        {mode === 'emi' && <EMICalculator />}
        {mode === 'metro' && <MetroPlanner />}

        {isCalculator && (
          <>
            <Display
              value={display}
              expression={expression}
              isDegree={isDegree}
              memory={memory}
              error={error}
            />

            <ButtonGrid
              onInput={handleInput}
              isDegree={isDegree}
            />

            {showHistory && (
              <History
                history={history}
                onSelect={handleHistorySelect}
                onClear={clearHistory}
                onClose={() => setShowHistory(false)}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default App;
