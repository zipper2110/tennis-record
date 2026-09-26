# Windows distribution

The Windows x64 package is a self-contained `jpackage` application image and
per-user EXE installer. It includes Java 17, the pinned LGPL libmpv build, and the pinned
BtbN FFmpeg GPL shared build listed in `native-dependencies.json`. Only the
FFmpeg archive's `bin/` directory and `LICENSE.txt` are bundled; its headers,
import libraries and HTML docs are not.

## Local app-image build

```powershell
.\distribution\windows\Get-NativeDependencies.ps1
mvn test
.\distribution\windows\Build-AppImage.ps1 -Version 1.0.0
& ".\target\package\app-image\Tennis Record\Tennis Record Diagnostics.cmd"
```

Building the EXE additionally requires WiX Toolset 3:

```powershell
.\distribution\windows\Build-Installer.ps1 -Version 1.0.0
```

The app icon source is `src/main/resources/icons/app-icon.svg`. The app uses
this SVG for the window icon. `assets/tennis-record.ico` is generated from it.
After you change the SVG, run this command to generate the ICO again:

```powershell
.\distribution\windows\New-AppIcon.ps1 -PngDirectory design\app-icon
```

## Release requirements

- Build on Windows x64 with Eclipse Temurin JDK 17 and WiX Toolset 3.
- Code signing is optional. The release workflow signs only when the repository
  variable `ARTIFACT_SIGNING_ENDPOINT` and the other Azure Artifact Signing
  settings exist. Without them, it builds unsigned files and shows a warning.
- The release workflow creates a draft release. Check it on the GitHub
  releases page, then click "Publish release".
- Tennis Record uses the Elastic License 2.0. Bundle only an LGPL build of
  libmpv (`-Dgpl=false`), because the app loads libmpv into its own process.
  `Validate-Release.ps1` checks this.
- Run FFmpeg only as a separate process. The GPL FFmpeg build is then an
  aggregate, and its license does not cover the app.
- Publish the corresponding source beside every binary release: the app
  source, the FFmpeg build source, and the libmpv build source.
- Review the generated Maven dependency license/SBOM output before release.

## Natives release

The natives release keeps copies of the pinned native builds and their
corresponding source in this repository. App builds then do not depend on
upstream hosting. BtbN removes its daily builds after about two weeks.

Make a new natives release only when you change a pin in
`native-dependencies.json`.

1. Start Docker Desktop. The FFmpeg source collection runs in a Linux
   container.
2. Run the script. It downloads the source of each FFmpeg library stage. This
   takes a long time and needs several GB of free disk space.

   ```powershell
   .\distribution\windows\New-NativesRelease.ps1
   ```

   The files are in `target\natives-release`. The default tag is
   `natives-YYYY-MM`. Use `-ReleaseTag` to set a different tag.
3. On GitHub, open the releases page and click "Draft a new release".
4. In "Choose a tag", type the tag (for example `natives-2026-09`) and select
   "Create new tag". Keep the target branch `master`.
5. Type the tag as the release title. Paste the text of `README.txt` as the
   description.
6. Drag all files from `target\natives-release` into the assets area. Wait
   until each upload is complete.
7. Select "Set as a pre-release". Clear "Set as the latest release". The app
   releases must stay the latest release.
8. Click "Publish release". A draft release is not visible to other users, and
   its download URLs do not work in the release workflow.
9. Change the URLs in `native-dependencies.json` to the natives release
   (`https://github.com/zipper2110/tennis-record/releases/download/<tag>/<file>`).
   Do not change the SHA-256 values. The files are the same.
