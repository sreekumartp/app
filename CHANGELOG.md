# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Namma Metro journey planner for Bengaluru (🚇 icon in the header)
  - Purple, Green and Yellow line network data (85 unique stations)
  - Shortest-time route finding with interchange penalties at Majestic and RV Road
  - Distance-slab fare estimates for tokens and smart cards
  - Travel time, arrival estimate and step-by-step boarding/alighting directions
  - Expandable legs listing intermediate stations
  - Popular journey shortcuts and recently planned trips
- Tests covering the metro network data, route finder, fares and planner UI

### Changed
- The app shell now switches between three views (calculator, EMI, metro)
  instead of toggling a single EMI flag

## [1.0.0] - 2026-08-05

### Added
- Complete scientific calculator implementation
- Basic arithmetic operations (+, -, ×, ÷)
- Scientific functions:
  - Trigonometric: sin, cos, tan, asin, acos, atan
  - Hyperbolic: sinh, cosh, tanh
  - Logarithmic: log, ln
  - Other: sqrt, abs, factorial, power
- Mathematical constants (π, e)
- Memory operations (M+, M-, MR, MC)
- Calculation history with persistence
- Light/Dark theme toggle
- Degree/Radian mode for trigonometric functions
- Keyboard support for all operations
- Responsive design for mobile, tablet, and desktop
- Landscape mode optimization
- Error handling and validation
- Haptic feedback for mobile devices
- Copy/paste support
- Comprehensive test suite
- Local storage for history and memory
- Scientific notation for large/small numbers
- PEMDAS/BODMAS compliant expression evaluation
- Accessibility features

### Features
- Modern, gradient-based UI design
- Smooth animations and transitions
- Multi-line expression display
- Real-time calculation
- Up to 50 calculations in history
- Precision settings (14 decimal places)
- Factorial support up to 170!
- Cross-browser compatibility
- PWA-ready architecture

### Technical
- Built with React 18
- mathjs for mathematical computations
- Component-based architecture
- CSS Grid and Flexbox layouts
- localStorage API integration
- Vibration API for haptic feedback
- Optimized performance with React.memo and useCallback
- Comprehensive unit and integration tests
