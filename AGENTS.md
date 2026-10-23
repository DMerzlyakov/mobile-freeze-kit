# Repository Guidelines

## Purpose & Module Design

MobileFreezeKit is a collection of reusable library modules for use across the owner's projects. Extract recurring approaches and proven implementations into focused modules that can be adopted independently.

- Build new libraries with Kotlin Multiplatform (KMP) by default, targeting reuse in KMP projects.
- Keep reusable logic and public contracts in `commonMain`; isolate platform APIs in platform source sets. Use `expect`/`actual` when platform implementations are necessary.
- Give each module a clear responsibility and minimal dependencies. Avoid coupling libraries to application entry points, project-specific business logic, or unrelated modules.
- Add Compose dependencies only to modules that provide UI. Keep non-UI libraries usable without Compose.
- Define supported targets explicitly for each module; do not claim support for unimplemented or unverified platforms.
- Prefer extending an existing module when the responsibility matches. Do not introduce speculative abstractions or modules without a concrete reuse case.

## Project Structure & Module Organization

The current scaffold includes shared Compose UI and Android, iOS, and Desktop (JVM) application entry points. Application modules are template placeholders and may remain empty; demos are optional and added only when needed or explicitly requested. Keep reusable libraries independent of these applications. Do not create demos as a required part of library development.

- `shared/src/commonMain/kotlin/com/dmerzliakov/freezekit/`: shared UI and logic; shared assets live in `commonMain/composeResources/`.
- `shared/src/androidMain/`, `iosMain/`, and `jvmMain/`: platform implementations, including `expect`/`actual` declarations.
- `androidApp/`: Android launcher, manifest, and platform resources.
- `desktopApp/`: JVM entry point and desktop packaging configuration.
- `iosApp/`: Xcode project, SwiftUI entry point, and asset catalogs.
- `gradle/libs.versions.toml`: dependency and plugin versions.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper. Examples use PowerShell; on macOS, replace `.\gradlew.bat` with `./gradlew`.

- `.\gradlew.bat :androidApp:assembleDebug`: build the Android debug APK.
- `.\gradlew.bat :desktopApp:run`: launch the desktop application.
- `.\gradlew.bat :shared:testAndroidHostTest`: run shared Android host tests.
- `.\gradlew.bat :shared:jvmTest`: run shared desktop tests.
- `./gradlew :shared:iosSimulatorArm64Test`: run iOS simulator tests on macOS.

Open `iosApp/` in Xcode to build and run iOS. The Gradle daemon configuration requests JDK 21; JVM compilation targets Java 11. Android SDK paths belong in `local.properties`.

## Coding Style & Naming Conventions

Follow existing Kotlin formatting with four-space indentation. Use `PascalCase` for classes and composable functions, `camelCase` for ordinary functions and properties, and lowercase package names. Keep packages under `com.dmerzliakov.freezekit`. Put reusable code in `commonMain` and platform APIs in their corresponding source sets. Manage dependency versions through the version catalog. No dedicated formatter or lint plugin is configured; use IDE formatting.

## Testing Guidelines

Tests use `kotlin.test` and live in `commonTest`, `androidHostTest`, `jvmTest`, and `iosTest`. Name test classes `*Test` and use descriptive test methods. Add meaningful behavioral assertions for changed logic. No coverage threshold is configured. Run the tests for affected platforms and report any unavailable platform checks.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout, so no existing commit convention can be established. Use concise, imperative commit subjects. Pull requests should describe the change, link relevant issues, list verification commands and results, and include screenshots for UI changes.

## Configuration Hygiene

Keep credentials and machine-specific paths out of tracked files. Preserve exclusions for `local.properties`, build outputs, and IDE state.
