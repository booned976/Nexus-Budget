# Contributing to Nexus Budget

Thanks for helping! Nexus Budget is a community project, and contributions of all sizes are welcome: bug reports, categorization improvements, translations, docs and features.

## Ground rules

These keep the app trustworthy:

1. **No tracking.** No analytics, ads, crash reporters or telemetry SDKs.
2. **No servers.** The app talks only to data providers the user configures and, if they opt in, the AI API. Don't add a backend dependency.
3. **Read-only.** Never request payment, transfer or write access from a data provider.
4. **Open source dependencies only.** Proprietary SDKs can't be bundled (this keeps the app buildable by anyone and eligible for open-source app stores).
5. **Money is `Long` cents.** Never use `Double` for balances or amounts, only for rates.
6. **Explain recommendations.** Anything the app tells users to do should show the numbers behind it.

## Getting set up

- JDK 17 and the Android SDK (API 35). Android Studio is the easiest option.
- `./gradlew :core:test :connectors:test :assistant:test` runs the JVM test suites (no emulator needed).
- `./gradlew :app:assembleDebug` builds an installable APK.
- Tap **Explore with demo data** on first launch to get realistic data without connecting anything.

## Where things go

| You want to… | Look in |
|---|---|
| Change a calculation (budgets, payoff, forecast, recommendations) | `core/src/main/kotlin/com/nexusbudget/core/engine/`, with a test in `core/src/test` |
| Improve automatic categorization | `Categorizer.kt` keyword rules (generic words only, no brand names) or `PlaidMapping.kt` |
| Support another data provider | A new client in `connectors/` returning `SyncResult`, plus a flow in `ConnectionRepository` |
| Give the AI a new capability | A `ToolSpec` and handler in `assistant/.../FinanceTools.kt`, with a test in `AssistantTest` |
| Change a screen | `app/src/main/java/com/nexusbudget/app/ui/` |

## Pull requests

- Keep PRs focused, and describe the user-facing change.
- Add or update tests for logic changes. CI must be green.
- Follow the existing style (Kotlin official code style, Compose conventions, comments that explain *why*).
- For UI changes, include a screenshot using demo data. Never include real financial data.

## Reporting bugs

Open an issue with steps to reproduce, your Android version, and which connection type you use. **Never paste account numbers, access tokens, API keys or real transactions.**

By contributing you agree that your contributions are licensed under the GPL-3.0.
