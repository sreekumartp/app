import { render, screen, fireEvent } from '@testing-library/react';
import App from './App';

describe('App Component Tests', () => {
  test('renders calculator title', () => {
    render(<App />);
    const titleElement = screen.getByText(/Scientific Calculator/i);
    expect(titleElement).toBeInTheDocument();
  });

  test('initial display shows 0', () => {
    render(<App />);
    const display = screen.getByText('0');
    expect(display).toBeInTheDocument();
  });

  test('clicking number buttons updates display', () => {
    render(<App />);
    const button1 = screen.getByText('1');
    const button2 = screen.getByText('2');

    fireEvent.click(button1);
    fireEvent.click(button2);

    expect(screen.getByText('12')).toBeInTheDocument();
  });

  test('AC button clears display', () => {
    render(<App />);
    const button5 = screen.getByText('5');
    const acButton = screen.getByText('AC');

    fireEvent.click(button5);
    fireEvent.click(acButton);

    expect(screen.getByText('0')).toBeInTheDocument();
  });

  test('theme toggle button exists', () => {
    render(<App />);
    const themeButtons = screen.getAllByRole('button');
    const themeButton = themeButtons.find(btn =>
      btn.textContent === '☀️' || btn.textContent === '🌙'
    );
    expect(themeButton).toBeInTheDocument();
  });

  test('history button exists', () => {
    render(<App />);
    const historyButton = screen.getByTitle('History');
    expect(historyButton).toBeInTheDocument();
  });

  test('DEG mode is shown by default', () => {
    render(<App />);
    const degIndicator = screen.getByText('DEG');
    expect(degIndicator).toBeInTheDocument();
  });
});
