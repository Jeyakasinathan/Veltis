# Veltis

**Veltis is an independent, high-performance Minecraft server project developed by VeltisMC.**

Its goal is to improve server performance and world simulation while keeping vanilla Minecraft at its core.

- Multithreaded world simulation
- Efficient CPU utilization
- Low server overhead
- Reproducible Minecraft patching
- Fast, reliable startup
- A foundation for future plugin development

Veltis is not based on Bukkit, Spigot, or Paper.

## Project Structure

| Module | Purpose |
|---|---|
| `veltis-api` | Public API boundary |
| `launcher` | Application entry point and distribution |
| `patch-engine` | Minecraft acquisition and patch preparation |
| `server` | Runtime and world simulation |
| `server/Shulker` | Minecraft source patches |

### Shulker

Shulker manages Veltis's Minecraft modifications. Patches are maintained in Git and applied during the build, keeping changes reviewable and reproducible.

## Requirements

- JDK 26
- Git
- Internet access for initial Minecraft and dependency downloads

Gradle is included through the repository wrapper.

## Build

**Windows (PowerShell)**

```powershell
.\gradlew.bat build
.\gradlew.bat buildVeltisMC
```

**Linux / macOS**

```bash
./gradlew build
./gradlew buildVeltisMC
```

The distributable is generated at:

```text
build/distributions/veltis.jar
```

## Run

From the directory containing the distributable:

```bash
java -jar veltis.jar --nogui
```

Veltis retrieves the required Minecraft server files and prepares its runtime. Minecraft itself is not redistributed with Veltis.

## Build Configuration

The root `build.gradle.kts` defines the release configuration.

| Property | Default |
|---|---|
| Minecraft version | `26.3` |
| Release channel | `pre-alpha` |
| Commit | Current CI or Git revision |

Override properties when needed:

```bash
./gradlew buildVeltisMC -PminecraftVersion=26.3 -Pchannel=pre-alpha
```

Supported current channels are `pre-alpha`, `alpha`, `beta`, and `release`.

The local JAR name remains `veltis.jar`. Published release assets include the Minecraft version, channel, and short commit identifier.

## Configuration

Veltis uses:

- **`server.properties`** for Minecraft's server settings.
- **`WorldConfig`** for Veltis world-engine settings, including region configuration, worker pools, tick intervals, and resource limits.

World-engine configuration is validated and has explicit defaults.

## Project Status

Veltis is under active development. World simulation, multithreading, and Minecraft-side optimizations continue to evolve. APIs and supported versions may change.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, patching instructions, testing requirements, and pull request guidelines.

## License

Veltis is licensed under the **GNU General Public License v3.0**.

Developed by **VeltisMC**.
