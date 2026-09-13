# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-09-13

The app is now a Bengaluru Namma Metro travel app. The scientific and EMI
calculators have been removed from the shell; they remain in git history.

### Added
- **Map tab** — schematic of all three lines with real compass orientation,
  the planned route drawn over the dimmed network, and tap-a-station actions.
  Station coordinates are derived so the interchanges land exactly where the
  lines cross.
- **Stations tab** — searchable directory of all 85 stations with per-station
  pages: lines served, position along each line, distance from the terminal,
  neighbouring stations, and first/last train times per direction for weekdays
  and Sundays.
- **Saved commute** — set home and work once for one-tap morning and evening
  trips; save individual stations for quick access.
- Three-tab app shell (Plan / Map / Stations) with a mobile-first bottom bar.
- Design tokens with a full light and dark palette, following the system
  preference on first visit.
- `useLocalStorage` — persistence that degrades gracefully where storage is
  blocked rather than breaking the app.

### Changed
- Journey planner rebuilt as a controlled component sharing state with the map
  and station pages, so a route planned in one tab is reflected in the others.
- Line colours adjusted to stay legible on both light and dark grounds.
- Purple Line map orientation corrected — Whitefield is east, Challaghatta west.

### Removed
- Scientific calculator, EMI calculator and their components, utilities and
  tests. The `mathjs` dependency is no longer needed.

## [1.1.0] - Namma Metro planner (unreleased)

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
