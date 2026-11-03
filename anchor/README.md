# Anchor — screen-time and focus app for Android

Anchor blocks *content*, not just apps, and steps in **before** a habitual open instead of after a limit is spent. This is the Phase 1 MVP from the product spec (*Screen-Time & Focus App — Product Specification*, 3 Nov 2026). "Anchor" is still the placeholder name.

## What's in this build

| Spec ID | Feature | Where |
|---|---|---|
| FR-01 | Daily and weekly usage dashboard: per-app minutes, unlocks, first pickup | `ui/home/HomeScreen.kt`, `core/usage/UsageAggregator.kt` |
| FR-02 | Per-app daily limits with a block screen | `core/policy/PolicyEngine.kt`, `guard/OverlayUi.kt` |
| FR-03 | Schedules, including overnight windows (21:00–07:00), started on time by exact alarms | `core/policy/Schedules.kt`, `guard/AlarmScheduler.kt` |
| FR-04 | Instant "block now" overlay showing the reason and time left, with a Go home action | `guard/OverlayController.kt` |
| FR-05 | Gating in-app surfaces (YouTube Shorts, Instagram Reels and Explore, Facebook Watch) with a versioned, signed rule pack | `guard/AnchorAccessibilityService.kt`, `core/surface/RulePack.kt`, `assets/rulepack.json` |
| FR-06 | Focus timer (stopwatch, countdown, Pomodoro) with a whitelist of allowed apps | `core/focus/FocusSession.kt`, `ui/home/FocusTab.kt` |
| FR-07 (partial) | Strict mode: no early exit, and Settings and the package installer are blocked during the session | `PolicyEngine.strictGuarded` |
| IN-01 (heuristic) | Habitual-open scoring from the spec's features, with the action ladder (nothing → breathing gate → intent prompt plus fuse). The model learns on the device from self-labelled opens | `core/predict/HabitScorer.kt` |
| IN-03 | Doomscroll fuse: rapid swipes in any app dim the screen step by step and start a fuse | `core/doomscroll/DoomscrollDetector.kt` |
| IN-04 | Physical anchor: an NFC tag, or a printed QR code as fallback, is the only way to end a Strict session early | `anchor/AnchorActivity.kt` |
| IN-06 | Exam-aware plans: focus targets rise and leisure tightens toward the exam date, with a 3-day taper and a lockdown on mock-test days | `core/exam/ExamPlanner.kt`, `ui/home/ExamTab.kt` |
| Journey A | Onboarding: 18+ age gate, goal, a 7-day baseline from usage history, tagging apps and surfaces, then each permission after its own disclosure screen, then the optional anchor | `ui/onboarding/OnboardingFlow.kt` |
| Journey B | Breathing gate, then "What are you opening this for?" Choosing "Message a friend" opens Instagram with Reels and Explore blocked; "Just browsing" starts a fuse | `guard/GuardService.kt`, `guard/Purposes.kt` |
| Journey C | A 50-minute Strict session that can only end early with the anchor | `ui/home/FocusTab.kt` |
| NFR-04 | Keeping the service alive on aggressive OEM ROMs: a WorkManager watchdog, gap logging, and per-OEM autostart guidance | `guard/WatchdogWorker.kt`, `data/Permissions.kt` |
| NFR-06/07 | All data stays on the device. Strict state, the anchor hash and the model are kept in EncryptedSharedPreferences. Clock and time-zone changes are logged as tamper events | `data/SecureStore.kt`, `guard/Receivers.kt` |

### Not yet built (later phases in the spec)

- **IN-01 as a LiteRT model.** It ships as an online logistic model behind the same `score()` contract.
- **IN-02** Study-safe YouTube content classifier.
- **IN-05 / FR-09** verified focus rooms.
- **IN-07** accountability partner and earned time.
- **IN-08** on-device coach.
- **FR-08** website blocking.
- Notification batching, **FR-10** shareable cards, FR-11 and FR-12.
- Device Admin uninstall barrier.
- Backend, Play Billing and localisation.

## Architecture

```
core/   Pure Kotlin (JVM), no Android dependencies. Unit-tested.
        policy engine · schedules · focus sessions · exam planner
        habit scorer · doomscroll detector · usage aggregation · rule packs
app/    Android shell
        guard/   GuardService (specialUse FGS hosting the engine)
                 AnchorAccessibilityService (observe-only; Back/Home for user rules)
                 OverlayController (block / breathing / intent / fuse / dim windows)
                 alarms, boot + tamper receivers, WorkManager watchdog
        data/    DataStore config, encrypted runtime store, UsageStats, permissions
        anchor/  NFC / QR anchor registration and unlock
        ui/      Compose: onboarding, Today, Focus, Rules, Exam, More
```

Observers (UsageStats, accessibility and screen-state receivers) feed `GuardService`. The models only produce scores. `PolicyEngine.decide()` is the single place that chooses to allow, add friction, or block. The overlay draws every ML-driven intervention. The accessibility service performs only Back and Home, and only for rules the user set (spec §8).

Deviations from the spec's stack: dependencies are wired by hand in `AppGraph` instead of Hilt, and config is JSON in DataStore instead of Room. The core API is DI-agnostic, so either can be swapped in later.

## Build

Requirements: JDK 17 or later and the Android SDK (platform 35).

```bash
cd anchor
./gradlew :core:test            # pure-Kotlin unit tests (no SDK needed: add -PcoreOnly)
./gradlew :app:assembleDebug    # APK in app/build/outputs/apk/debug/
```

CI (`.github/workflows/anchor-android.yml`) runs the core tests, builds the debug APK and lint, and uploads the APK as an artifact.

**Sideloading on Android 13 and later:** the Accessibility switch is greyed out until you open App info → ⋮ → *Allow restricted settings*. The onboarding screen explains this.

## Testing notes

- `core` has 25 unit tests. They cover overnight schedules, limits, focus and Strict precedence, fuses and cooldowns, the habit-score ladder, online learning, doomscroll detection, exam ramp, taper and mock-test days, usage splitting across midnight, the baseline, surface matching tiers, Ed25519 rule-pack verification and rollback rejection, and parsing of the bundled pack.
- The view IDs in `assets/rulepack.json` are a best-effort starting set. Check them on current YouTube, Instagram and Facebook builds, for example with `uiautomator dump`, and update the pack version. Signed remote packs go through `RulePackLoader.loadRemote`, which rejects bad signatures and older versions.
- On-device checks still to run: the overlay appears within 300 ms (NFR-01), battery drain is 2% or less per day (NFR-02), and the service survives 24 hours on the OEM matrix in NFR-04.
