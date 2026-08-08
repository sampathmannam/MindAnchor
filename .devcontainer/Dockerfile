# Reproducible build environment for MindAnchor.
#
# This Dockerfile produces an image that can build and
# test the project on a developer machine, matching
# the CI runner configuration (Ubuntu 24.04, JDK 21,
# Android SDK 35, NDK 27.3.13750724).
#
# Build:
#   docker build -t mindanchor-dev -f .devcontainer/Dockerfile .
#
# Use (with a host directory mounted as the source):
#   docker run --rm -v $(pwd):/src -w /src mindanchor-dev ./gradlew test
#
# The Dockerfile is referenced by .devcontainer/devcontainer.json
# for VS Code Dev Containers; both can be used standalone.

FROM ubuntu:24.04

# CI runs on ubuntu-latest; pin to 24.04 for reproducibility.
# The CI runner also has Git, but the devcontainer image
# needs Git for the actions/checkout step. Install Git
# and the dependencies the Android SDK manager needs.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    unzip \
    wget \
    zip \
    openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

# Android SDK — same version CI uses. The cmdline-tools
# download is the official Google URL; the SDK and NDK
# versions match .github/workflows/probe-ndk.yml.
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_NDK_ROOT=/opt/android-sdk/ndk/27.3.13750724
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# cmdline-tools (the install mechanism for the SDK itself)
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

# Accept licenses and install the SDK + NDK + CMake.
# The accept-licenses step is the "are you sure?" prompt
# the Android SDK manager emits; piping `yes` to it
# accepts the licenses non-interactively.
RUN yes | sdkmanager --licenses && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        "ndk;27.3.13750724" \
        "cmake;3.22.1"

# Gradle wrapper is committed; the image doesn't need a
# system Gradle. The wrapper downloads its own.
# (no RUN for gradle install)

# Verify the build toolchain is what the project expects.
RUN java -version && \
    ls $ANDROID_HOME/platforms && \
    ls $ANDROID_HOME/ndk

# Default working directory for the source tree.
WORKDIR /src
