# Contributing to Crowd Safety Emergency Management System

Thank you for your interest in contributing to the Crowd Safety Emergency Management System! This document provides guidelines and information for contributors.

## 🤝 How to Contribute

### **Reporting Issues**
- Use the GitHub issue tracker
- Provide detailed descriptions of the problem
- Include steps to reproduce the issue
- Add screenshots or logs when relevant
- Check if the issue has already been reported

### **Feature Requests**
- Describe the feature you'd like to see
- Explain why this feature would be useful
- Provide use cases and examples
- Consider the impact on existing functionality

### **Code Contributions**
- Fork the repository
- Create a feature branch from `develop`
- Make your changes following the coding standards
- Write tests for new functionality
- Update documentation as needed
- Submit a pull request

## 🏗️ Development Setup

### **Prerequisites**
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34 or later
- Kotlin 1.9.0 or later
- JDK 17

### **Getting Started**
1. Fork and clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build the project to ensure everything works
5. Create a feature branch: `git checkout -b feature/your-feature-name`

## 📝 Coding Standards

### **Kotlin Style Guide**
- Follow the [Official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Use proper indentation (4 spaces)
- Add KDoc comments for public APIs

### **Compose Best Practices**
- Follow Material3 design guidelines
- Use proper state management patterns
- Implement proper error handling
- Ensure accessibility compliance
- Optimize for performance

### **Architecture Guidelines**
- Follow MVVM pattern
- Use Repository pattern for data access
- Implement proper separation of concerns
- Use StateFlow for reactive data streams
- Keep ViewModels lightweight

## 🧪 Testing Requirements

### **Unit Tests**
- Write tests for all ViewModels
- Test repository implementations
- Cover edge cases and error scenarios
- Maintain minimum 80% code coverage
- Use descriptive test names

### **UI Tests**
- Test critical user flows
- Verify accessibility features
- Test different screen sizes
- Ensure proper error handling

### **Running Tests**
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.mahakumbh.crowdsafety.vm.TasksViewModelTest"

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

## 📚 Documentation

### **Code Documentation**
- Add KDoc comments for public functions
- Document complex business logic
- Explain non-obvious implementation details
- Keep comments up-to-date with code changes

### **Project Documentation**
- Update README.md for new features
- Add examples and usage instructions
- Document configuration changes
- Update API documentation

## 🔄 Pull Request Process

### **Before Submitting**
1. Ensure all tests pass
2. Run linting: `./gradlew lint`
3. Update documentation if needed
4. Squash commits into logical units
5. Write a clear PR description

### **PR Description Template**
```markdown
## Description
Brief description of the changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] UI tests added/updated
- [ ] Manual testing completed

## Screenshots (if applicable)
Add screenshots for UI changes

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests added/updated
```

### **Review Process**
- All PRs require at least one review
- Address review comments promptly
- Request reviews from appropriate team members
- Be open to feedback and suggestions

## 🚀 Release Process

### **Versioning**
- Follow [Semantic Versioning](https://semver.org/)
- Update version in `build.gradle.kts`
- Update CHANGELOG.md
- Create release notes

### **Release Checklist**
- [ ] All tests passing
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version numbers updated
- [ ] Release notes prepared
- [ ] APK built and tested

## 🐛 Bug Reports

### **Bug Report Template**
```markdown
## Bug Description
Clear description of the bug

## Steps to Reproduce
1. Step 1
2. Step 2
3. Step 3

## Expected Behavior
What should happen

## Actual Behavior
What actually happens

## Environment
- Device: [e.g., Pixel 6]
- Android Version: [e.g., Android 14]
- App Version: [e.g., 1.0.0]

## Additional Information
Screenshots, logs, or other relevant information
```

## 📞 Getting Help

### **Communication Channels**
- GitHub Issues for bugs and feature requests
- GitHub Discussions for questions and ideas
- Pull Request reviews for code feedback

### **Community Guidelines**
- Be respectful and constructive
- Help others when possible
- Share knowledge and best practices
- Follow the project's code of conduct

## 🎯 Contribution Areas

### **High Priority**
- Bug fixes and stability improvements
- Performance optimizations
- Accessibility enhancements
- Security improvements

### **Medium Priority**
- New features and enhancements
- UI/UX improvements
- Documentation updates
- Test coverage improvements

### **Low Priority**
- Code refactoring
- Style improvements
- Minor optimizations
- Experimental features

## 📋 Code Review Checklist

### **Functionality**
- [ ] Does the code do what it's supposed to do?
- [ ] Are edge cases handled properly?
- [ ] Is error handling implemented?
- [ ] Are there any security concerns?

### **Code Quality**
- [ ] Is the code readable and maintainable?
- [ ] Are naming conventions followed?
- [ ] Is the code properly documented?
- [ ] Are there any code smells?

### **Testing**
- [ ] Are tests comprehensive?
- [ ] Do tests cover edge cases?
- [ ] Are tests readable and maintainable?
- [ ] Is test coverage adequate?

### **Performance**
- [ ] Are there any performance issues?
- [ ] Is memory usage optimized?
- [ ] Are there any unnecessary operations?
- [ ] Is the UI responsive?

---

Thank you for contributing to making crowd management safer! 🚨✨
