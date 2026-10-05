# Repeat Alarm (Android)

An interval alarm app: rings at a start time and then every N seconds, minutes or hours until a
window ends, on the days you choose. Kotlin, Jetpack Compose, Material 3, Room, AlarmManager.
Everything runs on the phone, so no server or account is needed.

## Features

| # | Requirement | Where |
|---|---|---|
| 1 | Custom ring duration (5 s to 60 min, presets or custom seconds) | Editor, *Ringing* |
| 2 | Post-alarm screen can be turned on or off per alarm (off = notification only) | Editor, *Ringing* |
| 3 | Every ring posts a notification with Snooze and Dismiss, plus an optional note afterwards | `ring/AlarmService.kt`, Settings |
| 4 | Any interval: number + seconds/minutes/hours, with presets. Ends at midnight, at a set time (can cross midnight), or after N rings | Editor, *Repeat interval* / *Start time* |
| 5 | Themes: System, Light, Dark, AMOLED black, plus 8 accent colours (including White) or wallpaper colours (Android 12+) | Settings |
| 6 | 12-hour, 24-hour or follow system | Settings |
| 7 | Gradual volume increase, with ramp time from 3 to 120 s | Editor, *Ringing* |
| 8 | 6 built-in beeps (synthesised, no audio files), phone ringtones, or any audio file on the device | Editor, *Sound* |
| 9 | Permissions screen: battery optimisation, overlay, notifications, exact alarms, full-screen, DND access. In-call behaviour: ring, vibrate only, or skip. Optional ring-through-DND | Settings, *Permissions* |
| 10 | The status-bar alarm icon (via `setAlarmClock`) and an ongoing "Next alarm" notification with countdown and *Skip this one* | `notify/Notifications.kt` |
| - | History of the last 90 days: dismissed, snoozed, timed out, in-call | History tab |
| - | Quick add in 4 steps: name, interval, start time, days | *Quick add* button |
| - | Days: every day, days of week, days of month, or picked calendar dates/ranges; filter by months and years | Editor, *Days* |
| - | Repeat or single: a single alarm rings once at the start time on each chosen day | Editor, *Repeat* |
| - | Picture per reminder, cropped in the app (free, square or wide), shown in the notification and alarm screen | Editor, *Picture*; `ui/components/CropDialog.kt` |
| - | Emoji icon per reminder, shown in the list, notification and alarm screen | Editor, *Name* |
| - | Groups: list sections with one switch for the whole group | Editor, *Name*; Alarms list |
| - | Pop-up card over other apps while the phone is in use | Settings, *Notifications*; `ring/PopupOverlay.kt` |
| - | Home-screen icon: black or coloured background, notebook page in the accent colour (activity aliases) | Settings, *App icon* |
| - | Birthday import from phone calendars or .ics / .vcf files (Google Contacts export, other apps), as yearly reminders | Cake button on the Alarms tab |

### About YouTube Music and other streaming apps

Android does not let one app play another app's streamed or downloaded songs. YouTube Music,
Spotify and similar apps keep their files private. The *Music / audio file* button opens the
system file picker, which lists songs on the phone and in apps that share their files (Files,
Downloads, SD card). To use a song from a streaming app, save it as an audio file first.

## Build

Requirements: JDK 17 and the Android SDK (platform 35). Android Studio has both, or you can use
the command-line tools.

```
gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
gradlew testDebugUnitTest      # schedule engine tests
gradlew installDebug           # install to a connected phone
```

Put your SDK path in `local.properties` (`sdk.dir=...`). This file is not committed.

For a Play Store release, create a signing key and add a `signingConfigs` block (or use
Android Studio *Build > Generate Signed Bundle*). Then run `gradlew bundleRelease`.

## How it works

- `schedule/ScheduleCalculator.kt` is pure Kotlin and unit tested. It finds the next ring for an
  alarm: the start time plus k × interval on each matching date, up to the window end. It also
  handles windows that cross midnight, ring counts, skips and snoozes.
- `schedule/AlarmScheduler.kt` keeps only one system alarm registered, the earliest ring across
  all alarms, set with `AlarmManager.setAlarmClock`. This call is exempt from Doze and shows the
  next alarm in the status bar. It re-arms after every ring, edit, reboot, time change and
  time-zone change (`receiver/Receivers.kt`).
- `ring/AlarmService.kt` is a foreground service. It plays the sound on the alarm stream, runs
  the volume ramp, vibrates, stops after the ring duration, and posts the full-screen or heads-up
  notification. It also checks whether a call is in progress, optionally lifts DND, and writes
  history.
- If two alarms fall on the same second, they ring together as one session. If a new alarm fires
  while another is ringing, the new one takes over, and the old one is logged as *Interrupted*.

## Play Store notes

- `USE_EXACT_ALARM` is only allowed for apps whose core function is an alarm clock, which this
  app is. Declare it in the Play Console permissions form.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `USE_FULL_SCREEN_INTENT` also need declarations
  (reason: alarm ringing).
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is allowed for alarm apps. It is used only from the
  Permissions screen.
