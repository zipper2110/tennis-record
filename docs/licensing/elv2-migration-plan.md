# ELv2 License Migration Plan

Status: in progress. Created 2026-09-25.

This plan lists the work to change Tennis Record from GPLv3-or-later to the
Elastic License 2.0 (ELv2). Do each item as a separate task. Mark an item done
only when its "Done when" condition is true.

This plan is not legal advice. Get a review from a lawyer before the first
paid release.

## Goals

- Keep the source code public.
- Prevent users from legally removing the license checks (build expiry,
  version check, license file). ELv2 forbids this. GPLv3 does not.
- Keep the installer self-contained. Users must not install FFmpeg or mpv.

## Why the order is important

ELv2 adds restrictions. The GPL does not allow extra restrictions on a
combined program. The app loads libmpv into its own process through JNA, so
the app and libmpv are one combined program. The current libmpv build is
GPLv2+. Thus, do phase 1 before you release a build under ELv2.

FFmpeg is not a problem. The app starts `ffmpeg.exe` as a separate process.
The GPL calls this an "aggregate" (GPLv3 section 5).

## Current license state (checked 2026-09-25)

| Component | License | How the app uses it | Compatible with ELv2 |
|---|---|---|---|
| Tennis Record code | GPLv3-or-later, single author | - | Author can relicense |
| Kotlin, Jackson, FlatLaf, Ikonli, kotlin-logging | Apache-2.0 | In process | Yes |
| Ikonli icon packs (Material2, Feather) | Apache-2.0 | In process | Yes |
| SLF4J | MIT | In process | Yes |
| Logback | EPL-1.0 or LGPL-2.1 | In process | Yes |
| JNA | Apache-2.0 or LGPL-2.1 | In process | Yes (use Apache-2.0) |
| Temurin JDK 17 | GPLv2 with Classpath Exception | Runtime | Yes |
| FFmpeg (BtbN `gpl-shared`) | GPLv3 | Separate process | Yes, with source obligations |
| libmpv (shinchiro build) | GPLv2+ | In process | **No** |
| Fonts | System fonts, none bundled | - | Yes |

## Phase 0: Preparation

### L-0.1 Record which code is already under GPL

- Find out if the GitHub repository is public and if any build was shared.
- Tag the last GPL commit (for example `last-gpl`).
- Code up to that tag stays GPLv3 for people who already have a copy. You
  cannot revoke this.
- Done when: the tag exists and this plan records the facts.
- **Done 2026-09-25.** The GitHub repository is public. No build was shipped.
  The tag `last-gpl` points to `311a444` (the last pushed commit). The tag is
  on GitHub. Uncommitted work after `311a444` was never published,
  so it goes directly to ELv2.

### L-0.2 Protect the right to relicense

- You are the only author now. Keep it this way, or get a Contributor License
  Agreement (CLA) from each outside contributor.
- Add `CONTRIBUTING.md` that states the CLA rule.
- Done when: `CONTRIBUTING.md` exists and states the rule.
- **Done 2026-09-25.** `CONTRIBUTING.md` says that the project does not accept
  outside code until it publishes a CLA.

### L-0.3 Choose the licensor name

- ELv2 needs a named "Licensor". `LICENSE-NOTICE` now says "Tennis Record
  contributors". Use your legal name or your company name.
- Done when: the licensor name is decided.
- **Done 2026-09-25.** Licensor: Dmitrii Litvin (no company).

## Phase 1: Replace GPL libmpv with LGPL libmpv

### L-1.1 Choose an LGPL build

- Needs: LGPL; `d3d11` render API with a shader compiler (`shaderc`,
  `spirv-cross`) for `gpu-next` and `tr-adjust.hook`; `libplacebo`; H.264 and
  HEVC with d3d11va; the complete corresponding source (L-4.3).
- **Rejected: `zhongfly/mpv-winbuild`** (`mpv-dev-lgpl-x86_64`). It worked in
  all tests, but it cannot give the corresponding source: `mpv-winbuild-cmake`
  builds about 40 libraries from their latest Git version and records only the
  mpv commit.
- **Rejected: `jmonsellier/milutv-libmpv`.** No D3D11 render API and no shader
  compiler. FFmpeg has only the decoders that IPTV uses.
- **Rejected: LANcast** (one static DLL). No source archive, and the features
  are unknown.
- Community practice (checked 2026-09-25): no well-known builder publishes an
  LGPL libmpv for Windows with its source. Apps that cannot ship GPL code
  build their own (LANcast, open-ani/mediamp, MiluTV, grid).
