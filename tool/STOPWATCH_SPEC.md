# Stopwatch Tool Specification

Status: v1 implementation (reviewed against the SDK in this repository)

## Summary

Stopwatch is a Light Phone III tool for measuring elapsed time forward from
zero. It fills the gap between LightOS's existing Alarm and Timer tools: today a
user who wants to measure an unknown duration must set a countdown and work
backward.

Version 1 is intentionally small. The complete interaction model is Start,
Pause, Resume, and Reset. There are no laps, history, settings, or sounds.

## Product Goal

Let a user begin timing with one tap, glance at an unambiguous elapsed time,
leave the tool (or let the display sleep) without losing the measurement, and
return to pause or reset it.

User story: As a Light Phone user, I want to start at zero and measure how long
something takes so I do not have to work backward from a timer.

Success means the tool feels like it shipped with LightOS: quiet, immediate,
typographic, and free of configuration.

## Design Principles

1. Start immediately. Opening the tool presents the time and one primary action.
2. Preserve intent. A running stopwatch keeps counting when the display sleeps,
   the user leaves the tool, or the process is killed.
3. Prevent accidental loss. Reset is only offered while paused.
4. Derive time from a clock, never from UI ticks. Rendering cadence must have
   zero effect on the measured value.
5. Match LightOS. Use the SDK grid, theme, typography, and bars. No decorative
   containers, analog faces, animation, or color-coded state.
6. Do no work while invisible. When the screen is hidden or asleep, the tool
   runs no timers, no jobs, and writes nothing.

## Version 1 Scope

### Included

- Start from zero; pause; resume; reset (from paused) to zero
- Elapsed time displayed to tenths of a second; milliseconds tracked internally
- Timing continues across display sleep, navigation away, activity recreation,
  and process death within the same boot
- Best-effort recovery of a running session across a device reboot
- Light and dark Light palettes via `LightThemeController`

### Not Included

- Laps, splits, saved sessions, or multiple stopwatches
- Hundredths or millisecond display
- Sound, vibration, notifications, or alarms
- Settings, labels, or configurable precision
- Tool-declared permissions, network behavior, or background jobs (`LightWork`
  must not be used; its 15-minute minimum interval and deferred scheduling make
  it wrong for timekeeping, and no background work is needed). Shared SDK
  dependencies currently contribute baseline entries to the merged Android
  manifest; the stopwatch neither requests them in `lighttool.toml` nor invokes
  the corresponding capabilities.
- Keeping the display awake. The SDK exposes no window or power-manager access
  to tools (`getSystemService` and activity access are blocked by the build
  plugin), so this is infeasible today, not merely deferred. The display
  sleeps on the normal system schedule; timing is unaffected.

## Interaction Model

Three states, four actions:

| State   | Bottom bar                        | Action  | Result                        |
| ------- | --------------------------------- | ------- | ----------------------------- |
| Idle    | `START` (centered)                | `START` | Begin timing from zero        |
| Running | `PAUSE` (centered)                | `PAUSE` | Freeze at current elapsed     |
| Paused  | `RESET` (left), `RESUME` (right)  | `RESET` | Return to Idle at zero        |
| Paused  |                                   | `RESUME`| Continue from frozen value    |

- The primary action is centered, matching the LightOS convention used by the
  example tools (`ADD NEW` in authenticator, `THIS WEEK` in weather) and
  `LightBottomBar`'s native single-item layout. `START` and `PAUSE` -- the two
  most-used actions -- therefore share one stable touch target, and after
  `PAUSE` the center is empty, so a stray second tap does nothing.
- Reset needs no confirmation; requiring pause first is the guard, consistent
  with GNOME Clocks and Google Clock.
- An action received in a state that does not offer it is ignored (this makes
  rapid double-taps harmless by construction).
- Leaving the tool never implies pause. Reopening shows the state as if the
  tool had stayed visible.

## Time Display

- Below one hour: `MM:SS.t`, zero-padded except for the single tenths digit,
  e.g. `03:27.4`.
- At or above one hour: `H:MM:SS.t`, e.g. `1:03:27.4`. Hours grow without
  truncation (`27:12:05.6`, `100:00:00.0`); there is no arbitrary duration cap
  or rollover.
- The displayed tenth is the floor of the internal millisecond-precise elapsed
  time, never a rounded value. For example, 1,199 ms displays as `00:01.1`.
- The display updates ten times per second, aligned to elapsed-time tenth
  boundaries (sleep `100 - (elapsedMs % 100)` between updates), and immediately
  on every state change and screen show.
