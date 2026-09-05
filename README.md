# ParadisePaints

ParadisePaints is a client-side Fabric mod for painting Minecraft maps through
communication with the ParadisePaints server plugin. It is intended as an
alternative to MCPaint-based setups on plugin servers.

## Requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5 or newer
- Fabric API

## Development

Use the checked-in Gradle wrapper so every contributor builds with the same
Gradle version.

```powershell
.\gradlew.bat build
```

The generated mod JAR is written to `build/libs/`. Local Gradle state, IDE
files, Minecraft run directories, logs, and generated build output are ignored
by Git.

## Source layout

- `src/main/java` — common mod initialization and code safe for any environment.
- `src/main/resources` — Fabric metadata and common resources.
- `src/client/java` — client-only initialization, UI, rendering, and input code.
- `src/client/resources` — client-only resources and mixin configuration.

## License

Copyright (c) 2026 Andromedov. All rights reserved.
