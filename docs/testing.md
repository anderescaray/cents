# Testing

How the tests in this repo are written and run, and the traps already met. Every test runs on
the JVM (Robolectric), so CI covers the database and the UI without a device. What only a phone
can check goes in [`device-check.md`](device-check.md).

## Running the gates

```bash
./gradlew spotlessApply                                           # format first
./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug     # what CI runs
```

- **Read the results, not `BUILD SUCCESSFUL`.** Open
  `app/build/test-results/testDebugUnitTest/*.xml`, or the failing test in the CI log, and count
  the tests and failures.
- **A green run is one machine, one order, one run.** A race can pass locally and fail on CI's
  slower runner, or fail on a class that did nothing wrong. When in doubt, run the suite twice.
- **A red check on a PR that changes no code is a test problem, not a conflict.** Read the
  failing test in the log before re-running anything. To re-run only what failed:
  `gh run rerun <id> --failed`.
- **On Windows**, if Spotless fails reading `app/build/spotless-clean`, retry with
  `--no-parallel --max-workers=1`.

## Benchmarks and the Baseline Profile

The `:baselineprofile` module (#146) runs on the connected phone only, never in CI. It installs
`benchmarkRelease`, an R8 build under the id `io.github.anderescaray.cents.benchmark`, so a run
never touches the owner's Cents or its data. Each test first seeds it with an invented two-year
ledger through the real backup restore (`app/src/benchmark`, `SeedActivity`).

```bash
adb shell svc power stayon usb                                   # keep the screen on while plugged in
./gradlew :app:generateBaselineProfile                           # rewrites app/src/release/generated/baselineProfiles
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # startup and scrolling, with and without the profile
adb shell svc power stayon false                                 # put the phone back as it was
```

- **Unlock the phone first, and keep it on.** Every failed run so far failed only because the
  phone locked. `stayon usb` needs the owner's say-so, which was given on 2026-10-20.
- **Commit the regenerated profile** after a change to startup or the tabs. It is a source file,
  and a stale one is quietly worse than none.
- **Results** are in `baselineprofile/build/outputs/connected_android_test_additional_output/`.
  Read the medians, not one run: this phone is fast, so the profile's gain is small (about 7 % on
  a cold start on 2026-10-20, in #146).
- **A crash on the first frame of a release build is R8**, not the profile. Navigation finds an
  enum route argument by name, so `app/proguard-rules.pro` keeps `domain.model`'s enums. A new
  enum argument elsewhere needs the same rule.

## Where tests go

| What | How | Example |
|---|---|---|
| DAO and repository | In-memory Room from `createTestDatabase()` | `TransactionFilterTest` |
| Migration | `CentsDatabaseMigrationTest`, one case per version step (rule 9) | — |
| View model | Real repositories over in-memory Room, Turbine for `uiState` | `TransactionFormViewModelTest` |
| Compose screen | Compose test rule under Robolectric | `TransactionFormScreenTest` |
| HTTP (Drive) | `mockwebserver3`, checking the request actually sent | — |
| Workers | `work-testing`, running the real worker | — |

A schema change also commits its exported `app/schemas/.../<version>.json`. If a local build
rewrites an existing one, look at that diff before anything else.

## View-model tests and Room

`Dispatchers.Main` in these tests is an `UnconfinedTestDispatcher`, and the test database runs
every query, transaction and flow on the thread that asks for it (`createTestDatabase()`, #125).
So a view model under test runs on the test thread alone, as it runs on the phone's main thread.
One difference is left, on purpose: a write's flows emit before the write returns, where on the
phone they come a moment later. A test must not depend on either order. Five rules follow:

1. **Wait for the state you are about to assert on, by name.** Use `awaitStateThat { … }` from
   `AwaitState.kt`, with the condition the assertion checks. Not a proxy:
   `categories.isNotEmpty()` passes while only the first of two rows has arrived. Never read
   `uiState.value` straight after an action.
2. **A wait for a value the state already holds never fires.** `StateFlow` drops repeats. Wait
   for what the action *changes*, or for a change made just for the test.
3. **After a save, wait for `finished`, or read the flow with a predicate,** before reading the
   database. Taking a flow's first emission races the write.
4. **Wrap every view model in `ViewModelKeeper.keep(…)` and stop it before closing the
   database.** A leaked collector keeps using the test's `Dispatchers.Main`. The next class then
   fails with "Dispatchers.Main is used concurrently", in a test that did nothing wrong.
5. **Never count a timer on `viewModelScope`.** `runTest` shares its clock with the test's
   `Dispatchers.Main`, so while a test waits, virtual time skips a `delay` there.
   #120's correction undo closed before the removal it offered back had landed: its test failed
   every time on Windows and passed on CI by timing (#121). An undo window is an `UndoWindow`
   (`ui/undo/UndoWindow.kt`) on the `@ApplicationScope` scope. A test hands it a scope on a
   `TestCoroutineScheduler` of its own, and moves that clock only after waiting for the state
   the action changes.

Turbine's waits give up after 3 s at the line that waited. A bare `first { }` waits for
`runTest`'s one-minute timeout.

**No known flakes.** A view-model test that fails only sometimes is a new problem. First check
that its database comes from `createTestDatabase()`; `TestDatabaseFactoryTest` fails if that
database ever goes back to Room's own threads. Because that database allows main-thread queries,
the same test also requires every DAO method to suspend or return a `Flow`.

## Compose tests

- **Pin the screen size** with `@Config(qualifiers = "w411dp-h891dp")` when layout matters.
- **Find nodes by what the user perceives**: text, content description, or a `testTag` constant
  exported by the screen (`internal const val …_TAG`). Never by position.
- **Font scale needs Robolectric's native graphics** (`FontScaleTest`), because the default mode
  measures text ignoring font size. A dialog reads its font scale from the device, so an alert
  growing with the font is checked on the phone only.
