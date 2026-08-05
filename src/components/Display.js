import React from 'react';
import './Display.css';

const Display = ({ value, expression, isDegree, memory, error }) => {
  return (
    <div className="display-container">
      <div className="display-info">
        <span className="mode-indicator">{isDegree ? 'DEG' : 'RAD'}</span>
        {memory !== 0 && <span className="memory-indicator">M</span>}
      </div>

      {expression && expression !== value && (
        <div className="expression">{expression}</div>
      )}

      <div className={`display ${error ? 'error' : ''}`}>
        {value || '0'}
      </div>

      {error && <div className="error-message">{error}</div>}
    </div>
  );
};

export default Display;
