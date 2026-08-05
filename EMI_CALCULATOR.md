# EMI Calculator Feature

## Overview
An EMI (Equated Monthly Installment) calculator has been integrated into the scientific calculator application. This feature allows users to calculate loan EMIs and view detailed breakup of principal and interest payments.

## Features

### 1. **Interactive Input Controls**
- **Loan Amount (Principal)**: Range from ₹10,000 to ₹1,00,00,000
- **Interest Rate**: 0% to 30% per annum
- **Loan Tenure**: 1 to 360 months (30 years)
- Both slider and number input for precise control

### 2. **Summary Cards**
- **Monthly EMI**: Highlighted card showing the monthly payment amount
- **Principal Amount**: Total loan amount
- **Total Interest**: Total interest payable over the loan tenure
- **Total Amount**: Sum of principal + interest

### 3. **Visual Payment Breakup**
- Pie chart showing the proportion of principal vs interest
- Color-coded legend for easy identification
  - Blue: Principal amount
  - Red: Interest amount

### 4. **Detailed Amortization Schedule**
- Month-by-month breakdown of each EMI payment
- Shows for each month:
  - EMI amount
  - Principal component (highlighted in blue)
  - Interest component (highlighted in red)
  - Remaining balance
- Scrollable table for long tenure loans

## How to Use

1. **Switch to EMI Calculator**: Click the 💰 icon in the header to switch from the scientific calculator to the EMI calculator

2. **Adjust Loan Parameters**:
   - Use sliders for quick adjustments
   - Use number inputs for precise values
   - Values update in real-time

3. **Review Results**:
   - Check the monthly EMI amount in the highlighted card
   - View total interest and total payment amount
   - Review the pie chart for visual representation

4. **View Amortization Schedule**:
   - Scroll through the month-by-month payment breakdown
   - See how much principal and interest you pay each month
   - Track remaining balance over time

5. **Switch Back**: Click the 🔢 icon to return to the scientific calculator

## EMI Calculation Formula

The EMI is calculated using the standard loan formula:

```
EMI = P × r × (1 + r)^n / ((1 + r)^n - 1)

Where:
- P = Principal loan amount
- r = Monthly interest rate (annual rate / 12 / 100)
- n = Loan tenure in months
```

## Technology Stack

- **React**: Component-based UI
- **React Hooks**: useState and useEffect for state management
- **CSS**: Custom styling with dark mode support
- **JavaScript**: Mathematical calculations

## Files Modified/Created

1. **src/components/EMICalculator.js** - Main EMI calculator component
2. **src/components/EMICalculator.css** - Styling for EMI calculator
3. **src/App.js** - Updated to include toggle between calculators

## Features

- ✅ Responsive design for mobile and desktop
- ✅ Dark mode support (inherits from app theme)
- ✅ Real-time calculation updates
- ✅ Indian currency formatting (₹)
- ✅ Precise floating-point calculations
- ✅ Handles edge cases (0% interest rate)
- ✅ Clean, modern UI with smooth transitions

## Future Enhancements (Out of Scope)

- Export amortization schedule as PDF/Excel
- Compare multiple loan scenarios
- Include processing fees and other charges
- Save/load loan scenarios
- Graphical representation of principal vs interest over time (line chart)
- Pre-payment calculator
