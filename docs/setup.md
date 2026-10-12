# Setup

Everything needed to build Cents and run it on a phone, from a clean Windows machine. Commands
for day-to-day work are in [`CLAUDE.md` §6](../CLAUDE.md#6-commands).

## The machine

1. **Keep the repo out of OneDrive.** Gradle writes thousands of temporary files per build, and
   syncing them makes builds slow and can corrupt the project. The repo lives at `C:\dev\cents`.
2. **Android Studio** (`winget install --id Google.AndroidStudio`). Open it once and pick the
   **Standard** install, which downloads the SDK (4–6 GB). It bundles `adb` and the JetBrains
   Runtime (JDK 25), which is the JDK Gradle uses. Do not install another JDK.
3. **GitHub CLI** (`winget install --id GitHub.cli`), then `gh auth login` (interactive: run it
   yourself, or as `! gh auth login` from Claude Code). Choose GitHub.com › HTTPS › yes ›
   web browser.
4. **Git identity**, once:
   ```powershell
   git config --global user.name "Ander"
   git config --global user.email "anderescaray@gmail.com"
   git config --global init.defaultBranch main
   ```
5. **Long paths**, for Orca's worktrees under `~\orca\workspaces\cents\<branch>`, which go past
   260 characters: `git config --global core.longpaths true` and Windows' `LongPathsEnabled`.
   `.worktreeinclude` copies `local.properties` into each worktree.

From a Bash shell, `JAVA_HOME`, `adb` and `gh` are not on `PATH`. `CLAUDE.md` §6 has the two
lines that set them.

**Never commit** `local.properties`, `build/`, `.gradle/`, `*.keystore` or `*.jks`. The
`.gitignore` covers them. Check `git status` before a first commit on a new machine anyway.

## Phones

| Device | Role |
|---|---|
| **Pixel 11 Pro** (Android 17) | The owner's phone, where the real data lives. Walks happen here. |
| **Honor tablet** (`HEY3`) | Development and layout checks. Not where the data lives. |

To connect one: Settings › About phone › tap **Build number** seven times, then Developer
options › **USB debugging**, plug in, and accept the prompt. `adb devices` must list it as
`device`, not `unauthorized`. Screenshots need it awake and unlocked.

- **Never install a build older than the one on the Pixel.** The database only migrates forward.
  An older build needs an uninstall, which deletes the data. Back up first:
  `C:\dev\cents-device-backups\` (outside the repo) holds earlier copies of the app's data.
- **Capture on the Pixel** needs *Notification access* for Cents, granted from Settings ›
  Capture. Battery optimisation may have to be turned off for Cents, or the system can stop the
  listener.

## Google Drive backup: the OAuth client

The daily backup (D22) needs an **Android OAuth client** in Google Cloud. It has no secret, and
Google matches it by package name and signing certificate, so nothing goes in the repo or in
`local.properties`. Done once, on 2026-09-24. A **release key**, if one is ever made, needs a
second Android client with its own SHA-1.

1. console.cloud.google.com › new project **Cents**.
2. APIs & Services › Library › **Google Drive API** › Enable.
3. Google Auth Platform: app name Cents, audience **External**, left in **Testing**, with the
   owner's account as a **test user**.
4. Data access › add the scope `.../auth/drive.appdata`.
5. Clients › Create client › **Android**: package `io.github.anderescaray.cents`, and the debug
   key's SHA-1, `A6:15:3B:AD:C8:AB:09:A0:FF:34:14:D9:24:4A:2F:FA:93:13:B6:13`. That key is
   `%USERPROFILE%\.android\debug.keystore`, shared by every build on this PC. On a
   Spanish-locale JDK, `keytool` crashes printing a certificate unless it is run with
   `-J-Duser.language=en`.

**If connecting ever fails with an authorization error, check this client before the code.** In
*Testing* mode the grant expires after seven days, and the app then asks to **Reconnect**.

## Cloud sessions

A Claude Code session in the cloud runs every gate, given three things. Checked on 2026-10-12, on
a container with JDK 21 and no Android SDK; the first full run took ten minutes, most of it
downloads.

1. **`dl.google.com` and `api.foojay.io` allowed** in the environment's network settings. The
   first serves Google Maven and the Android SDK. The second serves the JDK 25 that
   `gradle/gradle-daemon-jvm.properties` asks for, which Gradle then downloads by itself.
2. **The Android SDK**, from `dl.google.com`, named in `local.properties` (git-ignored). Take the
   cmdline-tools zip and its SHA-1 from `repository2-3.xml` there, unpack it to
   `/opt/android-sdk/cmdline-tools/latest`, then:

   ```bash
   SM=/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager
   yes | $SM --sdk_root=/opt/android-sdk --licenses
   $SM --sdk_root=/opt/android-sdk --no-metrics "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
   echo "sdk.dir=/opt/android-sdk" > local.properties
   ```

3. **Maven Central through Google's mirror.** Central answers HTTP 429 to the container's shared
   IP, so a build fails a few artifacts at a time. A user-level init script, outside the
   repository, points Central's URL at the mirror:

   ```kotlin
   // ~/.gradle/init.d/central-mirror.init.gradle.kts
   val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

   fun RepositoryHandler.preferCentralMirror() {
       withType(MavenArtifactRepository::class.java).configureEach {
           if (url.host == "repo.maven.apache.org" || url.host == "repo1.maven.org") setUrl(centralMirror)
       }
   }

   settingsEvaluated {
       pluginManagement.repositories.preferCentralMirror()
       dependencyResolutionManagement.repositories.preferCentralMirror()
   }
   ```

   Robolectric downloads its `android-all` jar from Central while the tests run, so it needs the
   mirror too: add `-Drobolectric.dependency.repo.url=https://maven-central.storage-download.googleapis.com/maven2`
   to `JAVA_TOOL_OPTIONS` before `./gradlew`.

Without the first, run ktlint and detekt as CLIs and let CI build and test the PR.
