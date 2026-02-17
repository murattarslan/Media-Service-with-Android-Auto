# Media Service with Android Auto

## Project Overview

The Media Service with Android Auto project aims to create a media playback service for Android Auto, allowing users to enjoy a seamless media experience while driving. This project leverages modern Android development practices and follows the best coding guidelines to ensure maintainability and performance.

## Project Structure

```
Media-Service-with-Android-Auto/
├── app/                   # Main application module
│   ├── src/              # Source set for the app
│   │   ├── main/         # Main source set
│   │   └── test/         # Unit tests
│   ├── build.gradle       # Gradle build file for the application
│   └── proguard-rules.pro # Proguard rules
├── libraries/             # External and internal libraries
├── docs/                  # Documentation files
└── build.gradle           # Project level build file
```

## Setup

To set up the project, you need to have Android Studio installed. Follow these steps:
1. **Clone the repository:**  
   ```git
   git clone https://github.com/murattarslan/Media-Service-with-Android-Auto.git
   cd Media-Service-with-Android-Auto
   ```  
2. **Open the project:**  
   - Launch Android Studio and open the cloned project directory.
3. **Install dependencies:**  
   - Android Studio will prompt you to install any missing SDK components. Follow the prompts to install them.
4. **Build the project:**  
   - Click on `Build > Make Project` in the Android Studio menu to build the project.

## Usage

To use the Media Service:
1. **Run the application:  
   - Connect your Android device to your computer.
   - Enable USB debugging on your phone.
   - Click on `Run > Run 'app'` in Android Studio.
2. **Interact with the service:**  
   - Use the app interface to control media playback, or connect it to an Android Auto compatible device for UI adaptation.

## Features
- **Media Playback:**  
   - Supports various media formats and provides controls for play, pause, skip, rewind, and fast-forward.
- **Android Auto Integration:**  
   - Offers a user-friendly interface specifically designed for safe interactions while driving.
- **Background Playback:**  
   - Allows media to be played in the background while the user is interacting with other apps.
- **Notifications:**  
   - Provides notifications to control playback without opening the app.

## Libraries Used
- **Retrofit:**  
   - For networking operations.
- **Glide:**  
   - For image loading and caching.
- **ExoPlayer:**  
   - For media playback.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.