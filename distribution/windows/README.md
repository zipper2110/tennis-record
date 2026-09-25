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
- Configure Azure Artifact Signing and GitHub OIDC variables/secrets.
- Tennis Record uses the Elastic License 2.0. Bundle only an LGPL build of
  libmpv (`-Dgpl=false`), because the app loads libmpv into its own process.
  `Validate-Release.ps1` checks this.
- Run FFmpeg only as a separate process. The GPL FFmpeg build is then an
  aggregate, and its license does not cover the app.
- Publish the corresponding source beside every binary release: the app
  source, the FFmpeg build source, and the libmpv build source.
- Review the generated Maven dependency license/SBOM output before release.
