# Connecting your accounts

Nexus Budget offers four ways to bring in accounts. The two automatic options are **read-only**: they can see balances and transactions but can never move money, pay bills or change anything at your bank. You can mix methods, for example SimpleFIN for your bank and brokerage plus a manual entry for a private loan.

| | SimpleFIN | Plaid | Manual | CSV import |
|---|---|---|---|---|
| Balances | ✅ automatic | ✅ automatic | you enter them | you enter them |
| Transactions | ✅ ~daily | ✅ ~daily | you add them | from a file |
| Loan APR, minimum payment, due date | enter once in the app | ✅ automatic | you enter them | you enter them |
| Investment holdings | ✅ where supported | ✅ | — | — |
| Cost | SimpleFIN subscription | your Plaid account | free | free |
| Setup effort | ~5 minutes | ~15 minutes | ~1 minute per account | ~2 minutes |

Nexus Budget syncs automatically a few times a day while you have an internet connection. You can also pull down on **Home** or **Accounts** to sync now.

---

## Option 1: SimpleFIN (recommended)

[SimpleFIN](https://www.simplefin.org/) is an open, read-only protocol for sharing financial data. The **SimpleFIN Bridge** connects to thousands of US banks, credit card issuers, brokerages and loan servicers, and hands your app a read-only access link.

1. In Nexus Budget, go to **Accounts → Add account → Connect with SimpleFIN** and tap **Get a setup token**. This opens the SimpleFIN Bridge.
2. Create a SimpleFIN Bridge account and connect your institutions there (checking, credit cards, your brokerage, your student loan servicer, and so on). SimpleFIN charges a small subscription for this service.
3. In the Bridge, create a **setup token** for Nexus Budget and copy it.
4. Paste the token into Nexus Budget and tap **Connect**.

Nexus Budget exchanges the one-time token for a private access link, encrypts it on your phone, and downloads the last 90 days of transactions.

**After connecting:**
- SimpleFIN reports names and balances but not account types, so Nexus Budget guesses the type from the name ("Savings", "Visa", "Student Loan"…). If a guess is wrong, open the account, tap the edit icon and pick the right type. Your choice is kept on every future sync.
- For **loans and credit cards**, open each one and enter the **interest rate (APR)**, **minimum payment** and **due day**. The payoff planner, safe-to-spend and recommendations use these. Until you do, the app uses conservative estimates and marks them as such.
- A setup token can be claimed **only once**. If Nexus Budget says a token was already used and you didn't use it, disable it in the SimpleFIN Bridge and create a new one.
- If access is revoked or expires, **Settings → Connections → Reconnect** accepts a new token without losing history.

---

## Option 2: Plaid (bring your own keys)

[Plaid](https://plaid.com/) is a widely used financial data network. Nexus Budget has no servers, so it talks to Plaid with **your own** Plaid developer keys, which are stored encrypted on your phone. Linking uses Plaid's **Hosted Link** page in a browser tab, so no closed-source SDK is bundled in the app.

Plaid is the best option when you want **loan details filled in automatically** (student loan interest rates, minimum payments and due dates) and **investment holdings**.

### Get keys
1. Create an account at the [Plaid Dashboard](https://dashboard.plaid.com/).
2. Open **Developers → Keys** and copy your **client ID** and the **secret** for the environment you'll use:
   - **Sandbox** uses fake institutions and test data. It's perfect for trying things out. Use the test login `user_good` / `pass_good`.
   - **Production** connects to real institutions. Plaid requires you to request production access in the dashboard. Review Plaid's current pricing and terms for your usage.
3. In Nexus Budget open **Settings → Plaid API keys**, paste the client ID and secret, choose the environment, and tap **Save Plaid keys**.

### Connect
1. **Accounts → Add account → Connect with Plaid.**
2. Choose what you're connecting:
   - **Bank & cards**: transactions, plus liabilities (card APRs and minimums) where available.
   - **Investments**: brokerage and retirement holdings and balances.
   - **Loans**: student loans, mortgages and other loans with rates, minimums and due dates.
3. Tap **Continue to Plaid**, sign in to your institution in the browser tab, and finish. You're brought back to the app automatically; if not, tap **I've finished**.

**Read-only by design:** Nexus Budget only ever requests Plaid's `transactions`, `liabilities` and `investments` products. It never requests payment, transfer or account-verification products.

**Reconnecting:** if your bank asks you to sign in again, the connection shows **Needs reconnecting** in Settings. Tap **Reconnect** to repair it without losing history.

**Removing:** **Settings → Connections → Remove** revokes the connection on Plaid's side and deletes the token from your phone. You can keep the history as manual accounts or delete it.

---

## Option 3: Manual accounts

**Accounts → Add account → Add an account manually.** Good for cash, a car or house value, a private or family loan, or any institution you'd rather not connect.

- For debts, enter the APR, minimum payment and due day so they're included in payoff plans and bill reminders.
- Add transactions with **Add transaction** on the account screen. The balance updates as you do.

## Option 4: CSV import

Most banks let you download transactions from their website as a CSV file.

1. Download the CSV from your bank's website.
2. **Accounts → Add account → Import a CSV file** (or **Import CSV** on a manual account).
3. Choose the account and the file. Nexus Budget detects the date, description and amount columns automatically, including files with separate debit and credit columns.
4. Check the preview. If purchases show as positive numbers, turn on **Flip signs**.
5. Tap **Import**. Transactions you've already imported are skipped, so re-importing an overlapping file is safe.

---

## Troubleshooting

| Symptom | What to do |
|---|---|
| "Needs reconnecting" | Settings → Connections → Reconnect. For SimpleFIN, create a new setup token. |
| An account shows as the wrong type | Open it → edit → change **Type**. Balances flip sign automatically if needed. |
| Transactions are in the wrong category | Change the category and tick **Always use…** to create a rule. Rules are listed in Settings → Categorization rules. |
| A transfer between my accounts counts as spending | Transfers are matched automatically when both accounts are connected. Otherwise set the category to **Transfers**. |
| Payoff plan says "estimate" | Enter the real APR and minimum payment on the loan or card. |
| Plaid says my keys were rejected | Check that the secret matches the selected environment (Sandbox and Production have different secrets). |
