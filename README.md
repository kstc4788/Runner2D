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
- Daily mission system (3 missions):
  - Reach score 120
  - Perform 25 jumps
  - Collect 3 power-ups
- Game-over results panel with run stats
- Sprite-like animated player rendering via Compose Canvas
- Ads monetization skeleton:
  - Banner on static screens (start/pause/game-over)
  - Interstitial every 4 completed runs
  - Rewarded ad for one second-chance revive per run

## Tech Stack

- Kotlin 2.0
- Jetpack Compose (Canvas rendering)
- Android Gradle Plugin 8.9
- Google Mobile Ads SDK (AdMob)
- Min SDK 24, Target SDK 35

## Project Structure

- `app/src/main/java/com/example/runner2d/MainActivity.kt`: app entry point + MobileAds init
- `app/src/main/java/com/example/runner2d/GameScreen.kt`: game loop, rendering, physics, input, power-ups, missions, ads hooks
- `app/src/main/java/com/example/runner2d/AdsManager.kt`: banner/interstitial/rewarded manager
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
- `Watch ad for second chance` on game-over panel (if rewarded ad is loaded)

## Ads Notes

- The project currently uses Google official test ad unit IDs.
- Before production release, replace test IDs with your own AdMob unit IDs.
- Add consent flow (GDPR/EEA) and age handling before enabling live ads.

## Current Limitations

- Art is still code-drawn (no external sprite atlas yet)
- No online leaderboard yet
- No cloud save/profile system yet
- Consent UX not implemented yet (required for production monetization)

## Roadmap

- Replace procedural shapes with sprite sheets and frame animations
- Add cosmetic unlocks tied to missions
- Add leaderboard integration (Firebase)
- Add release build pipeline + store-ready assets
- Integrate consent SDK and remote-config ad tuning

## License

No license specified yet.
