# Dev Container

This directory ships a reproducible build environment
for MindAnchor. The goal: any developer with Docker
can run the project's tests locally, matching the CI
runner configuration exactly.

## Two ways to use it

### 1. VS Code Dev Containers (recommended for editor work)

1. Install the [Remote - Containers
   extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers).
2. Open the MindAnchor folder in VS Code.
3. VS Code detects the `.devcontainer/devcontainer.json`
   and offers to reopen in container.
4. The first build downloads the Android SDK + NDK
   (~2 GB). Subsequent builds are fast (Docker
   caches the layers).
5. Run `./gradlew test` in the integrated terminal.

### 2. Standalone Docker

```bash
docker build -t mindanchor-dev -f .devcontainer/Dockerfile .

# Run the tests
docker run --rm -v $(pwd):/src -w /src mindanchor-dev \
  ./gradlew test

# Open a shell for ad-hoc work
docker run --rm -it -v $(pwd):/src -w /src mindanchor-dev bash

# Build a debug APK
docker run --rm -v $(pwd):/src -w /src mindanchor-dev \
  ./gradlew assembleDebug
```

## What is in the image

| Component | Version | Why |
|-----------|---------|-----|
| Ubuntu | 24.04 | Matches `ubuntu-latest` CI runner |
| JDK | 21 (Temurin) | Matches `setup-java@v4` |
| Android SDK | 35 | Matches `compileSdk` / `targetSdk` |
| NDK | 27.3.13750724 | Matches `ndkVersion` (pinned in `app/build.gradle.kts`) |
| CMake | 3.22.1 | The NDK's bundled version |

## What is *not* in the image

- A pre-built APK. The image is a build environment,
  not a distributable.
- An Android Studio install. Studio is a desktop
  IDE; the devcontainer is for headless / editor
  work.
- A Gradle install. The project's `gradlew` wrapper
  downloads its own Gradle on first run.

## Maintenance

The Dockerfile pins the cmdline-tools version
(`commandlinetools-linux-11076708_latest.zip`)
to a specific Google CDN URL. A future Google CDN
update could break the build; the version pin is
explicit in the Dockerfile for that reason. When
updating:

1. Check the [Android Studio release
   notes](https://developer.android.com/studio/releases)
   for the current cmdline-tools version.
2. Update the URL in the Dockerfile.
3. Rebuild and verify `./gradlew test` passes.

## See also

- `docs/research/25-devcontainer.md` — the
  research brief this directory implements.
- `.github/workflows/ci.yml` — the CI workflow
  this devcontainer mirrors.
- `docs/PLAN.md` — the project's design record.
