# Contributing to tMinus

Thanks for helping improve tMinus. This project welcomes contributions from the community.

## Ways to contribute

- **Report issues** — Open a [GitHub issue](https://github.com/saarhaber/Tminus/issues) for bugs, confusing UX, or ideas (new widgets, notifications, etc.). Include Android version, device or emulator, and steps to reproduce when reporting bugs.
- **Submit pull requests** — Fork the repo, create a branch from `main`, and open a PR with a clear description of what changed and why. Small, focused PRs are easier to review than large refactors.
- **Improve documentation** — README, this file, or in-code comments where behavior is non-obvious.
- **Test builds** — Install debug APKs from CI artifacts or local `./gradlew assembleDebug` and report what works or breaks on your device.

## Development setup

1. [Android Studio](https://developer.android.com/studio) (recommended) or Android SDK with `ANDROID_HOME` / `local.properties` set.
2. Clone the repository and open the project root in Android Studio.
3. Build: `./gradlew assembleDebug`
4. Run on an emulator or device (API 26+).

Optional: add an MBTA V3 API key in **Settings** in the app for higher rate limits while developing.

## Code and project expectations

- **Language:** Kotlin (Compose for screens, Glance for the home-screen widget).
- **MBTA data:** Use the public [V3 API](https://www.mbta.com/developers/v3-api) and respect [MBTA real-time display guidelines](https://www.mbta.com/developers/v3-api) and the [MassDOT Developers License Agreement](https://cdn.mbta.com/sites/default/files/2023-08/mbta-massdot-develop-license-agreement.pdf) where applicable.
- **Scope:** Keep changes aligned with the PR description; avoid unrelated refactors or drive-by formatting in files you did not need to touch.
- **License:** By contributing, you agree your contributions are licensed under the same terms as the project ([Apache-2.0](LICENSE)).

## Pull request checklist

- [ ] Builds successfully (`./gradlew assembleDebug`).
- [ ] New user-facing strings are in `res/values/strings.xml` (or appropriate qualifiers) when needed.
- [ ] Briefly describe the change and testing in the PR body.

Questions are welcome in issues or PR comments.
