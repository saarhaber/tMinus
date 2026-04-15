# tMinus

<img src="app/src/main/res/drawable-nodpi/ic_brand_logo.png" alt="tMinus logo" width="160" />

Open-source Android widgets and tools for MBTA riders. Application ID: **`com.saarlabs.tminus`**. The first feature is a **home screen trip widget** (Jetpack Glance) based on the contribution in [mbta/mobile_app#1593](https://github.com/mbta/mobile_app/pull/1593), adapted to call the public **MBTA V3 API** directly.

The in-app UI uses bottom navigation: **Home** and **Settings** (API keys, time format, documentation links, and community / contribution links).

## Screenshots

Home screen **widgets** show trip countdowns or departures at a stop; the **app** covers commutes, first/last train alerts, and station accessibility watches.

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/images/readme-widget-trip.png" width="280" alt="MBTA trip home screen widget showing line, route, countdown, and times" />
      <br />
      <sub><strong>Trip widget</strong> — line, next trip, and schedule at a glance</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/images/readme-widget-station-board.png" width="280" alt="Station board widget showing scheduled departures from a stop" />
      <br />
      <sub><strong>Station board widget</strong> — upcoming departures for a stop</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/readme-home.png" width="280" alt="tMinus Home screen with commutes, train alerts, and elevator alerts" />
      <br />
      <sub><strong>Home</strong> — entry points for commutes and alerts</sub>
    </td>
    <td align="center">
      <img src="docs/images/readme-choose-stops.png" width="280" alt="Choose stops screen with from and to fields" />
      <br />
      <sub><strong>Choose stops</strong> — pick origin and destination for the widget</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/readme-commutes-list.png" width="280" alt="Commutes list with one commute card" />
      <br />
      <sub><strong>Commutes</strong> — reorder, edit, or add scheduled routes</sub>
    </td>
    <td align="center">
      <img src="docs/images/readme-commute-editor.png" width="280" alt="Commute editor with days, target time, and notification options" />
      <br />
      <sub><strong>Commute editor</strong> — target time, window, and notifications</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/readme-last-first-list.png" width="280" alt="Last and first train alerts list" />
      <br />
      <sub><strong>Last / first train</strong> — manage schedule-based train alerts</sub>
    </td>
    <td align="center">
      <img src="docs/images/readme-train-time-alert.png" width="280" alt="Train time alert editor with route, direction, and window" />
      <br />
      <sub><strong>Train time alert</strong> — route, direction, window, and lead time</sub>
    </td>
  </tr>
</table>

**Commutes** (from Home): save **multiple** named routes (from/to stops), **days of week**, a **target time**, and a **window** (minutes before/after) used to query schedules. Set **notify X minutes before departure** for a “time to leave” notification, and optionally a second ping around **scheduled arrival**. Checks run on a background schedule (about every 15 minutes) using **schedule data** from the MBTA V3 API—not live predictions. Grant **notification permission** on Android 13+ when prompted.

**Last / first train** alerts: pick **route id**, **direction id** (0 or 1), **stop**, **last vs first** mode, optional **time windows** (as minutes-from-midnight), and **notify N minutes before** that scheduled departure. Uses the latest/earliest departure in the window from the schedule API.

**Elevator & station alerts**: watch a **route** + **station**; the app pulls **active alerts** for that route and notifies when an alert’s text plausibly matches your station (elevator/escalator/stop-closure effects). This is **heuristic**—not a guarantee every outage is detected.

## API keys (optional but recommended)

The app works without keys for light use. For higher rate limits, request a free key from the V3 portal and paste it in **Settings** inside the app.

- **V3 API (schedules, stops, routes):** [MBTA Developers — V3 API](https://www.mbta.com/developers/v3-api) and [V3 API Portal](https://api-v3.mbta.com/) — paste your key in **Settings** in the app.

## Build locally

1. Install [Android Studio](https://developer.android.com/studio) or the Android SDK and set `ANDROID_HOME`.
2. Clone this repository.
3. From the project root:

```bash
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on your device.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to report issues, submit pull requests, and set up a dev environment.

## CI and installable APK

GitHub Actions builds a debug APK on each push and uploads it as a workflow artifact (`tminus-debug-apk`).

**Rolling build from `main`:** each merge to `main` updates the prerelease [**Latest main (debug)**](https://github.com/saarhaber/Tminus/releases/tag/latest-main) on the Releases page with a fresh `app-debug.apk` (tag `latest-main`). The release title and description include a **Built (UTC)** time so you can tell when the APK last changed—GitHub’s own “published” date for that rolling entry can stay stale.

**Versioned release:** create and push a tag such as `v0.1.0`; the [tag release workflow](.github/workflows/release-apk.yml) attaches the debug APK to that numbered release.

## License

Apache-2.0 (see [LICENSE](LICENSE)).
