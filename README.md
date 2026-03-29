# Runner2D (Android)

Runner2D is a lightweight 2D endless runner built with Kotlin + Jetpack Compose.
The project was created as a clean, extendable base for experimenting with mobile game loops, collision handling, and responsive UI on Android.

## Features

- Tap-to-jump gameplay
- Procedural obstacle spawning
- Collision detection and game over state
- Score + best score tracking (runtime session)
- Progressive difficulty scaling
- Parallax background layers (stars, hills, clouds)
- Pause / resume controls

## Tech Stack

- Kotlin 2.0
- Jetpack Compose (Canvas rendering)
- Android Gradle Plugin 8.9
- Min SDK 24, Target SDK 35

## Project Structure

- `app/src/main/java/com/example/runner2d/MainActivity.kt`: App entry point
- `app/src/main/java/com/example/runner2d/GameScreen.kt`: Game loop, rendering, physics, input
- `app/src/main/java/com/example/runner2d/ui/theme/*`: Compose theme setup

## Requirements

- JDK 17+
- Android Studio (latest stable recommended)
- Android SDK installed locally

## Run Locally

1. Open the project folder in Android Studio:
   - `C:\AppAndroid\Runner2D`
2. Ensure Gradle JDK is set to Java 17.
3. Ensure Android SDK is configured (`local.properties` with `sdk.dir=...`).
4. Run the `app` configuration on an emulator or physical device.

### CLI Build

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\\bin;$env:Path"
.\\gradlew.bat :app:assembleDebug -x test
```

APK output:
- `app/build/outputs/apk/debug/app-debug.apk`

## Controls

- `Tap` while running: jump
- `Tap` on start screen: start game
- `Tap` on game over screen: restart game
- `Pause button` (top-right): pause/resume

## Current Limitations

- No persistent high score (not stored across app restarts)
- Placeholder vector-like shapes instead of spritesheets
- No sound effects yet

## Roadmap

- Add audio SFX and optional vibration feedback
- Add persistent best score with DataStore
- Add sprite animations and simple enemy variants
- Add menu/settings (audio on/off, haptics on/off)
- Add lightweight tests for game utility logic

## License

No license specified yet.
