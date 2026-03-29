# Runner2D (Android)

Runner2D is a lightweight 2D endless runner built with Kotlin + Jetpack Compose.
The project is structured as an extendable base for gameplay loops, collision systems, and polished UI/game-feel iterations on Android.

## Features

- Tap-to-jump gameplay
- Procedural obstacle spawning
- Collision detection and game over state
- Score + persistent best score tracking
- Progressive difficulty scaling
- Parallax background layers (stars, hills, clouds)
- Pause / resume controls
- In-game toggles: audio ON/OFF, haptics ON/OFF (persisted)
- Power-ups:
  - Shield (absorbs one hit)
  - Slow Motion (temporary speed reduction)
  - Double Jump (temporary air-jump unlock)
- Sprite-like animated player rendering via Compose Canvas

## Tech Stack

- Kotlin 2.0
- Jetpack Compose (Canvas rendering)
- Android Gradle Plugin 8.9
- Min SDK 24, Target SDK 35

## Project Structure

- `app/src/main/java/com/example/runner2d/MainActivity.kt`: app entry point
- `app/src/main/java/com/example/runner2d/GameScreen.kt`: game loop, rendering, physics, input, power-ups
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
4. Run the `app` configuration on emulator or physical device.

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
- `Tap` mid-air with Double Jump active: second jump
- `Tap` on start screen: start game
- `Tap` on game over screen: restart game
- `Pause button` (top-right): pause/resume + settings panel

## Current Limitations

- Art is still code-drawn (no external sprite atlas yet)
- No online leaderboard yet
- No cloud save/profile system yet

## Roadmap

- Replace procedural shapes with sprite sheets and frame animations
- Add mission system and cosmetic unlocks
- Add leaderboard integration (Firebase)
- Add release build pipeline + store-ready assets

## License

No license specified yet.
