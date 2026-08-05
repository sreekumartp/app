# Contributing to Scientific Calculator

Thank you for your interest in contributing to the Scientific Calculator project! This document provides guidelines and information for contributors.

## How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with:
- A clear, descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Screenshots (if applicable)
- Browser and OS information

### Suggesting Enhancements

Enhancement suggestions are welcome! Please create an issue with:
- A clear, descriptive title
- Detailed description of the proposed feature
- Rationale for why this would be useful
- Examples of how it would work

### Pull Requests

1. Fork the repository
2. Create a new branch (`git checkout -b feature/AmazingFeature`)
3. Make your changes
4. Write or update tests as needed
5. Ensure all tests pass (`npm test`)
6. Ensure the build succeeds (`npm run build`)
7. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
8. Push to the branch (`git push origin feature/AmazingFeature`)
9. Open a Pull Request

## Development Setup

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

4. Run tests:
```bash
npm test
```

## Code Style Guidelines

### JavaScript/React
- Use functional components with hooks
- Use meaningful variable and function names
- Add comments for complex logic
- Keep components focused and single-purpose
- Use PropTypes or TypeScript for type checking (future enhancement)

### CSS
- Use BEM or component-scoped naming
- Mobile-first responsive design
- Support both light and dark themes
- Use CSS variables for consistency
- Ensure accessibility (contrast ratios, focus states)

### Testing
- Write tests for new features
- Maintain or improve code coverage
- Test both success and error cases
- Test edge cases

## Project Structure

```
src/
├── components/       # React components
├── utils/           # Utility functions
├── App.js           # Main application
├── App.css          # Main styles
└── index.js         # Entry point
```

## Commit Message Guidelines

Use clear, descriptive commit messages:
- `feat: Add new feature`
- `fix: Fix bug in calculation`
- `docs: Update README`
- `style: Format code`
- `refactor: Refactor component`
- `test: Add tests for feature`
- `chore: Update dependencies`

## Testing Requirements

All contributions should include appropriate tests:
- Unit tests for utility functions
- Component tests for UI components
- Integration tests for user flows

Run tests with:
```bash
npm test
```

## Documentation

Please update documentation when:
- Adding new features
- Changing existing behavior
- Modifying APIs or interfaces

## Code Review Process

1. All submissions require review
2. Reviewers will check:
   - Code quality and style
   - Test coverage
   - Documentation updates
   - Performance implications
   - Accessibility considerations

## Questions?

Feel free to open an issue for any questions or clarifications.

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

## Thank You!

Your contributions help make this project better for everyone!
