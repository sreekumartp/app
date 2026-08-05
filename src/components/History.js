import React from 'react';
import './History.css';

const History = ({ history, onSelect, onClear, onClose }) => {
  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;

    // Less than 1 minute ago
    if (diff < 60000) {
      return 'Just now';
    }

    // Less than 1 hour ago
    if (diff < 3600000) {
      const minutes = Math.floor(diff / 60000);
      return `${minutes} min${minutes > 1 ? 's' : ''} ago`;
    }

    // Less than 24 hours ago
    if (diff < 86400000) {
      const hours = Math.floor(diff / 3600000);
      return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    }

    // Format as date
    return date.toLocaleDateString();
  };

  return (
    <div className="history-overlay" onClick={onClose}>
      <div className="history-panel" onClick={(e) => e.stopPropagation()}>
        <div className="history-header">
          <h2>Calculation History</h2>
          <div className="history-actions">
            {history.length > 0 && (
              <button className="clear-history-btn" onClick={onClear}>
                Clear All
              </button>
            )}
            <button className="close-btn" onClick={onClose}>
              ✕
            </button>
          </div>
        </div>

        <div className="history-list">
          {history.length === 0 ? (
            <div className="empty-history">
              <p>No calculation history yet</p>
              <span>Your calculations will appear here</span>
            </div>
          ) : (
            history.map((item, index) => (
              <div
                key={index}
                className="history-item"
                onClick={() => onSelect(item)}
              >
                <div className="history-item-content">
                  <div className="history-expression">{item.expression}</div>
                  <div className="history-result">= {item.result}</div>
                </div>
                <div className="history-timestamp">
                  {formatTimestamp(item.timestamp)}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default History;
