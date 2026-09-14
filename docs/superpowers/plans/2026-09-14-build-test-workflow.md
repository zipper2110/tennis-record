# Build and Test Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a pinned Maven build, bounded test forks, and a documented test workflow that gives fast feedback.

**Architecture:** Maven Wrapper supplies Maven 3.9.16 from repository files. Maven Enforcer rejects a non-JDK-17 build before compilation. Surefire runs unit tests in one bounded fork, and the `ui-flow` profile skips Surefire and runs only Failsafe UI-flow tests. Repository documentation and agent rules describe one run procedure.

**Tech Stack:** Maven Wrapper 3.3.4, Maven 3.9.16, JDK 17, Maven Enforcer 3.6.3, Maven Surefire 3.6.0, Maven Failsafe 3.6.0, Kotlin/JVM 17, JUnit 5, AssertJ Swing, PowerShell.

**Spec:** [2026-09-14-build-test-workflow-design.md](../specs/2026-09-14-build-test-workflow-design.md)

## Global Constraints

- Use Maven Wrapper as the required Maven entry point.
- Pin Maven Wrapper to Maven 3.9.16.
- Use JDK 17 for standard builds and tests.
- Use Maven Surefire 3.6.0 and Maven Failsafe 3.6.0.
- Keep Surefire and Failsafe at the same version.
- Use one reusable test fork.
- Use a 15-second fork-exit timeout and a 180-second fork run timeout.
- Keep UI-flow tests sequential in one Failsafe fork.
- Keep `tennis.record.uiFlow=true` only in the Failsafe UI-flow execution.
- Use `mvnw.cmd` in Windows PowerShell.
- Do not change application runtime behavior.
- Do not change the current Robot import failure in this work.
- Use Simplified Technical English in all added prose.

---

## File Structure

- `mvnw` — POSIX Maven Wrapper script.
- `mvnw.cmd` — Windows Maven Wrapper script.
- `.mvn/wrapper/maven-wrapper.properties` — Pins the Maven distribution URL and its SHA-256 checksum.
- `pom.xml` — Defines JDK validation, shared test-plugin versions, test-fork limits, and the isolated `ui-flow` profile.
- `docs/build-and-test-workflow.md` — Defines commands, test levels, timeout response, and the defect reproduction limit.
- `README.md` — Points developers to the workflow and uses wrapper commands in quick-start examples.
- `AGENTS.md` — Defines required agent behavior for Maven runs and defect reproduction.

### Task 1: Add the Maven Wrapper and the JDK Guard

**Files:**
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Modify: `pom.xml:20-31`
- Modify: `pom.xml:35-92`

**Interfaces:**
- Consumes: a temporary global Maven 3.6.3-or-later installation only to generate the first wrapper files.
- Produces: `mvnw.cmd` as the Windows project entry point and a `validate` phase that accepts only JDK 17.

- [ ] **Step 1: Obtain the Maven distribution SHA-256 value**

Run this PowerShell command. It reads the checksum from Maven Central. Do not copy a checksum from an unverified page.

```powershell
$mavenDistributionSha256 = (Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip.sha256').Content.Trim()
if ($mavenDistributionSha256 -notmatch '^[0-9a-f]{64}$') { throw "Invalid Maven 3.9.16 SHA-256 value: $mavenDistributionSha256" }
```

Expected: `$mavenDistributionSha256` contains 64 lowercase hexadecimal characters.

- [ ] **Step 2: Generate a script-only Maven Wrapper**

Run this one-time command with the temporary global Maven installation. The generated wrapper becomes the only project Maven entry point after this step.

```powershell
mvn -N org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16 -Dtype=only-script -DdistributionSha256Sum=$mavenDistributionSha256
```

Expected: the command creates `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`. It does not create `maven-wrapper.jar`.

- [ ] **Step 3: Verify the generated wrapper properties before changing the POM**

Run this PowerShell check.

```powershell
$wrapperProperties = Get-Content .mvn/wrapper/maven-wrapper.properties -Raw
if ($wrapperProperties -notmatch 'distributionUrl=.*apache-maven-3\.9\.16-bin\.zip') { throw 'The wrapper does not select Maven 3.9.16.' }
if ($wrapperProperties -notmatch 'distributionSha256Sum=[0-9a-f]{64}') { throw 'The wrapper has no valid distribution SHA-256 value.' }
if (Test-Path .mvn/wrapper/maven-wrapper.jar) { throw 'The wrapper must use only-script mode.' }
```

Expected: the command exits with code 0.

- [ ] **Step 4: Add explicit build-tool properties and the JDK 17 Enforcer rule**

Add these properties after `<app.mainClass>` in `pom.xml`.

```xml
<maven.enforcer.version>3.6.3</maven.enforcer.version>
<maven.test.plugins.version>3.6.0</maven.test.plugins.version>
```

