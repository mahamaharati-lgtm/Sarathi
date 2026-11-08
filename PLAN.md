# Sarathi 2.2 — Plan

Plan for the next version of Sarathi, built from an analysis of the
`Sarathi-v2.1.0.apk` release. The repository has no source yet, so everything
in "Where 2.1 stands" comes from the APK itself: manifest, resource table, and
the dex class and string tables.

## Where 2.1 stands

| | |
|---|---|
| Package | `com.k.sarathi` · versionCode 8 · versionName 2.1.0 |
| SDK | minSdk 29 · targetSdk 36 · **compiled against SDK 34** |
| Stack | Kotlin + coroutines on the plain Android framework (no AndroidX, no Compose), with a hand-built "HUD" view kit |
| Size | 3.5 MB APK · one 5.9 MB dex, not minified · 1,738 app classes (ui 1,103 · domain 189 · ai 144 · device 129 · data 64 · core 47) |
| Signing | APK Signature Scheme v3 · `CN=Sarathi Debug, O=K, C=IN` · cert SHA-256 `37bc02c7…ee622c` |

**Navigation:** five tabs (HUD · Sarathi · Plan · Expenses · Systems), a drawer
with about 30 modules, and a command palette ("spent 200 on lunch",
"15% of 2000", or any screen name).

**AI:** an offline intent router handles instant commands (`ai_mode = AUTO`).
Everything else goes to the Claude Messages API with streaming, prompt caching,
and **57 tools**: alarms, timers, calendar, reminders, tasks, notes, journal,
music, habits and streak freezes, expenses and budgets, focus, calls and SMS
(both confirmed first), apps, torch, DND, volume, battery, weather, stocks,
routines, plan-my-day, weekly review, Notion sync, and remembering facts.
Memory facts are matched with vector embeddings, and conversations are kept.

**Device:**
- **Assistant:** acts as the default assistant (VoiceInteractionService and
  RecognitionService), listens for a wake word, and offers "Ask Sarathi" on
  selected or shared text.
- **Home screen:** 7 widgets (Clock, Quote, Voice, Discipline, Player, Next Up,
  Budget) and 2 Quick Settings tiles (Voice, Expense).
- **Notification listener:** controls media, logs UPI payments automatically,
  and builds a notification digest.
- **Trigger engine:** reacts to the charger, Bluetooth, and next-alarm changes.
- **Proactive nudges:** leave-time, streaks at risk, budget burn, overdue
  tasks, low battery.
- **Routine and safety:** morning/evening briefings, Notion sync, encrypted
  backup, biometric app lock, a crash reporter, and a self-test.

**Look and feel:** HUD style with Arc, Saffron, Ember, and Nebula accents,
plus Minimal Mono. Auto theme by time of day, lite mode, reduce-motion
support, a boot sequence, haptics, and synthesized UI sounds.

**Content:** 100 Gita shlokas (Sanskrit, IAST, and plain English), 150
original quotes, 13 chimes, 8 OFL fonts.

**History:** 2.0.0 (versionCode 7) also shipped an on-device LLM: llama.cpp
as an 11 MB arm64 `libllama_server.so`. 2.1 removed it and added a "Model
cleanup" screen, which is why its APK shrank from 8.1 MB to 3.5 MB. Both
releases are signed with the same key.

## Findings to fix

1. **SDK mismatch.** The app targets 36 but compiles against 34. On Android
   15+, edge-to-edge is forced and the 35/36 APIs are unavailable. Fix:
   compile against 36 and audit insets on every screen.
2. **Outdated model list.** The default is `claude-sonnet-5`, and the fast
   option is `claude-haiku-4-5-20251001`. Fix: default to Sonnet 5.5, use
   Haiku 5.5 as the fast option, keep Opus 5.5 and Fable 5.1, and migrate the
   saved setting.
3. **No reproducible build.** Fix: a Gradle build plus a GitHub Actions
   workflow that builds and signs the APK and attaches it to a GitHub release
   on every tag.