- **Chosen 2026-09-25: `lpbborges/grid`, release `libmpv-windows-v0.41.0-b4`.**
  - The recipe builds only mpv (`-Dgpl=false`), FFmpeg n7.1 (LGPL, decoders
    only, d3d11va, dxva2, dav1d) and libplacebo on MSYS2 UCRT64. The other DLLs
    are MSYS2 packages. `BUILD-INFO.txt` lists each DLL, its package version,
    and its license.
  - Binary: `libmpv-windows-x86_64.tar.gz`, SHA-256
    `c0e0c8dd9aa232c270614785f088dc7371434cfc3762912fcbc2c83fc0bb7d2d`
    (31 DLLs, `LICENSES/`, `BUILD-INFO.txt`).
  - Source: `libmpv-windows-source.tar.xz` (111 MB), SHA-256
    `162ab04fa08de2ecd6e212f95857ac4b58b80617296e2b2048f2b28b1a360ed7`.
    It holds mpv, FFmpeg, libplacebo, the build workflow, and the MSYS2 source
    packages of the LGPL DLLs (fribidi, gettext, glib2, graphite2,
    mingw-w64-headers, libiconv).
  - Risk: a new project by one person (prerelease builds). We pin both files
    by SHA-256 and copy them into our releases. The repository uses the MIT
    license, so we can copy its MSYS2 workflow into our own CI later (L-1.4).
- A scan of all 31 DLLs found no GPL markers. All six FFmpeg DLLs report
  "LGPL version 2.1 or later".

### L-1.2 Test the preview with the LGPL build

- Test playback, seek, frame step, and hardware decoding.
- Test `vo=gpu-next` and the `tr-adjust.hook` shader (rotate, crop, color).
- Run the diagnostics command and the UI integration tests.
- Done when: all tests pass and the preview has no visible difference.
- **Done 2026-09-25 for the grid build** (and before for zhongfly). Headless
  probe (`vo=null`, `hwdec=d3d11va-copy`):

  | Video | Codec | First frame GPL / zhongfly / grid (ms) |
  |---|---|---|
  | `D:\fav.mp4` 4K60 | H.264 | 491 / 468 / 456 |
  | `D:\PXL_20260913_070045619.mp4` 4K | HEVC | 659 / 404 / 380 |
  | `D:\dfdf (2)-data-saver-1080p.mp4` | H.264 | 526 / 299 / 322 |

- `MpvPreviewSpikeMain` (real adapter: `vo=gpu-next`, `gpu-api=d3d11`,
  `tr-adjust.hook`, libass overlay) on `D:\PXL_20260828_100310593.mp4` 4K60:

  | Check | GPL | grid |
  |---|---|---|
  | Load to ready | 340 ms | 248 ms |
  | 15 s play with ~244 live shader updates | 0.26 cores, 0 drops, 60 fps | 0.27 cores, 0 drops, 60 fps |
  | Seek and 5+5 frame steps | exact | exact, same timestamps |
  | 10 tab-switch reloads | 109-138 ms | 107-111 ms |
  | Mouse and key input through the mpv window | 1 / 1 | 1 / 1 |
  | Plain frame and shader frame | - | same video pixels (mean difference 0.1 levels) |

- Harmless log lines with the grid build: `ytdl` and `osc` options do not
  exist (no Lua), and libplacebo has no LittleCMS (used only for ICC display
  profiles, which the app does not set).
- The DLL must load by an absolute path with backslashes. Then Windows finds
  the dependent DLLs in the same folder. `LibMpv` already does this.
- Still to do: the diagnostics command and the UI integration tests on a
  packaged build.

### L-1.3 Change the native dependency manifest

- **Done 2026-09-25.** The `mpv` entry pins the grid binary and source
  (`license`, `sourceUrl`, `sourceSha256`, `sourceArchiveName`, `buildTag`).
- `Get-NativeDependencies.ps1` now extracts `.tar.gz` with `tar.exe`, and a new
  manifest field `flattenDirectories` copies `bin/` directly into
  `natives/windows-x64/mpv`. `LICENSES/` and `BUILD-INFO.txt` go beside the
  DLLs. Tested locally for the mpv entry: 31 DLLs, `BUILD-INFO.txt`, and 54
  license files.
- Still to do: a clean full build and an installer run (CI).
- Local development: the user variable `MPV_PATH` still points to the GPL DLL
  in `C:\Program Files\libmpv`. Replace the folder contents with the grid `bin/`
  files.

### L-1.4 Later: build libmpv in our own CI

- Copy the MIT-licensed MSYS2 workflow of `lpbborges/grid` into this project
  or a separate repository. Then we do not depend on a one-person project.
- Done when: CI makes a pinned LGPL libmpv and its source archive.

## Phase 2: Remove GPL code from the project source

### L-2.1 Review `tr-adjust.hook`

