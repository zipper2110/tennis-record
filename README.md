# Tennis Record (Kotlin Desktop)

A desktop application that helps tennis players turn full‑match recordings into compact, watchable videos. You can remove dead time between points, track the score, and export a final video with a scoreboard overlay. Future versions add zoom/crop/reposition and color adjustments.

> Detailed architecture and workflows live in the Solution Outline:
> - 📄 [docs/solution-outline.md](docs/solution-outline.md)
> Roadmap, milestones, and changelog:
> - 🗺️ [docs/roadmap.md](docs/roadmap.md)

## Key features (scope)
- Manual trimming of empty time between points (mark in/out, ripple delete)
- Score entry during editing; live scoreboard overlay in preview
- Final render: cuts applied + overlayed scoreboard
- Future: zoom/crop/reposition, brightness/contrast, presets, hardware‑accelerated exports

## Tech stack (selected)
- UI: Compose Multiplatform Desktop (Kotlin/JVM)
- Preview: libVLC via VLCJ (binding)
- Export/render: FFmpeg (CLI initially), optional hardware encoding (NVENC/Quick Sync/Videotoolbox)
- Models & storage: Kotlin + kotlinx.serialization (EDL JSON)
- Packaging: jpackage (Windows/macOS/Linux)

Rationale, trade‑offs, and module plan are explained in the [solution outline](docs/solution-outline.md).

## Project status
- This repository currently contains a Kotlin/Maven skeleton.
- Implementation will proceed in phases; see the Roadmap below.

## Getting started (development)
### Prerequisites
- JDK 17+
- Maven 3.9+
- FFmpeg (ffmpeg/ffprobe) available in PATH (for export stage)
- VLC installed or bundled libVLC (for preview at runtime)

### Build
```bash
mvn -DskipTests package
```

### Tests
- Run full test suite:
  ```bash
  mvn test
  ```
- Quick run for architecture dependency hygiene only:
  ```bash
  mvn -q -Dtest=ArchitectureDependencyHygieneTest test
  ```

### Run (temporary)
A proper desktop entrypoint (Compose Desktop) will be added with dependencies and packaging. For now, the skeleton app is minimal and only for verifying the toolchain.

## CI
- GitHub Actions workflow runs `mvn test` on pushes and pull requests to `main`/`master`.
- The architecture dependency hygiene test (`org.litvin.ArchitectureDependencyHygieneTest`) is part of the suite and will fail the build on violations.

## High‑level architecture
See: [docs/solution-outline.md](docs/solution-outline.md)

## Roadmap (high level)
- v0.1 (MVP): Open video, mark segments, simple scoreboard, save/load project, basic export with overlay.
- v0.2: Better scrubbing, thumbnails, undo/redo, presets, HW accel paths.
- v0.3: Proxy media, color adjustments, richer themes, in‑process FFmpeg.

## Contributing
- File issues and proposals referencing sections of the [solution outline](docs/solution-outline.md).
- Keep EDL/score models versioned and migration‑ready.

## Licensing & third‑party components
- libVLC (LGPL) — shipped unmodified and replaceable by users.
- FFmpeg — prefer LGPL builds unless GPL filters/codecs are explicitly required.
- Fonts — ensure redistribution rights (e.g., OFL fonts like Roboto).

## Acknowledgements
- FFmpeg, VLC, Kotlin, and JetBrains Compose teams for awesome tooling.
