import React from 'react';
import Button from './Button';
import './ButtonGrid.css';

const ButtonGrid = ({ onInput, isDegree }) => {
  const basicButtons = [
    // Row 1
    ['AC', 'DEL', '%', '÷'],
    // Row 2
    ['7', '8', '9', '×'],
    // Row 3
    ['4', '5', '6', '-'],
    // Row 4
    ['1', '2', '3', '+'],
    // Row 5
    ['0', '.', '(', ')'],
  ];

  const scientificButtons = [
    // Row 1
    ['sin', 'cos', 'tan', isDegree ? 'DEG' : 'RAD'],
    // Row 2
    ['asin', 'acos', 'atan', 'π'],
    // Row 3
    ['sinh', 'cosh', 'tanh', 'e'],
    // Row 4
    ['log', 'ln', 'sqrt', '^'],
    // Row 5
    ['abs', 'factorial', 'M+', 'M-'],
    // Row 6
    ['MR', 'MC', 'C', '='],
  ];

  const handleButtonClick = (value) => {
    if (value === 'DEG' || value === 'RAD') {
      onInput('DEG/RAD');
    } else {
      onInput(value);
    }
  };

  return (
    <div className="button-grid-container">
      <div className="scientific-panel">
        {scientificButtons.map((row, rowIndex) => (
          <div key={rowIndex} className="button-row">
            {row.map((btn) => (
              <Button
                key={btn}
                value={btn}
                onClick={handleButtonClick}
                type={
                  btn === '=' ? 'equals' :
                  btn === 'DEG' || btn === 'RAD' ? 'mode' :
                  ['M+', 'M-', 'MR', 'MC'].includes(btn) ? 'memory' :
                  ['AC', 'C', 'DEL'].includes(btn) ? 'clear' :
                  ['sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sinh', 'cosh', 'tanh', 'log', 'ln', 'sqrt', 'abs', 'factorial'].includes(btn) ? 'function' :
                  'operator'
                }
              />
            ))}
          </div>
        ))}
      </div>

      <div className="basic-panel">
        {basicButtons.map((row, rowIndex) => (
          <div key={rowIndex} className="button-row">
            {row.map((btn) => (
              <Button
                key={btn}
                value={btn}
                onClick={handleButtonClick}
                type={
                  btn === '=' ? 'equals' :
                  ['AC', 'C', 'DEL'].includes(btn) ? 'clear' :
                  ['+', '-', '×', '÷', '%', '^'].includes(btn) ? 'operator' :
                  ['(', ')'].includes(btn) ? 'bracket' :
                  'number'
                }
                span={btn === '0' ? 2 : 1}
              />
            ))}
          </div>
        ))}
        <div className="button-row">
          <Button
            value="="
            onClick={handleButtonClick}
            type="equals"
            span={4}
          />
        </div>
      </div>
    </div>
  );
};

export default ButtonGrid;
