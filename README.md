# Tminus

Open-source Android widgets and tools for MBTA riders. Application ID: **`com.saarlabs.tminus`**. The first feature is a **home screen trip widget** (Jetpack Glance) based on the contribution in [mbta/mobile_app#1593](https://github.com/mbta/mobile_app/pull/1593), adapted to call the public **MBTA V3 API** directly.

## API keys (optional but recommended)

The app works without keys for light use. For higher rate limits, request a free key from the V3 portal and paste it in **Settings** inside the app.

- **V3 API (schedules, stops, routes):** [MBTA Developers — V3 API](https://www.mbta.com/developers/v3-api) and [V3 API Portal](https://api-v3.mbta.com/)
- **GTFS Realtime (feeds for alerts / live data):** [GTFS Realtime — MBTA Developers](https://www.mbta.com/developers/gtfs-realtime) — a second field is stored for future widgets (notifications, alerts, etc.)

## Build locally

1. Install [Android Studio](https://developer.android.com/studio) or the Android SDK and set `ANDROID_HOME`.
2. Clone this repository.
3. From the project root:

```bash
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on your device.

## CI and installable APK

GitHub Actions builds a debug APK on each push and uploads it as a workflow artifact (`tminus-debug-apk`).

To publish an APK users can install from the **Releases** page, create a tag (for example `v0.1.0`) and push it; the [release workflow](.github/workflows/release-apk.yml) attaches `app-debug.apk` to that release.

### Create the GitHub repository (owner: saarhaber)

This environment cannot create repositories under your personal account. On your machine, with the [GitHub CLI](https://cli.github.com/) logged in as `saarhaber`:

```bash
cd /path/to/Tminus
gh repo create saarhaber/Tminus --public --source=. --remote=origin --push
```

Or create an empty repo named `Tminus` on GitHub, then:

```bash
git remote add origin https://github.com/saarhaber/Tminus.git
git push -u origin main
```

## License

Apache-2.0 (see [LICENSE](LICENSE)).
