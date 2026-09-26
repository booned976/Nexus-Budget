# Nexus Budget

**A free, open-source budgeting app for Android.** See all your accounts in one place, get a plan to pay off debt and reach your goals, and ask an AI money coach questions about your real numbers.

[![CI](https://github.com/booned976/Nexus-Budget/actions/workflows/ci.yml/badge.svg)](https://github.com/booned976/Nexus-Budget/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

- **Free. No subscriptions, no ads, no sign-up.** Everything works without paying anyone.
- **Your data stays on your phone.** There are no company servers and no tracking. Bank access is read-only and can never move money.
- **Open source (GPL-3.0).** Anyone can read, build, change and share it.

## Install

<a href="https://github.com/booned976/Nexus-Budget/releases/latest/download/NexusBudget.apk"><img src="docs/images/download-qr.svg" alt="QR code linking to the APK download" width="140" align="right"></a>

1. On your Android phone, tap **[Download Nexus Budget](https://github.com/booned976/Nexus-Budget/releases/latest/download/NexusBudget.apk)**, or scan the QR code with your phone's camera.
2. Open the downloaded file. If Android asks, allow your browser or Files app to install apps, then tap **Install**.
3. Open Nexus Budget and tap **Explore with demo data** to look around, or **Set up my accounts**.

Android 8.0 or newer. To update later, go to **Settings → Check for updates** in the app, or download again. Updates install over the old version and keep your data.

---

## What it does

### Everything at a glance (Home)
- **Safe to spend:** your checking balance minus bills due before your next paycheck and money set aside for goals, shown as a daily allowance.
- **Net worth** with a 30-day change and trend line.
- Totals for **cash, credit cards, investments and loans**.
- This month's **budget progress**, **upcoming bills**, **goal progress**, the **top recommendations** and **recent activity**.
- Pull down to sync. Tap the eye icon to hide every amount (privacy mode).
- A **home screen widget** shows safe-to-spend and net worth.

### Accounts
- Bring in **checking, savings, credit cards, brokerage and retirement accounts, and student, auto and other loans**.
- **Free statement import:** download an OFX, QFX, QBO or CSV file from your bank's website and open it with Nexus Budget. Statement files create the account, set the balance and add the transactions by themselves, and re-importing a newer file only adds what's new.
- Track anything else **manually** (cash, a car, a private loan).
- Optional **automatic sync** through third-party services (SimpleFIN or Plaid), always read-only.
- Debt details: APR, minimum payment, due date, credit utilization, and how long payoff takes at the minimum payment.
- Investment **holdings** from brokerage statement files or connected accounts.
- Net worth history chart. Touch and drag it to see exact values.

### Budget
- **Monthly category budgets** with pace tracking. You're warned when you're spending faster than the month is passing.
- **Insights** explain the numbers: where expected income comes from, how your budget splits between needs, wants and savings (50/30/20), whether flexible spending is on pace, and why each category's budget is set where it is.
- **Left to budget**: your expected income minus what's assigned, so every dollar has a job.
- **Rollover**: carry unspent money (or overspending) into next month, per category.
- **Build my budget in one tap:** budgets are generated from your real spending, then flexible categories are trimmed so at least 20% of income goes to savings and debt payoff.
- **Automatic categorization:** your own rules come first, then what you've taught it, then category data from your bank. Re-categorize a transaction once and choose "always" to create a rule. Optionally, **AI categorizes** whatever is left.
- **Recurring:** bills, subscriptions and paychecks are detected automatically, with next due dates, yearly cost and **price-increase alerts**.
- **Trends:** income vs. spending by month, savings rate, and where the money went.

### Plans
- **Recommendations** that explain themselves with your numbers, each with a button that takes you straight to the fix (the payoff plan, a prefilled goal, a category's budget). For example: spending more than you earn, a checking balance projected to go negative, emergency fund coverage, high-interest debt, credit utilization, categories over budget or unusually high, subscription totals, price increases, goals falling behind, student loan strategy, idle cash and savings rate.
- **Debt payoff planner:** avalanche (highest rate first) or snowball (smallest balance first), an extra-payment slider, your debt-free date, interest and time saved compared with paying only minimums, the payoff order, and exactly **what to pay each debt this month**.
- **Goals** such as an emergency fund, a trip or a down payment, with a projected completion date and the monthly amount needed to hit a deadline. A goal can be linked to a savings account so its progress updates automatically.
- **30-day cash flow forecast:** your projected checking balance, day by day, from upcoming bills, paychecks and typical everyday spending.

### Ask AI (optional)
- An AI money coach powered by **Claude** (Anthropic), using **your own API key**.
- It looks up your real numbers with on-device tools (overview, accounts, spending, budget, recurring charges, goals, payoff simulations, cash forecast) instead of guessing.
- It can **propose budget changes or new goals**, and nothing changes until you tap **Apply**.
- **Ask AI** buttons on recommendations and the payoff planner start a conversation with the right question.
- Privacy controls: choose the model and response depth, or limit the assistant to category totals only.

### Privacy and security
- Credentials (bank access tokens, API keys) are encrypted with a key held in the **Android Keystore**.
- **App lock** with fingerprint, face or device PIN. When it's on, the app is also hidden from the recents screen.
- Cloud backup and device transfer are disabled for app data. Network traffic is HTTPS only.
- Local notifications for over-budget categories, bills due tomorrow, low-balance forecasts and connections that need attention.

---

## Getting started

### Add your accounts
| Method | Cost | How |
|---|---|---|
| **Statement file** | Free | On your bank, card, brokerage or loan website, find **Download**, **Export** or **Statements** and pick OFX, QFX or QBO (or CSV). Open the file on your phone and choose Nexus Budget. |
| **Manual** | Free | Enter a balance yourself. Good for cash, a car or a private loan. |
| **Automatic sync** (optional) | Set by the provider | SimpleFIN (a paid service) or Plaid (your own developer keys; real accounts need Plaid's approval). |

Nexus Budget never charges for anything and earns nothing from these services. Step-by-step instructions: **[docs/CONNECTING_ACCOUNTS.md](docs/CONNECTING_ACCOUNTS.md)**.

### AI coach (optional)
Everything else works without it. To turn it on, create an API key in the [Anthropic Console](https://console.anthropic.com/settings/keys) and paste it into **Ask AI**. There's no subscription: Anthropic bills only for the questions you ask, and you can set a spending limit. Details and exactly what is shared: **[docs/AI_ASSISTANT.md](docs/AI_ASSISTANT.md)**.

---

## Build from source

Requirements: JDK 17 and the Android SDK (API 35). Android Studio works out of the box.

```bash
git clone https://github.com/booned976/Nexus-Budget.git
cd Nexus-Budget
./gradlew :core:test :connectors:test :assistant:test   # finance engine, connectors, AI tools
./gradlew :app:assembleDebug                             # app/build/outputs/apk/debug/
```

Every merge into `main` publishes a new APK on the Releases page automatically. See **[docs/RELEASING.md](docs/RELEASING.md)**, including the one-time signing key setup that lets updates install over earlier versions.

## Project layout

| Module | What's inside |
|---|---|
| `core` | Pure Kotlin finance engine: budgets, recurring detection, debt payoff, goals, forecasts, recommendations, statement (OFX/QFX/QBO) and CSV import, demo data |
| `connectors` | Read-only SimpleFIN and Plaid clients |
| `assistant` | Claude-powered coach: tools over local data, conversation loop, AI categorization |
| `app` | Android app (Jetpack Compose, Room, WorkManager, Glance widget) |

More in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**, **[docs/PRIVACY.md](docs/PRIVACY.md)** and **[CONTRIBUTING.md](CONTRIBUTING.md)**.

## License

Nexus Budget is free software, licensed under the [GNU General Public License v3.0](LICENSE). You can use, study, share and modify it, and anything you distribute that's built from it must stay open source too.

> Nexus Budget provides educational information and tools, not professional financial, tax or legal advice. Check important decisions with a qualified professional.
