import React from 'react';
import './Button.css';

const Button = ({ value, onClick, type = 'number', span = 1 }) => {
  const handleClick = () => {
    onClick(value);

    // Add haptic feedback for mobile devices
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }
  };

  const getButtonClass = () => {
    const classes = ['calculator-button', type];
    if (span > 1) classes.push(`span-${span}`);
    return classes.join(' ');
  };

  const getDisplayValue = () => {
    // Special display formatting for certain buttons
    const displayMap = {
      'factorial': 'n!',
      'sqrt': '√',
      'pi': 'π',
    };

    return displayMap[value] || value;
  };

  return (
    <button
      className={getButtonClass()}
      onClick={handleClick}
      aria-label={value}
    >
      {getDisplayValue()}
    </button>
  );
};

export default Button;
