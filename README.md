# AFBGKY - My Anytime Fitness

Android launcher app for quick access to Anytime Fitness Bowling Green (Club 2415) tools and pages, plus a home-screen widget for the Club OS “Who’s in the Club” list.

## Main Landing Page: My Anytime Fitness

Buttons open these pages in the device browser:

1. **Member Waiver** — https://f.cosdgw.com/dgw/Mjg5MjMxNjkz
2. **Member Online Signup** — Anytime Fitness join plans page
3. **ClubOS** — Gym management dashboard
4. **ABC Financial** — Billing management

## Who’s in the Club Widget

Home-screen widget that logs into Club OS and shows currently checked-in members.

### Setup
1. Open the app and tap **Club OS Widget Credentials**.
2. Enter your Club OS username and password (stored encrypted on device).
3. Add the **Who’s in the Club** widget to your home screen.
4. Tap **Refresh Who’s in Club Widget** in the app, or wait for the automatic update (every 30 min).

### Notes
- Credentials are stored with EncryptedSharedPreferences.
- The scraper looks for the “Who’s in the club” section on the dashboard. If Club OS changes its HTML, the parser may need a small update.
- Login field names used are the common ones (`username`/`password` and `j_username`/`j_password`). If login fails, inspect the form and adjust `ClubOsClient.kt`.

## Status

Launcher + widget implemented. Build in Android Studio and install on your device.

## Setup

- Open in Android Studio
- Min SDK 26 / Target SDK 34
- Build and install on device

App name: AFBGKY
