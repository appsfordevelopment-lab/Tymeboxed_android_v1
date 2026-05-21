# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing credentials in env vars or local.properties)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

No tests exist yet. The test runner is configured (`AndroidJUnitRunner`) but no test source sets are populated.

## Secrets & Signing

Secrets are read from environment variables first, then `local.properties`. Never hard-code them.

- `GOOGLE_WEB_CLIENT_ID` — Google Sign-In
- `TYMEBOXED_KEYSTORE_FILE`, `TYMEBOXED_KEYSTORE_PASSWORD`, `TYMEBOXED_KEY_ALIAS`, `TYMEBOXED_KEY_PASSWORD` — release signing (falls back to `KEYSTORE_*` variants)

Debug builds use `.debug` application ID suffix and require no signing config.

## Architecture

**Single-module Android app** (`app/`) using Kotlin, Jetpack Compose, and MVVM.

**Package:** `dev.ambitionsoftware.tymeboxed`

### Layer Overview

- **`ui/`** — Compose screens, ViewModels, navigation (`TymeBoxedNavHost`), theme (Material 3). Single-activity architecture (`MainActivity`).
- **`domain/`** — Pure models (`Profile`, `Session`, `BlockingStrategyId`) and blocking strategy logic.
- **`data/`** — Room database (`TymeBoxedDatabase`, v5), DAOs, repositories (`ProfileRepository`, `SessionRepository`, `AuthRepository`), DataStore preferences.
- **`service/`** — Background services: `AppBlockerAccessibilityService` (blocking engine), `SessionBlockerService` (foreground notification), alarm receivers, `ActiveBlockingState` (in-memory singleton), `BlockingStateRestorer` (cold-start rehydration).
- **`di/`** — Hilt modules and entry points (for injecting into non-Hilt-managed components like services/receivers).
- **`auth/`** — Google Sign-In flow.
- **`nfc/`** — NFC tag reading.
- **`oem/`** — OEM-specific workarounds (HyperOS, MIUI, etc.).
- **`permissions/`** — Permission coordination and models.
- **`admin/`** — Device admin receiver for strict-mode protection.

### Key Patterns

- **Hilt DI** everywhere. Use `@AndroidEntryPoint` on activities/fragments/services, `@HiltViewModel` on ViewModels. Non-Hilt components (accessibility service, broadcast receivers) use `EntryPointAccessors` — see `di/` for existing entry points.
- **Room** with KSP. Schemas exported to `app/schemas/`. Migrations v1-v2 are destructive; v3+ require proper `Migration` objects in `Migrations.kt`.
- **Coroutines + Flow** for async. Repositories expose `Flow`s observed by ViewModels.
- **DataStore** (not SharedPreferences) for user preferences in `data/prefs/`.
- **Compose Navigation** with type-safe routes defined in `ui/navigation/`.

### Build Configuration

- Kotlin 2.0.21, AGP 8.5.2, JVM 17
- minSdk 28, targetSdk/compileSdk 34
- Dependency versions centralized in `gradle/libs.versions.toml`
- KSP for annotation processing (Hilt, Room)
