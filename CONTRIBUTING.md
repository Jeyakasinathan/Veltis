# Contributing to Veltis

Thanks for your interest in contributing to Veltis.

Contributions to bug fixes, performance, world simulation, Shulker, and documentation are welcome.

## Requirements

- JDK 26
- Git
- Internet access for initial setup
- A Java IDE (IntelliJ IDEA recommended)

Gradle is included through the repository wrapper.

## Setup

```bash
git clone https://github.com/VeltisMC/Veltis.git
cd Veltis
```

Build the project:

**Windows**

```powershell
.\gradlew.bat buildVeltisMC
```

**Linux / macOS**

```bash
./gradlew buildVeltisMC
```

## Project Structure

```text
Veltis/
├── veltis-api/
├── launcher/
├── patch-engine/
├── server/
│   └── Shulker/
│       ├── code/
│       ├── data/
│       └── modules/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

Keep the existing module structure unless a new module has a clear technical purpose.

## Working with Shulker

Minecraft source modifications belong in `server/Shulker`.

Apply patches:

```bash
./gradlew applyPatches
```

On Windows:

```powershell
.\gradlew.bat applyPatches
```

Make changes in the patched Minecraft workspace, following the repository's generated workspace path. Rebuild the patches:

```bash
./gradlew rebuildPatches
```

Review your changes:

```bash
git diff -- server/Shulker
```

Do not edit the pristine Minecraft source tree directly. Keep patches focused, descriptive, and logically separated. Do not manually renumber existing patches.

## Performance Guidelines

Veltis prioritizes performance without sacrificing correctness.

Before submitting an optimization, consider:

- Does it reduce measurable work?
- Does it reduce allocations or improve memory access?
- Can it safely run concurrently?
- Does it preserve Minecraft behavior?
- Can the improvement be demonstrated through profiling or benchmarks?

Avoid unnecessary threads, dependencies, abstractions, and repeated work.

World state and gameplay logic must remain correct under concurrent execution.

## Testing

Before submitting a pull request, run:

```bash
./gradlew clean buildVeltisMC
```

On Windows:

```powershell
.\gradlew.bat clean buildVeltisMC
```

Also run relevant tests and verify the affected functionality.

For Shulker changes, ensure patches apply cleanly and rebuilding them does not introduce unrelated modifications.

For performance changes, include benchmark or profiling results when available.

## Code Style

Follow the surrounding code and existing project conventions.

Prefer:

- Clear names
- Small, focused changes
- Simple implementations
- Minimal dependencies
- Explicit error handling

Avoid:

- Unnecessary abstractions
- Large utility classes without a clear purpose
- Redundant manager/service layers
- Dead code
- Unnecessary dependency shading

## Pull Requests

Keep pull requests focused. Explain:

1. What changed.
2. Why the change is needed.
3. How it was tested.
4. Any performance impact.
5. Any compatibility or behavior changes.

Discuss substantial changes to the architecture, threading model, patch system, or public API before implementing them.

## Documentation

Document non-obvious behavior, design decisions, assumptions, and limitations. Comments should explain why an implementation exists when the code alone is insufficient.

## Reporting Bugs

Include:

- Veltis version or commit
- Minecraft version
- Java version
- Operating system
- Steps to reproduce
- Expected and actual behavior
- Relevant logs or stack traces

For performance issues, include profiling or benchmark results when available.

## License

Veltis is licensed under the GNU General Public License v3.0. Contributions are expected to be submitted under the same license.

Thanks for helping build Veltis.