4. **Unshrunk dex.** The full Kotlin stdlib and coroutines ship in the APK.
   Fix: enable R8 with keep rules for the services, receivers, and widgets.
   This gives a smaller APK and a faster cold start.
5. **Dark themes only.** Every theme is dark, which is hard to read outdoors.
   Fix: add a light "Day" style, and let auto-theme use it.
6. **Logic without tests.** The domain logic (TimeParse, IntentParser, Money,
   Recurrence, Streaks, UpiParse) is pure, so it is easy to cover with JVM
   unit tests. Fix: add them, and run them in CI.

## 2.2 scope

### A. Foundation

- [ ] Import the 2.1 source and keep the 2.1 signing key, so 2.2 installs over
      2.1 and keeps all data.
- [ ] Gradle build (AGP 8 and Kotlin 2), compileSdk 36, R8 release build, CI
      that builds the APK and creates a release.
- [ ] Unit tests for the domain parsers and finance math. Add a self-test
      entry for each new module.
- [ ] Bring the model list up to date and migrate the saved setting.

### B. UI/UX

- [ ] **Today timeline on the HUD.** One scrollable strip that merges events,
      due tasks, reminders, habits still open, and the focus block. Tap to
      complete, long-press to snooze or reschedule.
- [ ] **Customizable bottom bar.** Pin any module as a tab, so frequent
      screens are one tap away. The command palette also shows recent and
      suggested screens.
- [ ] **Light "Day" style,** with contrast-checked tokens (WCAG AA), added to
      the auto-theme rotation.
- [ ] **Undo instead of dialogs** for deleting an expense, task, or note
      (5-second snackbar). Dialogs remain only for calls and SMS.
- [ ] **Accessibility pass:** content descriptions on the custom views (orb,
      rings, heatmap, donut), 48 dp touch targets, support for system font
      scaling, and checks that honor "Remove animations".
- [ ] **Edge-to-edge and predictive back** working correctly on Android 15/16.
- [ ] **Just-in-time permissions.** Ask for each permission when its feature
      is first used, with the existing rationale text, instead of up front.

### C. New features

- [ ] **Gita module.** A chapter browser for the 100 shlokas, a shloka of the
      day, a journal prompt for each shloka, bookmarks, and reading progress,
      plus a home-screen Gita widget.
- [ ] **Voice conversation mode.** A hands-free back-and-forth (listen, then
      reply, then listen again) that ends on silence or "thank you, Sarathi".
- [ ] **AI cost meter.** Shows tokens and estimated monthly spend from the
      `usage` field. An optional cap switches to Haiku when reached.
- [ ] **Budget envelopes.** A budget for each category, with burn-rate bars,
      a subscriptions view built from recurring items, and CSV export.
- [ ] **Smarter weekly review.** Habits, focus minutes, spend versus budget,
      and completed tasks on one shareable card.

### Later (not in 2.2)

- Web search tool for current answers.
- Extended thinking for "plan my week".
- Wear OS tile.
- Kannada and Hindi UI translations.

## Status

- **Toolchain verified** in the cloud environment: Android SDK 36,
  AGP 8.13.2, Kotlin 2.4.21, and an R8 release build all work.
- **Blocked on two inputs from the 2.1 chat:**
  - **The source code.** An APK is compiled output and can't be edited back
    into Kotlin.
  - **The signing keystore** for "Sarathi Debug". Without it, Android rejects
    2.2 as an update, so 2.1 must be uninstalled, and its data is lost unless
    it is exported first with Systems → Backup (`allowBackup` is false).

## Order of work

1. Foundation, so that a reproducible APK exists before any feature work.
2. UI/UX items, from highest impact down: Today timeline, then the bottom
   bar, then the Day style.
3. New features.
4. Release 2.2.0 (versionCode 9): an APK in GitHub Releases, with a changelog
   in the About screen.