- Rationale for tenths: one changing fractional digit makes short stopwatch
  measurements feel responsive and useful without the visual churn or implied
  precision of hundredths. A 10 Hz visible-only ticker is substantially calmer
  and cheaper than a 30-100 Hz loop, while the measurement remains independent
  of ticker cadence. Precision is fixed rather than configurable so opening the
  tool remains immediate and settings-free.
- Render with a stable-width treatment so digits never shift layout:
  `LightText(monospace = true)` is the SDK-supported default. If the monospace
  family clashes aesthetically, derive one style from Light typography with
  tabular figures (`fontFeatureSettings = "tnum"`) instead of adding a type
  system.
- Center the time horizontally and vertically in the content region. Crossing
  the one-hour boundary widens the string once; because it is centered, growth
  is symmetric and the bars do not move.

## Screen Design

One screen, three fixed regions, no navigation:

1. `LightTopBar` with `LightTopBarCenter.Text("Stopwatch")`, no left button
   (this is the tool's only screen; the system back gesture exits the tool via
   the SDK).
2. A flexible central region containing only the elapsed time.
3. `LightBottomBar` with the actions for the current state.

### Idle

```text
+--------------------------------+
|           Stopwatch            |
|                                |
|                                |
|            00:00.0             |
|                                |
|                                |
|             START              |
+--------------------------------+
```

### Running

```text
+--------------------------------+
|           Stopwatch            |
|                                |
|                                |
|            03:27.4             |
|                                |
|                                |
|             PAUSE              |
+--------------------------------+
```

### Paused

```text
+--------------------------------+
|           Stopwatch            |
|                                |
|                                |
|            03:27.4             |
|                                |
|                                |
|  RESET                 RESUME  |
+--------------------------------+
```

## LightOS UI Guidance

- Wrap content in `LightTheme(colors)` fed by
  `LightThemeController.colors.collectAsState()`, and fill the background with
  `LightThemeTokens.colors.background`, exactly as the example tools do. No
  app-specific colors; running and paused are distinguished only by the action
  labels and the motion of the time.
- Bottom bar: Idle and Running pass a single `LightBarButton.Text` item
  (rendered centered); Paused passes two items (`RESET`, `RESUME`) for the
  left/right slots. Labels are uppercase.
- Elapsed time: use fixed-width monospace numerals with
  `LightTextVariant.Title` for the common `MM:SS.t` form. Once hours appear,
  use `LightTextVariant.Subtitle` while the string is 12 characters or fewer,
  then `LightTextVariant.Heading` for unusually large hour counts. These
  variants come from the Light typography system; do not add custom font sizes,
  kerning, or letter spacing.
- Use grid units (`gridUnitsAsDp`) for all spacing; do not hardcode dp.
- Keep the bars' built-in touch targets; give buttons content descriptions.

## Timekeeping Model

The single source of truth for elapsed time is:

```text
elapsed(now) = accumulatedMs                            // while paused/idle
elapsed(now) = accumulatedMs + (nowER - anchorER)       // while running
```

where `nowER` is `android.os.SystemClock.elapsedRealtime()`.

