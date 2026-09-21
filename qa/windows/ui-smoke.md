# Packaged Windows UI Smoke

Run this only on Windows from a packaged app image with its libmpv and FFmpeg dependencies present. It checks native integration that the deterministic `ui-flow` Maven tests intentionally fake.

```powershell
pwsh -File qa/windows/Run-UiSmoke.ps1 -KeepArtifacts
```

The runner prints absolute paths for the report, screenshots/artifacts directory, and isolated app-data directory. Use the checked-in `src/test/resources/media/ui-smoke.mp4`; do not substitute a local video. The runner leaves the QA root in place on failure and when `-KeepArtifacts` is supplied. To validate fixture, FFprobe metadata, and a known executable without launching it:

```powershell
pwsh -File qa/windows/Run-UiSmoke.ps1 -ExecutablePath "C:\path\to\Tennis Record.exe" -ValidateOnly
```

## Checklist

1. **Projects launch state** — The app opens on Projects. Project-dependent navigation is unavailable until a project is selected. Capture a screenshot.
2. **Import the fixture** — Choose *Import Match*, use the native chooser to select `ui-smoke.mp4`, and confirm Points opens with the project/source path. Capture a screenshot.
3. **Video playback** — Play, pause, and seek within the short clip. Preview state and timeline position should change without an error dialog. Capture a screenshot.
4. **Edit and score** — In Points mark one point boundary pair; set Colors brightness to 20; set Crop rotation to 15 degrees; then award Player 1 a point in Scoring. The visible values and saved project data should retain each edit. Capture a screenshot.
5. **FFmpeg export** — Configure a 1920x1080 export with the short clip, start it, and wait for a completed output. The output must play and have the expected resolution. Capture a screenshot.
6. **Relaunch and recents** — Close the app, relaunch it with the same runner-created app-data directory, and confirm the project appears in recents and reopens. Capture a screenshot.

## Report fields

Complete the report path printed by the runner. For every step, record pass/fail, screenshot path, expected result, and actual result. For any failure also record exact reproduction steps, severity (`blocker`, `high`, `medium`, or `low`), and a triage summary identifying product behavior, packaging/native integration, or smoke-harness ownership.

Do not commit generated reports, app data, exports, or screenshots. They belong under ignored `target/ui-smoke`.