Add this plugin before `kotlin-maven-plugin` in `<build><plugins>`.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>${maven.enforcer.version}</version>
    <executions>
        <execution>
            <id>require-jdk-17</id>
            <phase>validate</phase>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireJavaVersion>
                        <version>[17,18)</version>
                        <message>JDK 17 is required to build Tennis Record.</message>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Do not change `<kotlin.compiler.jvmTarget>` or `<maven.compiler.release>`. Both must remain `17`.

- [ ] **Step 5: Run the wrapper and JDK validation checks**

Run these commands in the same terminal session.

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -q validate
```

Expected: `-version` reports Apache Maven 3.9.16 and Java 17. `validate` exits with code 0. If it reports a non-JDK-17 environment, select JDK 17. Do not bypass the Enforcer rule.

- [ ] **Step 6: Commit the reproducible build entry point**

```powershell
git add mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.properties pom.xml
git commit -m "build: add Maven wrapper and JDK guard"
```

### Task 2: Bound Test Forks and Isolate the UI-flow Profile

**Files:**
- Modify: `pom.xml:60-80`
- Modify: `pom.xml:199-230`
- Test: `src/test/kotlin/org/litvin/ui/flow/UiFlowProfileGuardTest.kt`
- Create: `src/test/kotlin/org/litvin/ForkExitTimeoutProbeTest.kt`

**Interfaces:**
- Consumes: `maven.test.plugins.version` from Task 1 and the existing `tennis.record.uiFlow` property.
- Produces: bounded Surefire and Failsafe forks, a controlled fork-exit-timeout probe, and a `ui-flow` profile that runs selected `*UiFlowIT` classes without the Surefire suite.

- [ ] **Step 1: Record the focused-test baseline**

Run the existing fast guard test through the wrapper.

```powershell
.\mvnw.cmd -q -Dtest=UiFlowProfileGuardTest test
```

Expected: the command exits with code 0. The test confirms that the UI-flow system property is absent from a normal Surefire run.

- [ ] **Step 2: Configure Surefire and Failsafe with shared versions and limits**

Replace the existing empty Surefire plugin configuration with this configuration.

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>${maven.test.plugins.version}</version>
    <configuration>
        <forkCount>1</forkCount>
        <reuseForks>true</reuseForks>
        <forkedProcessExitTimeoutInSeconds>15</forkedProcessExitTimeoutInSeconds>
        <forkedProcessTimeoutInSeconds>180</forkedProcessTimeoutInSeconds>
    </configuration>
</plugin>
```

Replace the existing Failsafe plugin configuration with this configuration.

```xml
<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven.test.plugins.version}</version>
    <configuration>
        <forkCount>1</forkCount>
        <reuseForks>true</reuseForks>
        <forkedProcessExitTimeoutInSeconds>15</forkedProcessExitTimeoutInSeconds>
        <forkedProcessTimeoutInSeconds>180</forkedProcessTimeoutInSeconds>
    </configuration>
</plugin>
```

Remove every remaining literal `2.22.2` version for Surefire and Failsafe. Remove the duplicate fork configuration from the profile after moving it to the shared Failsafe plugin.

- [ ] **Step 3: Make the UI-flow profile skip Surefire and retain its Failsafe contract**

Add this Surefire override at the start of the `ui-flow` profile `<plugins>` list.

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <skipTests>true</skipTests>
    </configuration>