- `elapsedRealtime()` is monotonic, includes deep sleep, is unaffected by
  wall-clock and time-zone changes, survives process death, and resets only at
  reboot (https://developer.android.com/reference/android/os/SystemClock).
  The `android.os` package is not on the SDK plugin's blocked-import list, and
  the SDK itself uses it.
- The UI tick only re-renders the formatted value of `elapsed(now)`; it never
  contributes to it. A delayed, dropped, or duplicated tick cannot change the
  measurement.
- Wall-clock time (`System.currentTimeMillis()`) is persisted alongside the
  monotonic anchor solely for reboot recovery (below); it is never used while
  the process is alive.
- Wrap both clocks in an injectable `TimeSource` interface so every rule in
  this spec is unit-testable with a fake clock.

## Persistence and Restoration

`LightActivity` rebuilds the tool from its `@InitialScreen` on every activity
creation and offers no saved-instance-state hook, so activity recreation and
process death use the same restoration path: the tool's shared Preferences
`DataStore` (`lightContext.dataStore`). Navigation away leaves the screen's
view model alive, while the same persisted snapshot protects against later
process death.

### Snapshot

One atomic `dataStore.edit` writes the full snapshot, using namespaced keys:

| Key                        | Type   | Meaning                                |
| -------------------------- | ------ | -------------------------------------- |
| `stopwatch.schema`         | int    | `1`; bump on incompatible change       |
| `stopwatch.state`          | string | `idle` / `running` / `paused`          |
| `stopwatch.accumulatedMs`  | long   | Elapsed ms banked at last pause/start  |
| `stopwatch.anchorEr`       | long   | `elapsedRealtime()` at last (re)start  |
| `stopwatch.anchorWall`     | long   | `currentTimeMillis()` at last (re)start|

### When writes happen

A snapshot is written on each valid one of the four actions and once when
recovery must re-anchor or safely pause a running session. It is never written
for a display tick.
While running, every field is constant (the display changes; the state does
not), so a running stopwatch costs zero writes and needs no periodic worker.
The UI updates immediately, then the write runs in the same serialized
view-model coroutine. Storage failures are logged and leave the in-memory
stopwatch usable; a write lost to a storage failure or instant process kill
loses only that one action. Coroutine cancellation is never swallowed.

### Restoration rules (on view-model initialization)

Read the snapshot once before presenting interactive state, then:

1. Missing, unparsable, or wrong-schema snapshot: Idle at `00:00.0`.
2. `paused`: Paused at `accumulatedMs`.
3. `running`, same boot: Running with
   `elapsed = accumulatedMs + (nowER - anchorEr)`. This covers navigation
   away, display sleep, activity recreation, and process death.
4. `running`, reboot detected: best-effort wall-clock recovery. Compute
   `wallDelta = nowWall - anchorWall`. If `wallDelta >= 0`, continue Running
   with `elapsed = accumulatedMs + wallDelta`, re-anchored to fresh clock
   values and persisted. If `wallDelta < 0` (clock moved backward), give up
   gracefully: Paused at `accumulatedMs`.

Reboot detection: treat the anchor as invalid when `nowER < anchorEr`, or when
the boot epoch (`wall - elapsedRealtime`) has shifted by more than 60 seconds
since the snapshot was written. The second check catches reboots where the new
uptime already exceeds the old anchor. A near-epoch wall clock may be lower
than elapsed realtime; that does not invalidate same-boot monotonic timing. If
the wall clock changes between near-epoch and established while uptime
increases, prefer the monotonic same-boot interpretation. If a reboot is
otherwise detected while either wall clock cannot represent a non-negative
boot epoch, pause at the last banked value because wall-clock recovery is not
trustworthy.

Honest limits, stated rather than promised away:

- A manual clock change larger than 60 seconds while the process is dead is
  indistinguishable from a reboot and makes recovery approximate by the size
  of the change. The display must still never be negative or decrease.
- With a near-epoch wall clock, a reboot whose new uptime already exceeds the
  old anchor can be indistinguishable from same-boot process death. The tool
  prioritizes exact same-boot timing; cross-reboot recovery in that case may
  undercount. A definitively detected reboot without a usable wall clock pauses
  at the last banked value.
- Millisecond precision is exact within a boot and approximate (wall-clock
  granularity) across one.

Restoration is asynchronous and the SDK splash lifetime is not coupled to the
DataStore read. Begin in a non-interactive loading state. The splash will hide
this state during the normal fast path; if loading remains visible, show the
standard title and a subdued `Loading...` label with no bottom-bar actions.
Never render an interactive Idle default before restoration completes.

## Recommended Architecture

### Implementation standards

Follow the current Android practices required by the Light SDK root README:

- Write all production source in Kotlin.
- Build the UI with Jetpack Compose and the Light SDK UI components. Composables
  render state and emit user actions; they do not own timekeeping or persistence.
- Use Kotlin Coroutines and `Flow` / `StateFlow` for asynchronous work and
  observable state. Keep coroutines in structured scopes and cancel the visible
  ticker with the screen lifecycle.
- Use MVVM: `StopwatchScreen` owns Compose rendering and forwards actions;
  `StopwatchViewModel` owns stopwatch state, transitions, persistence, and
  ticker lifecycle. Pure timing, reduction, and formatting logic remains
  independent of both layers for unit testing.

These practices must be applied through the SDK's public abstractions and
allow-listed dependencies; ordinary Android APIs that bypass the Light sandbox
remain prohibited.

Small, deterministic, and test-first; roughly four files plus tests:

- `StopwatchState` -- sealed: `Idle`, `Running(anchorEr, anchorWall,
  accumulatedMs)`, `Paused(accumulatedMs)`.
- A pure reducer `reduce(state, action, now): StopwatchState` implementing the
  Interaction Model table; unsupported actions return the input state.
- `TimeSource` -- `elapsedRealtimeMs()` / `wallClockMs()`; real implementation
  wraps `SystemClock` / `System`, tests use a fake.
- Pure formatter `formatElapsed(elapsedMs): String` for the Time Display rules.
- `StopwatchScreen : LightScreen<Unit, StopwatchViewModel>` annotated
  `@InitialScreen`; the view model owns a `StateFlow<StopwatchState>`, the
  restoration read, snapshot writes, and the ten-times-per-second ticker
  coroutine.
- The UI state begins as not ready and becomes interactive only after the
  restoration read completes.
- Ticker lifecycle: run only while state is Running and the screen is visible.
  Start/refresh in `onScreenShow` (the SDK invokes it on both navigation and
  activity resume) and cancel in `onAppPause`/`onScreenHide`. Display sleep
  pauses the activity, so a sleeping phone does zero work.

No entry point, no `LightWork`, no `callRemoteServiceMethod`, no permissions in
`lighttool.toml`, and no dependencies beyond what the scaffold already allows.
Production metadata targets `com.lightos`. Local emulator runs temporarily
switch `serverPackage` to `com.thelightphone.sdk.emulator`.

## Battery

- Zero timers, writes, or wake-ups while the screen is off or the tool is
  hidden; the running state is a pair of numbers, not a process.
- Up to ten recompositions per second while visible and running; none while
  paused.
- No wake locks and no keep-awake (infeasible via the SDK; see Scope).

## Acceptance Criteria

1. A fresh install shows `00:00.0` and a centered `START`.
2. `START` begins counting up at tenth-second cadence and the action becomes a
   centered `PAUSE` in the same position.
3. `PAUSE` freezes the value and shows `RESET` (left) and `RESUME` (right).
4. `RESUME` continues from the frozen value without losing banked time.
5. `RESET` from Paused returns to criterion 1's state and persists it.
6. No reset affordance exists while running; repeated rapid taps on any action
   never corrupt state or skip states.
7. Letting the display sleep, navigating home, or killing the tool process
   (same boot) and reopening shows the correct current elapsed time and state;
   a stopwatch running across all of these reads as if it never stopped.
8. Rebooting mid-run recovers the session per Restoration rule 4 and never
   shows a negative or decreasing time.
9. Changing the time zone does not change the measurement. Changing the wall
   clock while the process is alive does not change the measurement; recovery
   after a large wall-clock change while process-dead follows the documented
   best-effort reboot heuristic.
10. Delaying or dropping UI updates does not change the measurement (verified
    with a fake clock: the value derives from `elapsed(now)` only).
11. Digit changes and state changes cause no layout shift; the one-hour
    transition re-centers symmetrically without moving the bars.
12. Representative hour-bearing strings through at least `100:00:00.0` fit at
    the LP3 reference size (1080 x 1240) in both light and dark themes.
13. The tool declares no `lighttool.toml` permissions, performs no network or
    background work, and adds no dependencies beyond the existing allow-list.
    SDK-transitive manifest entries are not treated as stopwatch capabilities.
14. While running with the screen off, no coroutines tick and no DataStore
    writes occur.

## Validation Plan

- Unit-test the reducer for every (state, action) pair, including ignored
  actions.
- Unit-test the formatter around tenth, minute, and hour boundaries, including
  negative input, `Long.MAX_VALUE`, and a 100+ hour value.
- Unit-test the delay to the next tenth boundary at exact, adjacent, negative,
  and saturating elapsed values.
- Unit-test pause/resume accumulation and tick-independence with a fake
  `TimeSource`.
- Unit-test restoration against fabricated snapshots: each state, missing
  keys, wrong schema, same-boot process death, `nowER < anchorEr` reboot,
  boot-epoch-shift reboot, and backward wall clock (`wallDelta < 0`).
- Round-trip every state through a real Preferences DataStore and verify an
  empty store restores as missing state.
- On the LightOS emulator: display sleep, home navigation, process kill
  (`adb shell am kill`), and reboot while running; verify each restoration
  rule and confirm no ticking while backgrounded.
- Visually inspect both themes at the LP3 reference viewport for fit and
  layout stability across the hour boundary.
- On hardware when available: touch comfort, and battery over a multi-hour
  running session with the screen mostly off (expectation: indistinguishable
  from idle).

## Open Platform Questions

None block version 1.

- Will a future SDK API surface an active stopwatch on the LightOS home
  screen or status line?
- Could the SDK expose a system boot token so reboot detection does not rely
  on clock heuristics?
- If laps ever ship, revisit whether tenths remains the right precision rather
  than assuming more digits are useful.

## Reference Behavior

- Apple's Stopwatch keeps timing while other apps are open or the phone sleeps,
  and exposes Reset only after Stop:
  https://support.apple.com/guide/iphone/use-the-stopwatch-iph96b1e460/ios
- GNOME Clocks uses Start / Pause / Resume / Clear, with Clear available only
  while paused: https://help.gnome.org/gnome-clocks/stopwatch.html
- LightOS Timer uses explicit text actions and seconds resolution, reinforcing
  text-first, calm controls; the fixed tenths digit is a deliberate stopwatch
  distinction rather than a change to that interaction model:
  https://support.thelightphone.com/hc/en-us/articles/24571548717716-Timer-Tool
- `SystemClock` semantics (monotonic bases, sleep behavior):
  https://developer.android.com/reference/android/os/SystemClock
