# Android Gradle Build Instructions

## Prerequisites
- JDK 11 or higher
- Android SDK
- Android Studio (recommended) or command-line tools

## Build Commands

### Using Gradle Wrapper (Recommended)

**Windows:**
```bash
.\gradlew.bat build
```

**Linux/Mac:**
```bash
chmod +x gradlew
./gradlew build
```

### Common Gradle Tasks

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Generate lint report
./gradlew lint
```

## Project Structure

```
SMS-Gateway-Automation/
├── app/
│   ├── build.gradle              # App module configuration
│   ├── proguard-rules.pro        # ProGuard rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/gateway/sms/  # Java source files
│           └── res/                    # Resources (layouts, strings, etc.)
├── build.gradle                  # Project-level configuration
├── settings.gradle               # Project settings
├── gradle.properties             # Gradle properties
├── gradlew                       # Gradle wrapper script (Unix)
└── gradlew.bat                   # Gradle wrapper script (Windows)
```

## Opening in Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the project root directory
4. Click "OK"
5. Wait for Gradle sync to complete

## Building APK

The APK will be generated in:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

**Gradle sync failed:**
- Ensure JDK 11+ is installed
- Check internet connection (downloads dependencies)
- Run `./gradlew clean build --refresh-dependencies`

**Permission denied on gradlew:**
```bash
chmod +x gradlew
```

**Missing Android SDK:**
- Set ANDROID_HOME environment variable
- Or install via Android Studio

## Next Steps

After successful build:
1. Install APK on Android device
2. Grant required permissions (SMS, Phone, Accessibility)
3. Enable Accessibility Service in Android Settings
4. Start the Gateway Service
