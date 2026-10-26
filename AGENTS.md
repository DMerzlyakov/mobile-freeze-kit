# Repository Guidelines

## Purpose & Module Design

MobileFreezeKit is a collection of reusable library modules for use across the owner's projects. Extract recurring approaches and proven implementations into focused modules that can be adopted independently.

- Build new libraries with Kotlin Multiplatform (KMP) by default, targeting reuse in KMP projects.
- Keep reusable logic and public contracts in `commonMain`; isolate platform APIs in platform source sets. Use `expect`/`actual` when platform implementations are necessary.
- Give each module a clear responsibility and minimal dependencies. Avoid coupling libraries to application entry points, project-specific business logic, or unrelated modules.
- Add Compose dependencies only to modules that provide UI. Keep non-UI libraries usable without Compose.
- Define supported targets explicitly for each module; do not claim support for unimplemented or unverified platforms.
- Prefer extending an existing module when the responsibility matches. Do not introduce speculative abstractions or modules without a concrete reuse case.

## Documentation Audience

- Keep `README.md` a public user guide to MobileFreezeKit: purpose, available modules, dependency setup, supported platforms, API examples, behavior, and limitations relevant to consumers.
- Show how to consume published artifacts from Maven Central. Keep dependency coordinates and examples aligned with the release being documented; do not claim an unpublished version is available.
- Do not add maintainer instructions to `README.md`: repository builds and tests, release procedures, version management, publishing plugins, CI configuration, credentials, signing keys, or scaffold application details.
- Keep repository and maintainer instructions in `AGENTS.md`. Do not create separate maintenance documents unless requested.

## Project Structure & Module Organization

The reusable `mvi` library provides the MVI presentation contracts, ViewModel, screen data exchange, and Compose lifecycle/event integration. It targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`; iOS verification requires macOS.

Presentation: freezekit-mvi

Apply personal `my-android-workflows` skills: `kotlin-style` and `presentation-freezekit-mvi` for affected code, `test` for test formatting, and `verification-policy` for checks. The `mvi` module is presentation infrastructure, not an application domain/data layer; do not add UseCase/Repository layers merely to match an application template. Read the actual library API before changing its contracts.

Apply presentation rules to the library infrastructure; do not add application screens or navigation to the library.

The current scaffold includes empty Android, iOS, and Desktop (JVM) application entry points. Application modules are template placeholders; demos are optional and added only when needed or explicitly requested. The `mvi` library is independent of these applications. Keep reusable libraries independent of application entry points. Do not create demos as a required part of library development.

- `androidApp/`: Android launcher, manifest, and platform resources.
- `desktopApp/`: JVM entry point and desktop packaging configuration.
- `iosApp/`: Xcode project, SwiftUI entry point, and asset catalogs.
- `gradle/libs.versions.toml`: dependency and plugin versions.
- `mvi/src/commonMain/kotlin/com/dmerzliakov/freezekit/mvi/`: reusable MVI contracts and implementations.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper. Examples use PowerShell; on macOS, replace `.\gradlew.bat` with `./gradlew`.

- `.\gradlew.bat :mvi:compileKotlinJvm :mvi:compileAndroidMain`: compile the library for JVM and Android.
- `.\gradlew.bat :mvi:jvmTest :mvi:testAndroidHostTest`: run library JVM and Android host tests.
- `./gradlew :mvi:iosSimulatorArm64Test`: run library iOS simulator tests on macOS.
- `.\gradlew.bat :androidApp:assembleDebug`: build the Android debug APK.
- `.\gradlew.bat :desktopApp:run`: launch the desktop application.

Open `iosApp/` in Xcode to build and run iOS. The Gradle daemon configuration requests JDK 21; JVM compilation targets Java 11. Android SDK paths belong in `local.properties`.

## Release Maintenance

- `version.properties` is the single source of the shared release version. The root Gradle script assigns it to all projects; library modules inherit `project.version` without declaring their own versions.
- Every release publishes all libraries configured for Maven Central, including unchanged modules. New libraries need their publishing plugin, artifact ID, POM metadata, signing configuration, and applicable workflow checks.
- Publishing uses `com.vanniktech.maven.publish` and Dokka directly. Publish sources with existing KDoc and generated Dokka documentation alongside the platform artifacts.
- `.github/workflows/release.yml` runs manually on macOS for an existing `v<version>` tag. It checks the tag against `version.properties`, runs the configured platform and artifact checks, then invokes `publishAndReleaseToMavenCentral` from the root without a module prefix. A push to `main` does not publish a release.
- GitHub Actions secrets are `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` from a Central Portal User Token, plus `SIGNING_KEY` (ASCII-armored private OpenPGP key) and `SIGNING_PASSWORD`. Never put their values in tracked files. The namespace must be verified and the public signing key distributed through a keyserver supported by Central.
- Released Maven Central versions cannot be overwritten. Increment the shared version and create a new matching tag for the next release. Do not commit, push, create release tags, or publish without user authorization.
- Generate local documentation with `.\gradlew.bat :mvi:dokkaGenerateHtml :mvi:kotlinMultiplatformDokkaJavadocJar`. HTML is written to `mvi/build/dokka/html`; publication archives are in `mvi/build/libs`.

## Coding Style & Naming Conventions

Apply the personal `kotlin-style`. Keep packages under `com.dmerzliakov.freezekit`. Put reusable code in `commonMain` and platform APIs in their corresponding source sets. Manage dependency versions through the version catalog. No dedicated formatter or lint plugin is configured; do not introduce one as part of ordinary code changes.

## Testing Guidelines

Tests use `kotlin.test` and live in `commonTest`, `androidHostTest`, `jvmTest`, and `iosTest`. Use the personal `test` skill for scenario KDoc and test method names. Add meaningful behavioral assertions for changed logic. No coverage threshold is configured. Run the tests for affected platforms and report any unavailable platform checks.

## Commit & Pull Request Guidelines

Follow the existing commit convention when available; otherwise use concise, imperative commit subjects. Pull requests should describe the change, link relevant issues, list verification commands and results, and include screenshots for UI changes.

## Configuration Hygiene

Keep credentials and machine-specific paths out of tracked files. Preserve exclusions for `local.properties`, build outputs, and IDE state.
