# Anytime Fitness - Who's in the Club Widget

Starter Android home screen widget project.

## Goal
Display the live "Who's in the club" list from Club OS dashboard (https://app.club-os.com/action/Dashboard/view) on your phone's home screen.

## Status
This is a starter skeleton. You will need to:
1. Open in Android Studio
2. Implement the login + HTML parsing logic in `WhosInClubWidgetProvider.kt` (look for TODO comments)
3. Store credentials securely (e.g. EncryptedSharedPreferences)
4. Build and install on your device

Club OS login is required. Scraping is used because no public real-time check-in API is available for regular accounts.

## Setup
- Min SDK 26
- Target SDK 34
- Uses OkHttp for network requests
