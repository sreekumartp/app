# Quick Start Guide

Get your Scientific Calculator up and running in under 5 minutes!

## 🚀 Installation

### Step 1: Prerequisites
Make sure you have [Node.js](https://nodejs.org/) (v14 or higher) installed.

Check your version:
```bash
node --version
npm --version
```

### Step 2: Install Dependencies
```bash
npm install
```

### Step 3: Start Development Server
```bash
npm start
```

The calculator will open automatically in your browser at `http://localhost:3000`

## 🎯 Using the Calculator

### Basic Operations
1. **Enter numbers**: Click number buttons or use keyboard
2. **Operators**: Click +, -, ×, ÷ or use keyboard (+, -, *, /)
3. **Calculate**: Click = or press Enter
4. **Clear**: Click AC (clear all) or C (clear current)

### Scientific Functions
Example: Calculate sin(30°)
1. Click `sin` button
2. Type `30`
3. Click `)` to close parenthesis
4. Click `=` or press Enter
5. Result: 0.5

### Memory Operations
- **M+**: Add current value to memory
- **M-**: Subtract from memory
- **MR**: Recall memory value
- **MC**: Clear memory

### Switch Between Degrees/Radians
- Click the **DEG/RAD** button in the button grid
- Current mode shown in top-left of display

### View History
1. Click the 📋 icon in the top-right
2. Click any history item to reuse the result
3. Click "Clear All" to delete history

### Toggle Theme
- Click the ☀️/🌙 icon to switch between light and dark themes

## ⌨️ Keyboard Shortcuts

| Key | Action |
|-----|--------|
| 0-9 | Enter numbers |
| + - * / | Basic operations |
| ( ) | Parentheses |
| . | Decimal point |
| ^ | Power |
| % | Percentage |
| Enter | Calculate (=) |
| Escape | Clear all (AC) |
| Backspace | Delete last character |

## 📱 Mobile Use

- Works on all mobile browsers
- Tap buttons for input
- Haptic feedback enabled (if supported)
- Optimized for portrait and landscape

## 🔧 Available Commands

```bash
# Start development server
npm start

# Run tests
npm test

# Build for production
npm run build

# Serve production build locally
npx serve -s build
```

## 🎨 Examples

### Basic Math
```
(2 + 3) × 4 = 20
10 - 2 × 3 = 4
```

### Scientific
```
sin(30) = 0.5 (in DEG mode)
log(100) = 2
sqrt(16) = 4
factorial(5) = 120
```

### With Constants
```
pi × 2 = 6.283...
e^2 = 7.389...
```

### Complex Expressions
```
(sin(30) + cos(60)) × sqrt(16) = 4
```

## 🐛 Troubleshooting

### Calculator won't start
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
npm start
```

### Keyboard shortcuts not working
- Make sure the calculator window has focus
- Click anywhere in the calculator area first

### History not saving
- Check if browser allows localStorage
- Try in a different browser

## 📚 Next Steps

- Read the full [README.md](README.md) for detailed features
- Check [DEPLOYMENT.md](DEPLOYMENT.md) to deploy your own version
- See [CONTRIBUTING.md](CONTRIBUTING.md) to contribute

## 💡 Tips

- Use parentheses for complex calculations
- History persists across sessions
- Memory survives page refresh
- Works offline once loaded
- Scientific notation activates for very large/small numbers

## 🆘 Need Help?

- Check the [README.md](README.md) for full documentation
- Look at example calculations in this guide
- Open an issue on the repository

## 🎉 That's It!

You're ready to use your Scientific Calculator. Happy calculating! 🧮

---

**Quick Reference**

| Feature | How to Access |
|---------|---------------|
| History | 📋 button (top-right) |
| Theme | ☀️/🌙 button (top-right) |
| Memory | M+, M-, MR, MC buttons |
| Mode | DEG/RAD button |
| Clear All | AC button |
| Delete | DEL button |

