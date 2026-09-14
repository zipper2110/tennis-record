# Build and Test Workflow Design

## Status

Approved design. The implementation plan follows this design.

## Goal

Make Maven build and test runs predictable and fast enough for normal defect work.

The workflow must reduce duplicate Maven runs, identify a failed test process, and keep the UI-flow tests separate from unit tests.

## Scope

This change updates the repository build files, the Maven entry point, the test profiles, and the agent instructions.

This change does not change application behavior. This change does not add a CI service. This change does not fix the current Robot import failure.

## Build Tool Baseline

Use Maven Wrapper as the required Maven entry point.

Use Maven 3.9.16 in the wrapper. Maven lists this release as the recommended stable release. Do not use Maven 3.10 or Maven 4 preview releases for standard work.

Use JDK 17 for standard builds and tests. The project already compiles for Java 17. The build must fail early when it runs with an unsupported JDK.

Use Maven Surefire 3.6.0 and Maven Failsafe 3.6.0. Keep both plugins at the same version.

The wrapper configuration must use the official Maven distribution URL. The wrapper configuration must include the official SHA-256 checksum for the selected distribution.

## Test Levels

The repository must provide these test levels.

| Level | Command | Purpose | Normal use |
| --- | --- | --- | --- |
| Focused unit test | `./mvnw.cmd -Dtest=TestClassName test` | Check one changed behavior. | Run first. |
| Full unit test | `./mvnw.cmd test` | Check all unit tests. | Run after focused tests pass. |
| UI-flow test | `./mvnw.cmd -Pui-flow -Dit.test=FlowNameIT verify` | Check a requested Robot flow. | Run only when the change affects that flow. |
| Full validation | `./mvnw.cmd verify` | Run the normal build and unit tests. | Run before delivery when the change affects build or runtime code. |

The `ui-flow` profile must skip Surefire tests. It must run only the selected Failsafe UI-flow test. The profile must keep a single fork and no parallel UI execution.

The unit-test configuration must use one reusable fork. The configuration must stop a fork that does not exit within 15 seconds after the test run. The configuration must stop a fork that exceeds 180 seconds. Maven test reports must contain the timeout result.

The UI-flow configuration must use the same exit timeout and run timeout. It must preserve the `tennis.record.uiFlow` system property.

## Run Procedure

Use `mvnw.cmd` in Windows PowerShell. Do not call a global `mvn` command for project work.

Keep a Maven command in one terminal session while it runs. Poll that session for output. Do not start a second Maven command when the first command has no new output.

Run the focused unit test before the full unit test. Run a UI-flow test only when the change affects the tested UI flow or when the reported defect needs it.

Set a five-minute reproduction limit for a reported defect. If the defect does not reproduce in that time, save the collected evidence. Then ask for the missing steps, data, log, or screen recording. Do not change unrelated code to force a reproduction.

When a Maven run fails, first read `target/surefire-reports` or `target/failsafe-reports`. Then check the active Maven session. Stop only the process tree that the current Maven session started. Do not stop other Java processes.

## Documentation and Agent Rules

Add a build and test workflow document under `docs`. It must state the supported JDK, Maven Wrapper commands, test levels, timeout behavior, and failure procedure.

Update `AGENTS.md` with the run procedure. The instructions must require the wrapper, a persistent Maven session, focused tests before broad tests, the five-minute reproduction limit, and the restricted UI-flow test use.

## Rollout

1. Add Maven Wrapper files and the wrapper checksum.
2. Update Surefire and Failsafe together.
3. Add the JDK rule and test fork settings.
4. Change `ui-flow` to run UI-flow tests without the unit-test suite.
5. Add the workflow document and agent instructions.
6. Verify the wrapper version, a focused unit test, all unit tests, and one UI-flow test.

Use small commits for each step. Revert the commit that changes the tool configuration if a supported local environment cannot build with it.

## Acceptance Criteria

- `mvnw.cmd -version` reports Maven 3.9.16 and a supported JDK.
- Surefire and Failsafe use version 3.6.0.
- A focused unit test does not start unrelated tests.
- The `ui-flow` profile does not run the full unit-test suite before its UI-flow test.
- A hung test fork produces a bounded failure and reports its timeout.
- The build and test workflow is documented in `docs`.
- `AGENTS.md` contains the required run rules.

## Risks and Limits

The JDK rule can reject a developer environment that uses a different JDK. The error must state that JDK 17 is required.

The newer test plugins can expose existing shutdown defects. Treat each failure as a test or application defect. Do not hide it by increasing the timeout without evidence.

Robot tests can still fail because of focus, file-picker, or desktop state. The separate profile limits the cost of these failures. It does not make Robot tests deterministic.

## References

- [Apache Maven download page](https://maven.apache.org/download.cgi)
- [Apache Maven Wrapper documentation](https://maven.apache.org/tools/wrapper/)
- [Apache Maven Surefire download page](https://maven.apache.org/surefire/download.cgi)
- [Surefire test goal reference](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html)
