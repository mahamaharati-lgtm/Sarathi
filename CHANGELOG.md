# Sarathi 3.0.0

This is a ground-up rebuild of Sarathi 2.1 in Kotlin and Jetpack Compose. It
installs as a separate app (`com.k.sarathi3`) next to 2.x, because 2.1's
source and signing key weren't available.

## New

- **Today timeline on the HUD.** Overdue tasks, timed tasks, calendar events,
  the running focus session and habit chips appear in one list. Tap to
  complete with undo; long-press to snooze.
- **Gita module.**
  - All 100 shlokas, by chapter.
  - Search by meaning, bookmarks and reading progress.
  - Sanskrit with IAST transliteration, and listen.
  - "Reflect" sends the shloka to the journal as a prompt.
- **Hands-free voice.** Sarathi listens again after each spoken reply until
  you say "thank you".
- **AI cost meter.** Estimated monthly spend and tokens, how much input the
  prompt cache served, and an optional monthly cap that switches to Haiku
  5.5.
- **Budget envelopes.** A budget for each category, with pace markers,
  insights and CSV export.
- **Weekly review.** Tasks, habits, focus, spending and journal for the week,
  shareable.
- **Light Day theme.** It can also be used automatically in daylight.
- **Customisable bottom bar.** Pin any two modules beside the Sarathi orb.
- **Command palette.** Jump to any screen or run a command from anywhere.
- **Undo for every delete,** plus swipe-to-delete.
- **Shared text.** Text shared into Sarathi can be asked about, saved as a
  note, added as a task, or logged as an expense when it contains an amount.
- **Home-screen widgets** (Shloka of the day, Today) and **Quick Settings
  tiles** (Ask Sarathi, Quick expense).

## Improved

- **Claude.**
  - Opus 5.5 by default, with Sonnet 5.5, Haiku 5.5 and Fable 5.1 available.
    2.1 shipped `claude-sonnet-5` and Haiku 4.5.
  - Uses API version `2026-10-21` and auto prompt caching.
  - Effort is set explicitly, and server-side refusal fallbacks are on.
  - Tool input is strictly validated.
  - Conversations are append-only, so thinking blocks and the cache stay
    valid. The current time moved from the system prompt into each message.
- **Platform.** Built against Android 16 (SDK 36) to match its target, which
  fixes the 2.1 mismatch with SDK 34. Edge-to-edge, and predictive back
  enabled.
- **Size.** The R8-shrunk release APK is about 6 MB.
- **Permissions.** Each is requested the first time its feature is used
  (notifications, calendar, microphone), not all at once.
- **Accessibility.** 48 dp touch targets, content descriptions on custom
  views, and support for system font scaling.
- **Tests.** 50 automated tests: time parsing, offline commands, money,
  streaks, the Claude streaming client, the tool loop, and screenshot
  renders of 15 screens.

## Not carried over from 2.1 (yet)

- Wake word and default-assistant service.
- Calls and SMS.
- Music control.
- Notion sync.
- UPI notification auto-logging.
- Markets watchlist.
- Translate.
- App lock.
- Notification digest.
