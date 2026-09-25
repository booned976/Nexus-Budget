# Architecture

Nexus Budget is a single Android app split into four Gradle modules. Everything that can be plain Kotlin is plain Kotlin, so the finance logic, bank connectors and AI tools are unit-tested on the JVM without an emulator.

```
┌────────────────────────── app (Android) ───────────────────────────┐
│ Compose UI (Home · Accounts · Budget · Plans · Ask AI · Settings)   │
│ Repositories ── Room DB ── Keystore-encrypted secrets ── DataStore   │
│ WorkManager sync · local alerts · Glance widget · biometric lock    │
└──────┬───────────────────────┬──────────────────────────┬──────────┘
       │                       │                          │
┌──────▼──────┐        ┌───────▼────────┐        ┌────────▼─────────┐
│ connectors  │        │     core       │        │    assistant     │
│ SimpleFIN   │──uses─▶│ models, money, │◀─uses──│ Claude tools,    │
│ Plaid       │        │ finance engine │        │ chat loop, AI    │
│ (read-only) │        │ (pure Kotlin)  │        │ categorization   │
└─────────────┘        └────────────────┘        └──────────────────┘
```

## `core`: the finance engine

No Android and no network: pure functions over plain data classes. Amounts are always `Long` cents, and floating point is used only for rates.

| Component | Responsibility |
|---|---|
| `model/*` | `Account`, `Transaction`, `Category`, `BudgetTarget`, `Goal`, `NetWorthSnapshot`, `Money` formatting and parsing |
| `MerchantNormalizer` | Turns `POS DEBIT 0412 GREENLEAF GROCERY #1234 IL` into `Greenleaf Grocery` and a stable grouping key |
| `Categorizer` / `TransactionPipeline` | User rules → learned merchant history → provider hint → generic keyword rules. Links opposite amounts between the user's own accounts as transfers, credit card payments or loan payments |
| `CashFlowAnalyzer` | Monthly income, spending and essential spending, 3-month averages, savings rate |
| `BudgetEngine` | Per-category budget status with rollover, pace-based warnings (fixed bills are exempt), left-to-budget |
| `BudgetPlanner` | Starter budget from real spending, trimmed toward 50/30/20 |
| `RecurringDetector` | Groups by merchant and direction; finds weekly to yearly cadences; separates variable bills from habits; next dates, subscriptions, price changes |
| `DebtPayoffEngine` | Month-by-month simulation (avalanche, snowball, custom, minimums-only) with rollover, lump sums, feasibility detection, and a binary search for "extra needed by date" |
| `GoalEngine` | Projected completion, required monthly amount, status |
| `SafeToSpendCalculator` | Spendable cash − bills before next payday (detected series plus debt due dates, de-duplicated) − prorated goal contributions |
| `CashFlowForecaster` | 30-day projected checking balance from recurring events and typical daily spending |
| `Advisor` | Rule-based recommendations with stable ids, severity and estimated yearly impact |
| `FinancialPicture` | Computes all of the above in one pass for the UI and the AI tools |
| `CsvImporter` | RFC 4180 parsing, header detection, date formats, debit/credit columns |
| `DemoData` | A deterministic, fictional household for the demo mode and tests |

## `connectors`: read-only data providers

- **`SimpleFinClient`**: decodes and claims setup tokens (the claim URL must be HTTPS), fetches `/accounts` with Basic Auth from the access URL, and sanitizes all provider text before it reaches the UI. Account types are guessed from names (`AccountTypeGuesser`) because the protocol doesn't include them.
- **`PlaidClient`**: `/link/token/create` with Hosted Link, `/link/token/get` to collect public tokens, token exchange, `/accounts/get`, paginated `/transactions/sync` (restarting on mutation during pagination), `/liabilities/get` and `/investments/holdings/get`, and `/item/remove`. Plaid's category taxonomy is mapped onto the app's categories (`PlaidMapping`).
- Both return a provider-neutral `SyncResult`. Debts are normalized to positive amounts owed, and transactions to negative-for-outflow.

## `assistant`: the AI coach

- **`FinanceTools`**: JSON-schema tool definitions plus a local executor over an `AssistantContext` (the current `FinancialPicture`, transactions, categories and budgets). Inputs are validated. Invalid input returns an error result rather than throwing, and privacy settings can withhold transaction-level tools.
- **`FinancialAssistant`**: a streaming tool-use loop on the official Anthropic Java SDK. The conversation history is append-only, and all tool results for one turn go back in a single message. It uses automatic prompt caching, server-side refusal fallbacks on models that support them, and rolls back the turn on errors so the history stays valid.
- **`AiCategorizer`**: batches uncategorized transactions and uses structured JSON output restricted to valid category ids.
- **Proposals** (`propose_budget_changes`, `propose_goal`) are returned to the UI as data. The app applies them only when the user taps **Apply**.

## `app`: the Android client

- **UI**: Jetpack Compose with Material 3. There's one activity, Navigation Compose with five top-level tabs, and screens that read a single `StateFlow<FinanceState>`. Charts are hand-drawn on `Canvas` with a colorblind-validated palette, hairline grids, touch scrubbing and legends for multi-series charts.
- **Data**: Room tables for accounts, transactions, categories, budgets, goals, rules, net-worth snapshots, holdings, connections and chat. `FinanceRepository` combines them with settings into `FinanceState`, recomputed on a background dispatcher whenever anything changes.
- **Secrets**: `SecureStore` encrypts values with AES-256-GCM using an Android Keystore key.
- **Sync**: `ConnectionRepository` upserts accounts (respecting user-edited types and debt details), runs new transactions through the categorization pipeline, applies removals and stale-pending cleanup, and stores holdings. `SyncWorker` runs about every 8 hours whenever a network connection is available, then checks alerts and refreshes the widget.
- **Security**: `BiometricPrompt` app lock (biometric or device credential), `FLAG_SECURE` when locked, no backups, HTTPS-only network security config.

## Testing

```bash
./gradlew :core:test :connectors:test :assistant:test
```

- `core`: amortization math, avalanche vs. snowball, infeasible plans, extra-needed search, categorization and transfer linking, budget pacing and rollover, recurring detection and price changes, safe-to-spend, CSV parsing, and an end-to-end demo-household computation.
- `connectors`: SimpleFIN claim/fetch/revocation against a mock server, Plaid sync mapping (liabilities, categories, pagination, removals) and re-authentication errors.
- `assistant`: every tool returns valid JSON; input validation and privacy restrictions; proposal validation; a full streamed tool-use round trip against a mock Messages API (including the fallback header and tool result shape); error rollback.

CI (`.github/workflows/ci.yml`) runs these tests and builds both debug and minified release APKs on every push.
