# Aibtra

This is the source code of [Aibtra](https://www.aibtra.dev/), a desktop tool for refining texts using OpenAI.
If you are only interested in Aibtra from a user's perspective, visit the [website](https://www.aibtra.dev/).
Binaries can be downloaded directly from [GitHub ](https://github.com/aibtra/aibtra/releases/tag/latest).

## Introduction

This project is built on the Kotlin JVM platform and utilizes the Swing framework for its GUI components. A combination of popular libraries are employed to achieve various tasks.

## Setup

The build system used is Gradle with the Kotlin DSL.

## Building and Packaging

To package the application:

```bash
gradlew jpackage
```

## Running Tests

Tests use the JUnit 5 platform. You can run tests using:

```bash
gradlew test
```

## Dependencies

Detailed information about the dependencies can be found in `build.gradle.kts`.
All dependencies have their respective licenses.

## License

Aibtra is licensed under the GNU General Public License v3.0.
