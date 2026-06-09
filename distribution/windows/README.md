# Windows distribution

The Windows x64 package is a self-contained `jpackage` application image and
per-user EXE installer. It includes Java 17, VLC 3.0.23, and the pinned
BtbN FFmpeg LGPL shared build listed in `native-dependencies.json`.

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

The icon in `assets/tennis-record-temp.ico` is deliberately temporary and
must be replaced with final brand artwork before public launch.

## Release requirements

- Build on Windows x64 with Eclipse Temurin JDK 17 and WiX Toolset 3.
- Configure Azure Artifact Signing and GitHub OIDC variables/secrets.
- Keep Tennis Record and distributed derivatives under GPLv3-or-later while
  the application links to the public GPL build of vlcj.
- Publish corresponding source beside every binary release.
- Review the generated Maven dependency license/SBOM output before release.