- The shader copies the math of FFmpeg `vf_eq.c`, `vf_hue.c`, and `vf_lut.c`.
  `vf_eq.c` is in the GPL-only list of FFmpeg (verify this).
- Math is not protected. Copied code is protected.
- If the shader is a line-by-line port, rewrite it from the formulas.
- Done when: the shader has no code copied from GPL files.
- **Reviewed 2026-09-25.** `vf_eq.c` is GPL-only (GPLv2+, in the FFmpeg
  `LICENSE.md` list). `vf_hue.c` and `vf_lut.c` are LGPL.
- The shader is not a line-by-line port. It is GLSL with its own structure.
  `eq_plane` repeats only the fixed-point formula of `vf_eq.c` `process_c`
  (about three lines of arithmetic), so the preview matches the export exactly.
- Assessment: low risk, because a short functional formula has little
  copyright protection. Add it to the lawyer review (L-6.2).
- Option to remove the risk: change the export from the GPL `eq` filter to an
  app-defined `lutyuv` expression (LGPL), and use the same formula in the
  shader. This keeps parity and makes the formula our own.
- Decision 2026-09-25: keep the shader as it is.

### L-2.2 Search for other copied code

- Search the source for "ported from", "based on", "copied from", and links to
  GPL projects.
- Done when: each result is rewritten or has a compatible license.
- **Done 2026-09-25.** One result: `WrapLayout` in `IconBrowserDialog.kt`, from
  Rob Camick's Java Tips Weblog. The blog permits use, change and distribution
  without restriction. The credit comment now says this.
- `src/test/resources/media/ui-smoke.mp4` is a project-owner file. Its README
  records the permission.

## Phase 3: Change the license files

### L-3.1 `LICENSE`

- Replace the GPLv3 text with the full ELv2 text.
- Done when: `LICENSE` contains only the ELv2 text.
- **Done 2026-09-25.** The text is the same as `licenses/ELASTIC-LICENSE-2.0.txt`
  in the elastic/elasticsearch repository (CRLF, UTF-8).

### L-3.2 `LICENSE-NOTICE`

- Name the licensor (from L-0.3).
- State that the build expiry, the version check, and the license file are
  "license key functionality" as ELv2 uses the term.
- State that third-party components keep their own licenses, and that ELv2
  does not restrict the rights those licenses give.
- State that versions up to the `last-gpl` tag were released under GPLv3.
- Done when: the notice contains all four statements.
- **Done 2026-09-25.** Get a lawyer to check the definition of "license key
  functionality" (L-6.2).

### L-3.3 `pom.xml`

- Change `<licenses>` (lines 12-18) to "Elastic License 2.0" with URL
  `https://www.elastic.co/licensing/elastic-license`.
- Done when: the POM and the SBOM show ELv2.
- **Done 2026-09-25** for the POM. Check the SBOM in the next release build.

### L-3.4 `README.md`

- Rewrite the license section (lines 104-110). Say that the source is public
  under ELv2 and that ELv2 is not an OSI open source license.
- Change the libmpv line to LGPL.
- Done when: the README has no GPL claim about the app.
- **Done 2026-09-25.**

### L-3.5 `distribution/windows/README.md`

- Rewrite lines 36-38. The app uses ELv2. libmpv is LGPL. FFmpeg is GPL in a
  separate process.
- Done when: the file matches the new license state.
- **Done 2026-09-25.**

### L-3.6 `distribution/THIRD-PARTY-NOTICES.txt`

- Change the libmpv entry to LGPL, with the new build source.
- Change the Tennis Record entry to ELv2.
- Point the source text to the release assets from phase 4.
- Done when: each entry matches the shipped files.
- **Done 2026-09-25.** The FFmpeg and libmpv entries say that the source is
  published beside each release. Phase 4 must make this true before a release.

### L-3.7 `distribution/windows/Validate-Release.ps1`

- Change the checks (lines 5-15) to require the ELv2 text and the notice
  statements from L-3.2.
- Add a check that fails if the mpv manifest `variant` is not LGPL.
- Done when: the script passes on the new files and fails on the old files.
- **Done 2026-09-25.** Tested in Windows PowerShell 5.1. It passes on the new
  files. It fails on the `HEAD` files (LICENSE check). With new license files
  and the old manifest, it fails on the libmpv check. PowerShell 7 is not
  installed locally. CI uses `pwsh`.

### L-3.8 Installer license page

- `Build-Installer.ps1` joins `LICENSE-NOTICE` and `LICENSE` into the
  installer license page. Add `THIRD-PARTY-NOTICES.txt` to it.
- Done when: the installer shows the ELv2 text and the third-party notices.
- **Done 2026-09-25.** The script now reads the files as UTF-8, so the "’" in
  the ELv2 text stays correct in Windows PowerShell 5.1. The license page text
  was tested. A full installer build is still to do (with L-1.3).

