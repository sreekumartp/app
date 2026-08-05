# Scientific Calculator & EMI Calculator

A modern, feature-rich web application built with React that includes both a scientific calculator and an EMI (Equated Monthly Installment) calculator. The scientific calculator supports basic arithmetic operations, advanced scientific functions, memory operations, calculation history, and both light/dark themes. The EMI calculator helps you calculate loan payments with detailed principal and interest breakup.

## Features

### Core Functionality
- **Basic Operations**: Addition, subtraction, multiplication, division
- **Scientific Functions**:
  - Trigonometric: sin, cos, tan, asin, acos, atan
  - Hyperbolic: sinh, cosh, tanh
  - Logarithmic: log (base 10), ln (natural log)
  - Other: square root, absolute value, factorial, power
- **Mathematical Constants**: π (pi), e
- **Parentheses Support**: For complex expressions
- **Order of Operations**: PEMDAS/BODMAS compliant

### Advanced Features
- **Memory Functions**: M+, M-, MR (Recall), MC (Clear)
- **Calculation History**: Stores up to 50 previous calculations
- **Angle Modes**: Switch between degrees and radians for trigonometric functions
- **Keyboard Support**: Full keyboard input support
- **Themes**: Toggle between light and dark modes
- **Responsive Design**: Works on desktop, tablet, and mobile devices
- **Landscape Mode**: Optimized layout for landscape orientation
- **Error Handling**: Clear error messages for invalid expressions
- **Local Storage**: Persists history and memory across sessions

### User Experience
- Smooth animations and transitions
- Haptic feedback on mobile devices
- Copy/paste support
- Multi-line expression display
- Scientific notation for very large/small numbers
- Accessibility features

## EMI Calculator

### Features
- **Loan Amount Input**: Set principal amount from ₹10,000 to ₹1,00,00,000
- **Interest Rate**: Configure annual interest rate from 0% to 30%
- **Loan Tenure**: Set tenure from 1 to 360 months (30 years)
- **Interactive Controls**: Use sliders or manual input for precise values
- **Real-time Calculation**: EMI updates automatically as you adjust parameters

### EMI Breakdown Display
- **Monthly EMI**: Highlighted card showing your monthly payment
- **Principal Amount**: Total loan amount
- **Total Interest**: Complete interest payable over loan tenure
- **Total Amount**: Sum of principal and interest

### Visual Analytics
- **Pie Chart**: Visual representation of principal vs interest ratio
- **Color-coded Legend**: 
  - Teal: Principal amount
  - Amber: Interest amount
- **Percentage Display**: Shows exact percentage split

### Amortization Schedule
- **Month-by-month Breakdown**: Detailed table showing:
  - Month number
  - EMI amount
  - Principal component (highlighted in teal)
  - Interest component (highlighted in amber)
  - Outstanding balance
- **Scrollable Table**: Easy navigation through the entire loan period
- **Sticky Header**: Table header remains visible while scrolling

### Theme Support
- Works seamlessly with both light and dark themes
- Consistent teal color scheme for professional appearance

## Installation

### Prerequisites
- Node.js (v14 or higher)
- npm or yarn

### Steps

1. Clone the repository:
```bash
git clone <repository-url>
cd app
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm start
```

4. Open [http://localhost:3000](http://localhost:3000) in your browser.

## Usage

### Basic Operations
1. Click number buttons to enter numbers
2. Click operation buttons (+, -, ×, ÷) to perform calculations
3. Click = to see the result
4. Use AC (All Clear) to clear everything or C to clear current input

### Scientific Functions
1. Click any function button (sin, cos, log, etc.)
2. The function name with opening parenthesis will appear
3. Enter your number
4. Close the parenthesis with )
5. Click = to calculate

Example: To calculate sin(30°), click: `sin` → `3` → `0` → `)` → `=`

### Memory Operations
- **M+**: Add current display value to memory
- **M-**: Subtract current display value from memory
- **MR**: Recall value from memory
- **MC**: Clear memory

### Angle Mode
- Click the **DEG/RAD** button to toggle between degrees and radians
- The current mode is displayed in the top-left of the display

### History
1. Click the 📋 (clipboard) icon to view calculation history
2. Click any history item to use that result
3. Click "Clear All" to delete all history

### Switching Between Calculators
- Click the 💰 icon in the header to switch to EMI Calculator
- Click the 🔢 icon to return to Scientific Calculator
- Each calculator maintains its own state

### Keyboard Shortcuts
- **Numbers**: 0-9
- **Operations**: +, -, *, /
- **Parentheses**: (, )
- **Decimal**: .
- **Calculate**: Enter
- **Clear All**: Escape
- **Delete**: Backspace
- **Power**: ^
- **Percentage**: %

## Available Scripts

### `npm start`
Runs the app in development mode at [http://localhost:3000](http://localhost:3000).

### `npm test`
Launches the test runner in interactive watch mode.

### `npm run build`
Builds the app for production to the `build` folder.

## Technology Stack

- **React 18**: Modern React with hooks
- **mathjs**: Mathematical expression evaluation
- **CSS3**: Animations, gradients, and responsive design
- **Local Storage API**: For persisting history and memory
- **Vibration API**: For haptic feedback on mobile

## Project Structure

```
src/
├── components/
│   ├── Button.js          # Individual calculator button
│   ├── Button.css
│   ├── ButtonGrid.js      # Grid layout for all buttons
│   ├── ButtonGrid.css
│   ├── Display.js         # Calculator display component
│   ├── Display.css
│   ├── EMICalculator.js   # EMI calculator component
│   ├── EMICalculator.css
│   ├── History.js         # History panel component
│   └── History.css
├── utils/
│   ├── calculator.js      # Core calculation engine
│   └── calculator.test.js # Calculator tests
├── App.js                 # Main app component
├── App.css
├── App.test.js            # App tests
├── index.js               # Entry point
├── index.css
└── setupTests.js          # Test configuration
```

## Testing

The application includes comprehensive unit tests for:
- Basic arithmetic operations
- Scientific functions
- Trigonometric calculations
- Error handling
- Expression validation
- UI components

Run tests with:
```bash
npm test
```

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)
- Mobile browsers (iOS Safari, Chrome Mobile)

## Performance Optimizations

- React.memo for component optimization
- Callback memoization with useCallback
- Efficient re-renders
- Lazy loading for history panel
- Optimized CSS animations

## Future Enhancements

Potential features for future versions:
- Export calculation history
- Customizable themes with color picker
- Graph plotting capabilities
- Statistics functions (mean, median, standard deviation)
- Matrix operations
- Unit conversions
- Programmable functions
- Cloud sync for history

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues, questions, or suggestions, please open an issue in the repository.

---

Built with ❤️ using React and mathjs
