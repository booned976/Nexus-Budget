# Privacy and security

Nexus Budget is built so that your financial data belongs to you and stays with you.

## The short version

- **No servers, no accounts, no ads, no analytics, no tracking.** The app has no backend. It doesn't phone home.
- **Your data stays on your phone**, in the app's private storage.
- **Bank connections are read-only.** They can see balances and transactions. They can never move money.
- **Credentials are encrypted** with a key kept in your phone's secure hardware.
- **It's open source**, so anyone can check that all of the above is true.

## Where your data lives

| Data | Where | Protection |
|---|---|---|
| Accounts, transactions, budgets, goals, chat history | Local database in the app's private storage | Android app sandbox and device file-based encryption |
| SimpleFIN access links, Plaid access tokens, Plaid keys, Anthropic API key | App-private preferences | Encrypted with AES-256-GCM using a key held in the Android Keystore |
| Settings | App-private preferences | Android app sandbox |

Cloud backup and device-to-device transfer are **disabled** for all app data, so nothing is copied into a backup service. Uninstalling the app, or using **Settings → Delete all data**, removes everything.

## Network connections

Nexus Budget only makes HTTPS requests (plain HTTP is blocked by the app's network security policy), and only to:

| Destination | When | What's sent |
|---|---|---|
| Your SimpleFIN server | Syncing a SimpleFIN connection | Your encrypted-at-rest access link (used as credentials) |
| Plaid (`sandbox.plaid.com` or `production.plaid.com`) | Linking or syncing a Plaid connection | Your Plaid keys and access tokens |
| Anthropic (`api.anthropic.com`) | Only when you use Ask AI or AI categorization | Your question and the data the assistant looks up (see [AI_ASSISTANT.md](AI_ASSISTANT.md)) |

Links you open yourself (like "Get a setup token") open in your browser.

## Read-only access

- **SimpleFIN** is a read-only protocol by design. Its access links can only fetch account and transaction data.
- **Plaid**: the app only requests the `transactions`, `liabilities` and `investments` products, which are read-only. It never requests payment initiation, transfer or account-verification products.
- **The AI assistant** can read the data its tools return and propose budget or goal changes. It can't apply anything without your tap, and it can't reach your bank.

## Protecting the app on your phone

- **App lock** (Settings → Security & privacy) requires your fingerprint, face or device PIN when you open the app, and again after 30 seconds in the background. With app lock on, the app's content is hidden in the recents screen and screenshots are blocked.
- **Hide amounts** masks every dollar figure on screen. The home screen widget also hides amounts when app lock or hide amounts is on.
- Notifications use private visibility, so their content is hidden on a secure lock screen.

## Reporting a security issue

See [SECURITY.md](../SECURITY.md).
