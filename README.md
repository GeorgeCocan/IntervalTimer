# Interval Timer

A single-screen Android app built with Kotlin and Jetpack Compose for timed gym interval workouts (work and rest phases across configurable rounds), with audio beeps, vibration cues, and persistent workout presets.

## Features

- **Configurable intervals**: Set work duration, rest duration, and total rounds (`+` / `-` buttons adjust time in 5-second increments and rounds by 1).
- **Audio & haptic cues**: Countdown beeps during the final 5 seconds of a phase plus tone and vibration feedback on phase transitions.
- **Process-death resilient timer**: Countdown state (`workTime`, `restTime`, `totalRounds`, `state`, `currentRound`, `currentPhase`, `timeLeft`) is backed by `SavedStateHandle.saveable` inside `TimerViewModel` and runs in `viewModelScope`.
- **Saved presets**: 4 preset slots persisted locally via `SharedPreferences` (`PresetManager`).
- **Always-on portrait workout display**: Keeps the screen awake (`FLAG_KEEP_SCREEN_ON`) during active workouts.

## Tech Stack

| Component | Version |
|---|---|
| Language | Kotlin `2.2.10` |
| UI | Jetpack Compose (Material 3), Compose BOM `2026.02.01` |
| Android Gradle Plugin | `9.2.1` |
| Gradle Wrapper | `9.4.1` |
| `compileSdk` / `targetSdk` | `37` |
| `minSdk` | `24` |
| Lifecycle / ViewModel | `androidx.lifecycle` `2.11.0` |

## Project Structure

```text
IntervalTimer/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/intervaltimer/
│     │  ├─ MainActivity.kt         # MainActivity, TimerViewModel, PresetManager, and Compose UI
│     │  └─ ui/theme/               # Theme scaffolding
│     └─ res/                       # Strings, themes, and adaptive launcher icons
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle/libs.versions.toml
└─ LICENSE.md
```

## Building & Installing

Compile or assemble the debug APK using the Gradle wrapper:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` and can be installed to a connected device via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

Released under the [MIT License](LICENSE.md). Anyone is free to fork, use, modify, and distribute this project as they like.
