# Publishing Repeat Reminders on Google Play

Everything to paste into Play Console is below, in the order Play Console asks for it.

| Item | Value |
|---|---|
| App name | Repeat Reminders |
| Package | `com.sudhirshahu.loopalarm` (permanent) |
| Upload file | `app/build/outputs/bundle/release/app-release.aab` |
| Privacy policy | https://nzwqw1y2ia.execute-api.ap-south-1.amazonaws.com/privacy.html |
| Website | https://nzwqw1y2ia.execute-api.ap-south-1.amazonaws.com/ |
| Contact email | sudhirkumarshahu80@gmail.com |
| Icon | `store/icon-512.png` |
| Feature graphic | `store/feature-graphic-1024x500.png` |

## 1. Create the developer account (one time, US$25)

Google does not offer a free Play developer account.

1. Go to https://play.google.com/console/signup and sign in with the Google account that will own the app.
2. Choose **Yourself** (a personal account). An organisation account needs a D-U-N-S number.
3. Pay the US$25 registration fee by card.
4. Verify your identity with a government ID. Also verify a phone number and the contact email. Verification usually takes 1 to 3 days.
5. Personal accounts also need proof that you can access an Android device. Play Console asks you to install the Play Console app and sign in on your phone.

## 2. Create the app

In Play Console, open **Home**, then **Create app**:

- App name: `Repeat Reminders`
- Default language: English (United States), or English (India)
- App or game: **App**. Free or paid: **Free**. You cannot switch a free app to paid later.
- Tick both declarations, then click **Create app**.

## 3. Store listing (Grow users > Store presence > Main store listing)

**Short description** (80 characters max):

> Interval alarms that repeat every few minutes or hours, on the days you choose.

**Full description:**

> Repeat Reminders rings on repeat. Set a start time and an interval, and it rings every few seconds, minutes or hours until you tell it to stop. Use it for water breaks, stretching, medicine, study sessions, workouts, work shifts or anything else that repeats.
>
> QUICK ADD
> Create an alarm in four steps: name, interval, start time and days.
>
> ANY INTERVAL
> • Every 10 seconds up to every few hours, or any value in between
> • Stop at midnight, at an end time (even after midnight), or after a set number of rings
>
> FLEXIBLE DAYS
> • Every day, chosen days of the week, or days of the month
> • Pick exact dates or date ranges on a calendar
> • Limit an alarm to certain months or years
>
> RINGING YOUR WAY
> • Choose how long each alarm rings, from 5 seconds to an hour
> • Gradually rising volume
> • Six built-in beeps, your phone's ringtones, or your own music files
> • Vibration, snooze and a full-screen alarm screen, which you can turn off to get notifications only
> • Vibrate only, or skip the alarm, while you're on a phone call
> • Optionally ring through Do Not Disturb
>
> ALWAYS KNOW WHAT'S NEXT
> • The next alarm and a countdown in the notification bar, with a one-tap Skip
> • A history of every alarm: dismissed, snoozed or missed
>
> MAKE IT YOURS
> • Light, dark and pure black themes, and accent colours
> • 12-hour or 24-hour clock
>
> PRIVATE BY DESIGN
> No account, no ads and no tracking. The app has no internet access, and your alarms never leave your phone.
>
> Tip: if alarms arrive late, open Settings > Permissions in the app and grant the listed permissions. On some phones, also turn on Autostart.

- **App icon:** `store/icon-512.png`
- **Feature graphic:** `store/feature-graphic-1024x500.png`
- **Phone screenshots:** at least 2 are required (up to 8, 16:9 or 9:16, 320 to 3840 px). Install the app, add 2 or 3 sample alarms, then take screenshots of the Alarms list, Quick add, the editor, the ringing screen and Settings.
- **Category:** App category **Tools** (Productivity also fits).
- **Contact details:** email `sudhirkumarshahu80@gmail.com`. Website: the site URL above.

## 4. App content (Policy > App content)

| Section | Answer |
|---|---|
| Privacy policy | https://nzwqw1y2ia.execute-api.ap-south-1.amazonaws.com/privacy.html |
| Ads | No, my app does not contain ads |
| App access | All functionality is available without special access |
| Content rating | Start the questionnaire. Category: **All other app types**. Answer **No** to every question (violence, sexuality, language, controlled substances, user interaction, sharing location, digital purchases). The expected rating is Everyone / 3+. |
| Target audience | 13 to 15, 16 to 17 and 18+. Do **not** tick under-13 ages; that brings in the Families policy. Appeals to children: No. |
| News app | No |
| Data safety | See below |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | My app does not have any health features |
| Advertising ID | No |

### Data safety

- Does your app collect or share any of the required user data types? **No**
- Screen text: *"No data collected. No data shared."*

The app has no INTERNET permission, so nothing can leave the device. Android backup to the user's own Google account does not count as collection under Play's rules.

The optional calendar permission (birthday import) and reminder pictures are read and stored on the device only. Under Play's rules, data processed only on the device is not "collected", so the answer stays **No**.

### Permission declarations

Play Console asks about these after you upload the bundle.

**Exact alarm (`USE_EXACT_ALARM`):** choose **Alarm clock / timer** as the core functionality. Justification:

> Repeat Reminders is an alarm clock app. Its core function is ringing user-set interval alarms at exact times (for example every 15 minutes from 9:00), which requires exact alarms.

**Full-screen intent (`USE_FULL_SCREEN_INTENT`):** choose **Alarm**. Justification:

> Shows the ringing alarm screen with Snooze and Dismiss over the lock screen when a user-set alarm goes off. Users can turn this screen off per alarm.

**Foreground service (type `systemExempted`):** Justification:

> While an alarm rings, a foreground service plays the alarm sound and vibration for the duration the user chose (5 seconds to 60 minutes) and shows the Snooze/Dismiss notification. It stops as soon as the alarm is dismissed, snoozed or times out. It is started only by AlarmManager.setAlarmClock at the user's alarm time.

Play may ask for a short video link showing the feature. Record your phone screen while an alarm rings, upload it to YouTube as **Unlisted**, and paste the link.

## 5. Testing, then production

**Personal accounts created after November 2023 must run a closed test first:** at least **12 testers** opted in for **14 days in a row** before you can apply for production.

1. **Test and release > Testing > Closed testing > Create track.** Add a tester list by creating an email list of at least 12 Gmail addresses. Friends and family work.
2. **Create new release.** When asked, accept **Play App Signing**. Google keeps the app-signing key, and your file `upload-keystore/loopalarm-upload.jks` becomes the *upload key*.
3. Upload `app-release.aab`. Release notes: `First release.`
4. **Review and roll out.** Send testers the opt-in link from the track page. Each tester opens it, taps *Become a tester*, and installs the app from Play.
5. After 14 days with 12 or more testers, go to **Dashboard > Apply for production**. Answer the short questions about the test. Approval usually takes a few days.
6. **Production > Create new release**, promote the same build, and roll out. Countries: all countries, or choose them.

Review of the first version often takes 3 to 7 days.

## 6. Keep these safe

- `upload-keystore/loopalarm-upload.jks` and `keystore.properties` (holds its password). Back both up somewhere private, such as a password manager or an encrypted drive. Never commit them to git (they are git-ignored). With Play App Signing, a lost upload key can be reset through Play support, but it takes time.
- For each update, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`, then run `gradle bundleRelease` and upload the new `.aab`.
