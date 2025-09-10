# 🚨 Crowd Safety - Emergency Management System

A professional, modern mobile application for crowd monitoring and emergency management during large-scale events, festivals, and gatherings.

## 📱 Features

### **Visitor App**
- **Home Dashboard**: Real-time crowd status (Green/Yellow/Red) with wait times and safe gate suggestions
- **SOS Emergency**: One-tap emergency reporting with three types:
  - 🚨 **Panic** - Immediate danger
  - 🏥 **Medical** - Health emergency  
  - 🔒 **Security** - Safety threat
- **Alerts Screen**: Real-time emergency notifications with blinking indicators for critical incidents
- **Profile Management**: User information and zone check-in functionality

### **Volunteer App**
- **Task Dashboard**: Active SOS reports with accept/resolve actions
- **Task Detail View**: Comprehensive emergency details with quick action buttons
- **Crowd Map View**: Zone and gate status overview with color-coded safety levels
- **Check-in System**: Zone presence tracking for volunteers

## 🎨 Design Features

- **Modern Material3 Design** with clean, minimalistic aesthetics
- **Safety-oriented Color Scheme**: Green (safe), Yellow (warning), Red (danger)
- **Professional UI/UX** with rounded cards, smooth gradients, and intuitive navigation
- **Responsive Layouts** optimized for mobile devices
- **Dark/Light Theme Toggle** for user preference
- **Smooth Animations** including blinking red indicators for critical alerts

## 🏗️ Architecture

- **Kotlin + Jetpack Compose** for modern Android development
- **MVVM Architecture** with ViewModels and StateFlow
- **Repository Pattern** with mock data implementation
- **Navigation Compose** for seamless screen transitions
- **Material3 Components** for consistent design language

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34 or later
- Kotlin 1.9.0 or later


### Configuration
- Update `app/src/main/res/values/strings.xml` with your Google Maps API key
- Configure signing for release builds in `app/build.gradle.kts`

## 📁 Project Structure

```
app/src/main/java/com/mahakumbh/crowdsafety/
├── data/           # Data models and repository
├── di/             # Dependency injection
├── ui/             # UI components and screens
├── vm/             # ViewModels
└── MainActivity.kt # Main application entry point
```

## 🔧 Technical Details

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Build Tools**: Gradle 8.0+
- **Dependencies**: 
  - Jetpack Compose BOM 2024.06.00
  - Material3 1.2.1
  - Navigation Compose 2.7.7
  - Google Maps Compose 4.3.0

## 📸 Screenshots

*[Add screenshots of your app here]*

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Team

- **Developer**: [Ragul]
- **Project**: Crowd Safety Emergency Management System
- **Version**: 1.0.0

## 📞 Support

For support and questions, please open an issue on GitHub or contact [ragult.vlsi2023@citchennai.net]

---

**Built with ❤️ for safer crowd management**
"# crowd-safety-app-firebase" 
