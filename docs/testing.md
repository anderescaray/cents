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

`Dispatchers.Main` in these tests is an `UnconfinedTestDispatcher`, while Room emits on its own
threads. A view model that collects a Room flow can therefore still be working when the test's
next line runs. On the phone everything runs on the main thread, so this is a test hazard, not
an app bug. Four rules follow from it:

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

Turbine's waits give up after 3 s at the line that waited. A bare `first { }` waits for
`runTest`'s one-minute timeout.

**Known flake:** `CategoryEditViewModelTest` › *the same name is free on the other kind*.
`nameTaken` is computed from names a Room collector writes on another thread, so a keystroke and
the first load can cross. If it fails, re-run it and give the fix its own branch. The fix at the
root has not been tried yet: pin the test database's query context to the test thread
(`setQueryCoroutineContext` in `createTestDatabase()`). It touches every Room test, so it needs a
full run of its own.

## Compose tests

- **Pin the screen size** with `@Config(qualifiers = "w411dp-h891dp")` when layout matters.
- **Find nodes by what the user perceives**: text, content description, or a `testTag` constant
  exported by the screen (`internal const val …_TAG`). Never by position.
- **Font scale needs Robolectric's native graphics** (`FontScaleTest`), because the default mode
  measures text ignoring font size. A dialog reads its font scale from the device, so an alert
  growing with the font is checked on the phone only.
