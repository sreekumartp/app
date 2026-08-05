import React, { useState, useEffect } from 'react';
import './EMICalculator.css';

function EMICalculator() {
  const [principal, setPrincipal] = useState(100000);
  const [interestRate, setInterestRate] = useState(10);
  const [tenure, setTenure] = useState(12);
  const [emi, setEmi] = useState(0);
  const [totalInterest, setTotalInterest] = useState(0);
  const [totalAmount, setTotalAmount] = useState(0);
  const [schedule, setSchedule] = useState([]);

  useEffect(() => {
    if (principal <= 0 || interestRate < 0 || tenure <= 0) {
      setEmi(0);
      setTotalInterest(0);
      setTotalAmount(0);
      setSchedule([]);
      return;
    }

    const P = parseFloat(principal);
    const r = parseFloat(interestRate) / 12 / 100; // Monthly interest rate
    const n = parseFloat(tenure);

    let calculatedEMI;
    if (r === 0) {
      // If interest rate is 0, EMI is simply principal divided by tenure
      calculatedEMI = P / n;
    } else {
      // EMI Formula: P * r * (1 + r)^n / ((1 + r)^n - 1)
      calculatedEMI = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    }

    const totalPmt = calculatedEMI * n;
    const totalInt = totalPmt - P;

    setEmi(calculatedEMI);
    setTotalInterest(totalInt);
    setTotalAmount(totalPmt);

    // Generate amortization schedule
    let balance = P;
    const scheduleData = [];

    for (let month = 1; month <= n; month++) {
      const interestPayment = balance * r;
      const principalPayment = calculatedEMI - interestPayment;
      balance -= principalPayment;

      // Ensure balance doesn't go negative due to floating point errors
      if (balance < 0.01) balance = 0;

      scheduleData.push({
        month,
        emi: calculatedEMI,
        principal: principalPayment,
        interest: interestPayment,
        balance: balance
      });
    }

    setSchedule(scheduleData);
  }, [principal, interestRate, tenure]);

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  };

  const principalPercentage = totalAmount > 0 ? (principal / totalAmount) * 100 : 0;
  const interestPercentage = totalAmount > 0 ? (totalInterest / totalAmount) * 100 : 0;

  return (
    <div className="emi-calculator">
      <h2>EMI Calculator</h2>

      <div className="emi-inputs">
        <div className="input-group">
          <label>
            Loan Amount (Principal)
            <span className="input-value">{formatCurrency(principal)}</span>
          </label>
          <input
            type="range"
            min="10000"
            max="10000000"
            step="10000"
            value={principal}
            onChange={(e) => setPrincipal(e.target.value)}
          />
          <input
            type="number"
            min="10000"
            max="10000000"
            step="10000"
            value={principal}
            onChange={(e) => setPrincipal(e.target.value)}
            className="number-input"
          />
        </div>

        <div className="input-group">
          <label>
            Interest Rate (% per annum)
            <span className="input-value">{interestRate}%</span>
          </label>
          <input
            type="range"
            min="0"
            max="30"
            step="0.1"
            value={interestRate}
            onChange={(e) => setInterestRate(e.target.value)}
          />
          <input
            type="number"
            min="0"
            max="30"
            step="0.1"
            value={interestRate}
            onChange={(e) => setInterestRate(e.target.value)}
            className="number-input"
          />
        </div>

        <div className="input-group">
          <label>
            Loan Tenure (Months)
            <span className="input-value">{tenure} months ({Math.floor(tenure / 12)}y {tenure % 12}m)</span>
          </label>
          <input
            type="range"
            min="1"
            max="360"
            step="1"
            value={tenure}
            onChange={(e) => setTenure(e.target.value)}
          />
          <input
            type="number"
            min="1"
            max="360"
            step="1"
            value={tenure}
            onChange={(e) => setTenure(e.target.value)}
            className="number-input"
          />
        </div>
      </div>

      <div className="emi-summary">
        <div className="summary-card highlight">
          <h3>Monthly EMI</h3>
          <p className="summary-value">{formatCurrency(emi)}</p>
        </div>

        <div className="summary-card">
          <h3>Principal Amount</h3>
          <p className="summary-value">{formatCurrency(principal)}</p>
        </div>

        <div className="summary-card">
          <h3>Total Interest</h3>
          <p className="summary-value">{formatCurrency(totalInterest)}</p>
        </div>

        <div className="summary-card">
          <h3>Total Amount</h3>
          <p className="summary-value">{formatCurrency(totalAmount)}</p>
        </div>
      </div>

      <div className="emi-chart">
        <h3>Payment Breakup</h3>
        <div className="pie-chart">
          <div className="pie-segment principal" style={{ '--percentage': principalPercentage }}>
            <span className="pie-label">{principalPercentage.toFixed(1)}%</span>
          </div>
          <div className="pie-segment interest" style={{ '--percentage': interestPercentage }}>
            <span className="pie-label">{interestPercentage.toFixed(1)}%</span>
          </div>
        </div>
        <div className="chart-legend">
          <div className="legend-item">
            <span className="legend-color principal"></span>
            <span>Principal: {formatCurrency(principal)}</span>
          </div>
          <div className="legend-item">
            <span className="legend-color interest"></span>
            <span>Interest: {formatCurrency(totalInterest)}</span>
          </div>
        </div>
      </div>

      <div className="amortization-schedule">
        <h3>Amortization Schedule</h3>
        <div className="schedule-table-container">
          <table className="schedule-table">
            <thead>
              <tr>
                <th>Month</th>
                <th>EMI</th>
                <th>Principal</th>
                <th>Interest</th>
                <th>Balance</th>
              </tr>
            </thead>
            <tbody>
              {schedule.map((row) => (
                <tr key={row.month}>
                  <td>{row.month}</td>
                  <td>{formatCurrency(row.emi)}</td>
                  <td className="principal-cell">{formatCurrency(row.principal)}</td>
                  <td className="interest-cell">{formatCurrency(row.interest)}</td>
                  <td>{formatCurrency(row.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default EMICalculator;
