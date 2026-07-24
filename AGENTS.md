# AGENTS.md — Password Manager (vlt)

Android password manager built with Kotlin + Jetpack Compose + Room + Biometric Auth.

## Build / Test / Lint

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests (JVM, no device needed)
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew app:testDebugUnitTest --tests "io.zx.password.ExampleUnitTest"

# Lint
./gradlew lint

# Clean build
./gradlew clean
```

- Gradle 9.4.0 via wrapper (`./gradlew`).
- Kotlin 2.3.20; KSP for Room annotation processing.
- Chained mirror repos (Aliyun + Tencent Cloud) for Maven/Gradle — already configured in `settings.gradle.kts`.
- `app/debug/` and `app/release/` contain pre-built APKs checked into the repo.

## Architecture

MVVM + Repository pattern, single-module Gradle project.

```
app/
├── src/main/java/io/zx/password/
│   ├── Pwd.kt                 # Room @Entity — one password record
│   ├── PwdDao.kt              # Room @Dao — CRUD + Flow queries
│   ├── PwdDB.kt               # Room @Database singleton, file "passwd"
│   ├── PwdRepository.kt       # Thin wrapper around PwdDao
│   ├── PwdViewModel.kt        # ViewModel with UiState sealed class
│   ├── PwdViewModelFactory.kt # Factory injecting PwdDB → DAO → repo
│   ├── MainActivity.kt        # Entry point, biometric lock/unlock
│   └── ui/
│       ├── theme/             # Color/Type/Theme + DataStore theme prefs
│       ├── layout/            # Screens: Home, Search, Setting + bottom nav
│       └── component/         # Reusable dialogs & widgets
```

**Data flow**:
`Room (PwdDB)` → `PwdDao (Flow)` → `PwdRepository` → `PwdViewModel (StateFlow)` → Compose UI

**Biometric lock flow**:
`MainActivity.onResume()` locks → `MainViewModel.isUnlocked = false` → lock overlay shown → biometric prompt → success → `unlock()` → overlay removed.

## Key Files & Directories

| Path | Purpose |
|------|---------|
| `gradle/libs.versions.toml` | Single version catalog for all dependencies |
| `app/build.gradle.kts` | App module config: SDK levels, enable Compose, dependencies |
| `app/src/main/AndroidManifest.xml` | Permissions (`USE_BIOMETRIC`), launcher activity |
| `app/src/main/java/io/zx/password/MainActivity.kt` | App entry, biometric auth, theme setup, lock screen |
| `app/src/main/java/io/zx/password/PwdViewModel.kt` | All password CRUD logic via `UiState` sealed class |
| `app/src/main/java/io/zx/password/PwdDB.kt` | Room DB singleton (companion `getInstance()`) |
| `app/src/main/java/io/zx/password/ui/theme/ThemeManager.kt` | `ThemePreferences` (DataStore), `ThemeState`, `ThemeMode` enum |
| `app/src/main/java/io/zx/password/ui/layout/MainScreen.kt` | `NavHost` with 3 routes + `BottomNavigationBar` |
| `app/src/main/java/io/zx/password/ui/component/` | `InfoDialog`, `EditPwdDialog`, `CommonDialog`, `UpdateAppDialog`, `SwipeableItem` |

Resource XML:
- `res/values/strings.xml` — only `app_name = "password"`
- `res/values/themes.xml` — `Theme.Password` (parent `Material.Light.NoActionBar`)
- `res/xml/backup_rules.xml` / `data_extraction_rules.xml` — default templates, not customized

## Coding Conventions

- **Language**: All comments and UI strings are in Chinese (zh-CN).
- **Package**: `io.zx.password` for data/business; `io.zx.password.ui.{theme,layout,component}` for UI.
- **ViewModel**: `PwdViewModel` uses a sealed class `UiState` pattern (Loading / Success / Error). All mutations go through `viewModelScope.launch` with suspend DAO calls. Room Flows auto-refresh the UI.
- **Factory**: `PwdViewModelFactory` builds the dependency chain `DB → DAO → Repository → ViewModel`. Screens use `viewModel(factory = PwdViewModelFactory(context))`.
- **Theme**: Custom `ExtendedColors` via `staticCompositionLocalOf` for card/special colors beyond Material3. `LocalThemeState` is a separate `CompositionLocal` carrying `ThemeState` (theme mode + callback). Theme mode persisted via DataStore Preferences.
- **Navigation**: `NavHost` with 3 bottom-nav routes ("home", "search", "setting") defined as a `sealed class BottomNavItem`. Brief fade transitions (100ms) on every nav action.
- **Dialogs**: Custom `Dialog` composables (`InfoDialog`, `CommonDialog`, `UpdateAppDialog`) use `DialogProperties(dismissOnClickOutside = true)`. `EditPwdDialog` uses Material3 `AlertDialog`.
- **Naming**: CamelCase for composables (`HomeScreen`, `MainScreen`); PascalCase for data classes (`Pwd`); lowercase with underscores for DB names ("passwd"). Composables that are entry points are `public`; helpers like `PwdItemCard` are `private`.
- **No DI framework** — manual dependency injection through factory classes.

## Git Workflow

- **Remote**: `git@github.com:zxionf/vlt.git`
- **Branch**: `main` only; no feature branches observed in recent history.
- **Commit style**: Loose Chinese-language messages ("排除不必要的文件夹", "添加简单弹窗，添加禁止截图"). No conventional commits or issue references.

## Tips for AI Agents

- **Add a new screen**: Create composable in `ui/layout/`, add a route in `BottomNavItem` sealed class, add a `composable()` block in `NavigationGraph()` inside `MainScreen.kt`.
- **Add a DB column**: Edit `Pwd.kt` entity → bump `@Database(version = X)` in `PwdDB.kt` → add migration (or use `fallbackToDestructiveMigration()` during dev).
- **SearchScreen is not wired to DB**: Currently uses `sampleItems` hardcoded list. To connect: inject `PwdViewModel` and replace `sampleItems` with `viewModel.items`.
- **Biometric lock protects the whole app**: `MainActivity` has `FLAG_SECURE` commented out (line commented in `onCreate`). `onResume`/`onPause` both call `lock()`, so app always shows lock when returning.
- **Dependency versions** live in `gradle/libs.versions.toml` — change them there, not in build files.
- **Pre-built APKs**: `app/debug/app-debug.apk` and `app/release/app-release.apk` are in the repo. Running `assembleDebug` will overwrite them. They are not `.gitignore`'d — avoid commit noise.
- **Theme colors**: Custom colors (card backgrounds, etc.) are in `Theme.kt` via `LightExtendedColors`/`DarkExtendedColors`. `PwdTheme.colors` is the shorthand accessor (singleton object, not function).
- **Test directory**: Unit tests in `app/src/test/`, instrumented tests in `app/src/androidTest/`. Currently only skeleton tests exist.