## Phase 4: Third-party source obligations

### L-4.1 Fix the empty release sources folder

- `.github/workflows/windows-release.yml` line 120 copies
  `target/distribution/sources/*`. No script fills this folder.
- Done when: a script fills the folder and the release job uses it.
- **Done 2026-09-25** (not yet run in CI). A new job `native-sources` on
  `ubuntu-24.04` fills `target/distribution/sources/` and uploads it as the
  artifact `native-sources`. The job `windows-installer` needs it and
  downloads it before it stages the release files. A manual run
  (`workflow_dispatch`) runs only `native-sources`, so you can test it without
  a release.

### L-4.2 Publish the FFmpeg source with each release

- GPLv3 requires the source of the exact FFmpeg build that you ship.
- Include the FFmpeg source at the build commit, the BtbN build scripts, and
  the source of the GPL libraries in the build (for example x264 and x265).
- Do not only link to BtbN. BtbN deletes old autobuilds.
- Done when: each GitHub release has the FFmpeg source archives.
- **Done 2026-09-25** (not yet run in CI).
  `distribution/sources/collect-btbn-ffmpeg-sources.sh` clones BtbN at the
  pinned build commit. It loads the stage selection functions of BtbN
  `generate.sh` for `win64 gpl-shared 9.0` and runs the pinned download
  command of each enabled stage (97 stages). It also archives the BtbN build
  scripts and FFmpeg at the exact commit. `SOURCES.txt` lists each stage and
  its download command.
- The manifest has new `ffmpeg` fields: `buildRepository`, `buildCommit`,
  `buildTarget`, `buildVariant`, `buildAddins`, `sourceRepository`,
  `sourceCommit`. Update them together with the FFmpeg `url`.
- Tested locally: `--list` mode (97 stages) and a real download of three
  stages (zlib, x264, vulkan-loader).
- Expected size: 1-2 GB. The job splits the `.tar` file into parts if it is
  larger than the 2 GiB asset limit of GitHub.

### L-4.3 Publish the libmpv source with each release

- The LGPL also requires the source.
- **Done 2026-09-25** (not yet run in CI). The job `native-sources` downloads
  the grid source archive (`mpv.sourceUrl`), checks `mpv.sourceSha256`, and
  adds it to the release files.
- The zhongfly build was rejected for this reason (see L-1.1).

### L-4.4 Keep FFmpeg out of the app process

- Add a rule to `docs/architecture-rules.md`: the app must start FFmpeg only as
  a separate process, with command-line arguments and pipes.
- Do not load a GPL FFmpeg build (for example through JNA or JavaCV) into the
  app. An LGPL FFmpeg build can load in process as a replaceable library.
- Done when: the rule is in `docs/architecture-rules.md`.
- **Done 2026-09-25.** The section "Third-party license boundaries" is in
  `docs/architecture-rules.md`.

## Phase 5: License key functionality

ELv2 protects only "license key functionality". Without a license check,
ELv2 does not help the goal of this plan.

### L-5.1 Write a design spec

- Cover: build expiry date, remote minimum-version check, signed license file,
  an offline grace period, and warnings before expiry.
- Decide what an expired build can do. Recommended: open projects in
  read-only mode and allow export of user data.
- Cover privacy: disclose the version check separately from the analytics
  consent.
- Done when: the spec is approved.

### L-5.2 Add an entitlement layer

- Features ask `Entitlements.has(Feature.X)`. For now, all features are free.
- Done when: the layer exists and has tests.

### L-5.3 Add the build expiry and the version check

- Implement the design from L-5.1.
- Add the version endpoint to `analytics-worker` or to a separate Worker.
- Done when: an expired test build shows the correct message and the offline
  grace period works.

## Phase 6: Release

### L-6.1 First ELv2 release

- Release notes must say that the license changed, and why.
- Done when: `Validate-Release.ps1` passes and the release has all license
  files and source archives.

### L-6.2 Before the first paid release

- Get a lawyer review of the license files and the EULA terms.
- Check codec patent licenses for H.264 (libx264) and AAC encoding. The
  hardware encoders (NVENC, QSV, AMF) can reduce this risk.
- Decide on code signing. Since 2026-09-25, signing in the release workflow is
  optional, and releases are unsigned. Azure Artifact Signing accepts
  individual developers only from the USA and Canada, so it is not available
  from Georgia. Options: an OV certificate with cloud signing (about $130-230
  per year, one-year validity), or the Microsoft Store (free registration; the
  Store signs MSIX packages). No certificate gives an instant SmartScreen pass.
- Done when: the review is complete.
