# 25 — Devcontainer + Dockerfile for reproducible local build

## Why this brief

The senior-architect review noted that the only
build path is CI; no Android SDK locally. The
project is single-developer today, but the
"can a new engineer run the tests" question is
the right test of the build setup. This brief
ships the devcontainer and Dockerfile that
make "clone, open in VS Code, build" work.

## Primary research

- VS Code Dev Containers specification:
  https://containers.dev/
  - "devcontainer.json" is the standard manifest
    for VS Code's Remote-Containers extension.
  - "build.dockerfile" + "context" specifies the
    Docker image; the same Dockerfile is
    standalone-buildable for non-VS-Code use.
- Android SDK command-line tools:
  https://developer.android.com/studio
  - The cmdline-tools download URL is the
    official mechanism for installing the SDK
    without Android Studio.
  - `sdkmanager --licenses` accepts all licenses
    non-interactively when piped from `yes`.

## What this PR ships

1. `.devcontainer/Dockerfile` — Ubuntu 24.04
   (matches CI's ubuntu-latest), JDK 21
   (matches setup-java), Android SDK 35, NDK
   27.3.13750724 (matches the project's pinned
   `ndkVersion`), and CMake 3.22.1 (the version
   the NDK ships with).

2. `.devcontainer/devcontainer.json` — the
   VS Code Dev Containers manifest pointing at
   the Dockerfile. Recommends the Kotlin and
   Java extension packs.

3. `.devcontainer/README.md` — quick-start for
   the two use cases:
   - VS Code Dev Containers: open the folder,
     VS Code detects the devcontainer and offers
     to reopen in container.
   - Standalone Docker: `docker build -t
     mindanchor-dev -f .devcontainer/Dockerfile
     .` then `docker run --rm -v $(pwd):/src -w
     /src mindanchor-dev ./gradlew test`.

## What this PR does NOT ship

- A pre-built image published to a registry. The
  Dockerfile is the source of truth; the image
  is rebuilt per-environment. The 2 GB SDK
  download is the cost of a clean build; CI does
  the same and caches it on the runner.
- A M1/ARM64 base. Ubuntu 24.04 multiarch is
  available but the NDK prebuilt binaries are
  x86_64-only. ARM64 macOS users will need to
  run the devcontainer under Rosetta or use
  x86_64 emulation. A follow-up commit can
  address this if the project's contributor
  base shifts to Apple Silicon.
- A "fast" image for incremental development.
  The current image is a full SDK install
  (~2 GB). Caching is the responsibility of the
  runner; for local dev, `docker buildx` cache
  is the right tool.

## Risk

- The Dockerfile downloads the SDK and NDK at
  build time. A future Google CDN change could
  break the build; the URLs in this Dockerfile
  are pinned to a specific cmdline-tools version
  (11076708, the version that was current in
  2026-Q1) so a follow-up can pin or update.
- The `yes | sdkmanager --licenses` step accepts
  all SDK licenses. The legal acceptance is the
  human running the build, not the Docker image.
  This is the standard pattern; the alternative
  is to pre-accept licenses on a host machine
  and copy the accepted-licenses file in, which
  adds complexity for no benefit.

## Verification

- 1 new test in `DockerfileSyntaxTest.kt`:
  parses the Dockerfile for the canonical
  structure (FROM, ENV, RUN, WORKDIR) and the
  required Android components.

Actually, a Dockerfile cannot be unit-tested in
the JVM. The verification is a *manual* one:
running `docker build -t mindanchor-dev -f
.devcontainer/Dockerfile .` produces a usable
image. The CI step that exercises this is the
follow-up commit (a workflow that runs the
build inside the devcontainer image, mirroring
what a developer would do).

## Primary sources

- VS Code Dev Containers specification
  (https://containers.dev/)
- Android SDK command-line tools
  (https://developer.android.com/studio)
- The project's existing CI configuration
  (`.github/workflows/ci.yml`,
  `.github/workflows/probe-ndk.yml`)