</plugin>
```

Replace the profile Failsafe configuration with this configuration. Keep the existing `integration-test` and `verify` goals.

```xml
<configuration>
    <includes>
        <include>**/*UiFlowIT.*</include>
    </includes>
    <parallel>none</parallel>
    <systemPropertyVariables>
        <tennis.record.uiFlow>true</tennis.record.uiFlow>
    </systemPropertyVariables>
</configuration>
```

The common Failsafe configuration from Step 2 supplies the fork count, fork reuse, and both timeouts.

- [ ] **Step 4: Add a controlled Surefire fork-exit-timeout probe**

Create `src/test/kotlin/org/litvin/ForkExitTimeoutProbeTest.kt` with this code.

```kotlin
package org.litvin

import org.junit.jupiter.api.Test

class ForkExitTimeoutProbeTest {

    @Test
    fun leavesANonDaemonThreadOnlyWhenTheProbeIsEnabled() {
        if (System.getProperty("tennis.record.test.forkExitTimeoutProbe") != "true") {
            return
        }

        val thread = Thread(
            { Thread.sleep(Long.MAX_VALUE) },
            "fork-exit-timeout-probe",
        )
        thread.isDaemon = false
        thread.start()
    }
}
```

The test does nothing during normal test runs. It creates one non-daemon thread only when the named system property is `true`.

- [ ] **Step 5: Verify the effective test configuration**

Run these commands.

```powershell
.\mvnw.cmd -q help:effective-pom -Doutput=target/effective-pom.xml
$effectivePom = Get-Content target/effective-pom.xml -Raw
if (($effectivePom | Select-String -AllMatches '<artifactId>maven-surefire-plugin</artifactId>\s*<version>3\.6\.0</version>').Matches.Count -ne 1) { throw 'Surefire 3.6.0 is not effective.' }
if (($effectivePom | Select-String -AllMatches '<artifactId>maven-failsafe-plugin</artifactId>\s*<version>3\.6\.0</version>').Matches.Count -ne 1) { throw 'Failsafe 3.6.0 is not effective.' }
if ($effectivePom -notmatch '<forkedProcessExitTimeoutInSeconds>15</forkedProcessExitTimeoutInSeconds>') { throw 'The fork-exit timeout is missing.' }
if ($effectivePom -notmatch '<forkedProcessTimeoutInSeconds>180</forkedProcessTimeoutInSeconds>') { throw 'The fork run timeout is missing.' }
```

Expected: the commands exit with code 0.

- [ ] **Step 6: Verify normal unit tests, the timeout probe, and the isolated UI-flow test**

Run each command in one persistent terminal session. Use `clean` before the UI-flow command so old Surefire reports cannot hide an unwanted unit-test run.

```powershell
.\mvnw.cmd -q -Dtest=UiFlowProfileGuardTest test
.\mvnw.cmd -q test
.\mvnw.cmd -q -Dtest=ForkExitTimeoutProbeTest -Dtennis.record.test.forkExitTimeoutProbe=true test 2>&1 | Tee-Object target/fork-exit-timeout-probe.log
if ($LASTEXITCODE -eq 0) { throw 'The enabled fork-exit-timeout probe must fail.' }
if (-not (Test-Path target/fork-exit-timeout-probe.log)) { throw 'The timeout probe log is missing.' }
.\mvnw.cmd -q clean
.\mvnw.cmd -q -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
if (Test-Path target/surefire-reports) { throw 'The ui-flow profile started Surefire tests.' }
if (-not (Test-Path target/failsafe-reports/TEST-org.litvin.ui.flow.spike.AssertJSwingCompatibilityUiFlowIT.xml)) { throw 'Failsafe did not produce the selected UI-flow report.' }
```

Expected: the focused test and all unit tests exit with code 0. The enabled timeout probe exits with a nonzero code after the bounded fork-exit wait and writes `target/fork-exit-timeout-probe.log`. The UI-flow command creates only the selected Failsafe report. If the UI-flow test fails, keep its reports and diagnostics. Do not increase the timeout without a recorded cause.

- [ ] **Step 6: Commit the test workflow configuration**

```powershell
git add pom.xml src/test/kotlin/org/litvin/ForkExitTimeoutProbeTest.kt
git commit -m "build: isolate bounded UI flow tests"
```

### Task 3: Document the Run Procedure and Add Agent Rules

**Files:**
- Create: `docs/build-and-test-workflow.md`
- Modify: `README.md:24-75`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: the wrapper command, JDK guard, test levels, and timeout behavior from Tasks 1 and 2.
- Produces: one user workflow document and one required agent procedure.

- [ ] **Step 1: Add the build and test workflow document**

Create `docs/build-and-test-workflow.md` with these sections and commands.

```markdown
# Build and Test Workflow

## Required Tools

Use JDK 17. Use the repository Maven Wrapper. In Windows PowerShell, run `./mvnw.cmd`. Do not use a global `mvn` command for project work.

## Test Levels

| Level | Command | Use |
| --- | --- | --- |
| Focused unit test | `./mvnw.cmd -Dtest=TestClassName test` | Run this first for a changed behavior. |
| Full unit test | `./mvnw.cmd test` | Run this after the focused unit test passes. |
| UI-flow test | `./mvnw.cmd -Pui-flow -Dit.test=FlowNameIT verify` | Run this only for an affected UI flow or a UI defect reproduction. |
| Full validation | `./mvnw.cmd verify` | Run this before delivery for build or runtime changes. |

The `ui-flow` profile skips unit tests. It runs only Failsafe UI-flow tests. UI-flow tests use one fork and no parallel execution.

## Maven Run Procedure

Keep each Maven command in one terminal session. Poll that session for output. Do not start another Maven command while the first command runs.

Surefire and Failsafe stop a test fork that does not exit within 15 seconds after test completion. They also stop a test fork that runs for more than 180 seconds.

If Maven fails, first read `target/surefire-reports` or `target/failsafe-reports`. Then check the Maven session that started the run. Stop only its process tree. Do not stop unrelated Java processes.

## Defect Reproduction Limit

Try to reproduce a reported defect for no more than five minutes. Save the steps, test data, log, and screen result that you collect. If the defect does not reproduce, ask for the missing evidence. Do not change unrelated code to force a reproduction.
```

- [ ] **Step 2: Replace the README build and test examples**

In `README.md`, replace `Maven 3.9+` in the prerequisites list with `Maven Wrapper files in this repository`. Replace the build command with this command.

```powershell
.\mvnw.cmd -DskipTests package
```

Replace the full-test and architecture-test commands with these commands.

```powershell
.\mvnw.cmd test
.\mvnw.cmd -q -Dtest=ArchitectureDependencyHygieneTest test
```

Replace the UI-flow table commands with `./mvnw.cmd -B test` and `./mvnw.cmd -B -Pui-flow verify`. Add this sentence after the table.

```markdown
For command selection, timeout response, and defect reproduction rules, see [docs/build-and-test-workflow.md](docs/build-and-test-workflow.md).
```

- [ ] **Step 3: Add the required agent rules**

Append this section to `AGENTS.md`.

```markdown
## Build and Test Runs

Use `mvnw.cmd` for Maven commands in Windows PowerShell. Do not use a global `mvn` command for project work.

Keep a Maven command in one terminal session while it runs. Poll that session. Do not start a second Maven command when the first command has no new output.

Run a focused unit test before a full unit test. Run a UI-flow test only when the change affects that flow or when it is needed to reproduce a reported defect.

Stop defect reproduction after five minutes when the defect does not reproduce. Save the collected evidence. Ask for the missing steps, data, log, or screen recording.

Read `target/surefire-reports` or `target/failsafe-reports` after a Maven test failure. Stop only the process tree that the current Maven session started. Do not stop other Java processes.
```

- [ ] **Step 4: Verify the documented commands and rules**

Run this PowerShell check.

```powershell
$requiredText = @(
    'JDK 17',
    'mvnw.cmd',
    'five minutes',
    'target/surefire-reports',
    'target/failsafe-reports'
)
$workflowText = Get-Content docs/build-and-test-workflow.md -Raw
$agentText = Get-Content AGENTS.md -Raw
foreach ($text in $requiredText) {
    if ($workflowText -notlike "*$text*") { throw "Workflow document does not contain: $text" }
    if ($agentText -notlike "*$text*") { throw "Agent instructions do not contain: $text" }
}
```

Expected: the command exits with code 0. Read the rendered Markdown files. Confirm that the command table is readable and each command starts with the repository wrapper.

- [ ] **Step 5: Commit the workflow documentation**

```powershell
git add docs/build-and-test-workflow.md README.md AGENTS.md
git commit -m "docs: define Maven test workflow"
```

### Task 4: Run the Final Build Validation and Record Results

**Files:**
- Modify: no source files.
- Review: `pom.xml`
- Review: `.mvn/wrapper/maven-wrapper.properties`
- Review: `docs/build-and-test-workflow.md`
- Review: `AGENTS.md`
- Review: `target/surefire-reports`
- Review: `target/failsafe-reports`

**Interfaces:**
- Consumes: all completed wrapper, POM, and documentation changes.
- Produces: recorded verification evidence for the workflow change.

- [ ] **Step 1: Check the committed file scope**

Run these commands.

```powershell
git status --short
git diff --check HEAD~3..HEAD
```

Expected: only the wrapper, `pom.xml`, `docs/build-and-test-workflow.md`, `README.md`, and `AGENTS.md` changes appear in these commits. `git diff --check` reports no content whitespace errors.

- [ ] **Step 2: Verify the build baseline and focused unit test**

Run these commands in one persistent terminal session.

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -q validate
.\mvnw.cmd -q -Dtest=UiFlowProfileGuardTest test
```

Expected: Maven 3.9.16 and JDK 17 are reported. Each command exits with code 0.

- [ ] **Step 3: Verify all unit tests and one isolated UI-flow test**

Run these commands in the same terminal session.

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q clean
.\mvnw.cmd -q -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
```

Expected: the unit-test command exits with code 0. The UI-flow command runs only the selected Failsafe test. If a test fails, save the report directory before any retry. Classify the failure as a product defect, test defect, or environment defect. Do not change the timeout until the report gives a cause.

- [ ] **Step 4: Inspect reports and complete the validation record**

Run this PowerShell command after each test run.

```powershell
Get-ChildItem target/surefire-reports,target/failsafe-reports -ErrorAction SilentlyContinue | Select-Object FullName,Length,LastWriteTime
```

Record the command, exit code, duration, report path, and result in the delivery message. State any known baseline log output separately from a failed Maven exit code.

- [ ] **Step 5: Commit only if the final validation changes tracked files**

Run this command.

```powershell
git status --short
```

Expected: no tracked files changed after validation. Do not create an empty commit. If a tracked file changed only to correct a verification issue, add it to the relevant Task 1, Task 2, or Task 3 commit and rerun that task's checks.
