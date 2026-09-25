# Connecting your accounts

Nexus Budget is free, and the free ways to add accounts cover almost every bank, card issuer, brokerage and loan servicer. Automatic syncing is optional and goes through third-party services with their own costs. Nexus Budget doesn't charge for them or earn anything from them.

| | Statement file | Manual | SimpleFIN | Plaid |
|---|---|---|---|---|
| Cost | **free** | **free** | paid service, billed by SimpleFIN | your own Plaid developer account; real accounts need Plaid's approval |
| Balances | from the file | you enter them | ✅ automatic | ✅ automatic |
| Transactions | from the file | you add them | ✅ ~daily | ✅ ~daily |
| Investment holdings | ✅ from brokerage statements | — | ✅ where supported | ✅ |
| Loan APR, minimum payment, due date | enter once in the app | you enter them | enter once in the app | ✅ automatic |
| Setup effort | ~2 minutes per account | ~1 minute per account | ~5 minutes | ~15 minutes |

You can mix methods, for example statement files for your bank and brokerage plus a manual entry for a private loan.

**Why isn't automatic sync free?** Reading bank accounts automatically goes through data networks that charge for access. Nexus Budget has no servers and no income, so it can't cover that cost for you. Instead, you choose: import files for free, or pay a provider directly if you want hands-off syncing.

---

## Option 1: Statement files (free)

Almost every bank, credit card issuer, brokerage and student loan servicer lets you download your account activity from their website.

1. Sign in to the institution's website (a phone browser works, or use a computer and send the file to your phone).
2. Look for **Download**, **Export**, **Download transactions** or **Statements**. Pick the date range you want.
3. Choose a format:
   - **OFX, QFX or QBO** (sometimes labeled for money-management or accounting software) is best. These files include the account type, the last four digits, the balance and, for brokerages, your holdings.
   - **CSV** works too, but it only has transactions, so you pick the account yourself.
4. Open the downloaded file on your phone (from the browser's download notification or the Files app) and choose **Nexus Budget**. You can also use **Accounts → Add account → Import a statement file → Choose file**.
5. Check the preview and tap **Import**.

**What happens on import:**
- Each account in the file is matched to the account it was imported into before, or to a manual account with the same type and last four digits. Otherwise a new account is created. The preview shows which.
- The balance is taken from the file unless you've already imported a newer one.
- Transactions you've already imported are skipped, so importing overlapping date ranges is safe.
- Transactions are categorized automatically, and transfers between your own accounts are matched.
- For brokerages, holdings and cash come from the file. Buys and sells stay inside the account, so they don't count as spending.
- The full account number is never stored, only the last four digits.

**Keeping up to date:** download and open a new file whenever you like, for example once a week. Only new transactions are added.

**CSV tips:** Nexus Budget detects the date, description and amount columns automatically, including files with separate debit and credit columns. If purchases show as positive numbers in the preview, turn on **Flip signs**.

**Loans and cards:** statement files don't include interest rates. Open each loan or card once and enter the **APR**, **minimum payment** and **due day** so payoff plans and bill reminders are accurate.

## Option 2: Manual accounts (free)

**Accounts → Add account → Add an account manually.** Good for cash, a car or house value, a private or family loan, or any institution you'd rather not connect.

- For debts, enter the APR, minimum payment and due day so they're included in payoff plans and bill reminders.
- Add transactions with **Add transaction** on the account screen. The balance updates as you do.

---

## Automatic sync (optional)

Both services below are **read-only**: they can see balances and transactions but can never move money, pay bills or change anything at your bank. Nexus Budget syncs a few times a day while you have an internet connection, and you can pull down on **Home** or **Accounts** to sync now.

### SimpleFIN (paid service)

[SimpleFIN](https://www.simplefin.org/) is an open, read-only protocol for sharing financial data. The **SimpleFIN Bridge** connects to thousands of US banks, credit card issuers, brokerages and loan servicers, and hands your app a read-only access link.

1. In Nexus Budget, go to **Accounts → Add account**, tap **Set up** next to SimpleFIN, then **Get a setup token**. This opens the SimpleFIN Bridge.
2. Create a SimpleFIN Bridge account and connect your institutions there (checking, credit cards, your brokerage, your student loan servicer, and so on). SimpleFIN charges a subscription for this; check their site for the current price.
3. In the Bridge, create a **setup token** for Nexus Budget and copy it.
4. Paste the token into Nexus Budget and tap **Connect**.

Nexus Budget exchanges the one-time token for a private access link, encrypts it on your phone, and downloads the last 90 days of transactions.

**After connecting:**
- SimpleFIN reports names and balances but not account types, so Nexus Budget guesses the type from the name ("Savings", "Visa", "Student Loan"…). If a guess is wrong, open the account, tap the edit icon and pick the right type. Your choice is kept on every future sync.
- For **loans and credit cards**, open each one and enter the **interest rate (APR)**, **minimum payment** and **due day**. The payoff planner, safe-to-spend and recommendations use these. Until you do, the app uses conservative estimates and marks them as such.
- A setup token can be claimed **only once**. If Nexus Budget says a token was already used and you didn't use it, disable it in the SimpleFIN Bridge and create a new one.
- If access is revoked or expires, **Settings → Connections → Reconnect** accepts a new token without losing history.

### Plaid (your own developer keys)

[Plaid](https://plaid.com/) is a widely used financial data network. Nexus Budget has no servers, so it talks to Plaid with **your own** Plaid developer keys, which are stored encrypted on your phone. Linking uses Plaid's **Hosted Link** page in a browser tab, so no closed-source SDK is bundled in the app.

Plaid can fill in **loan details automatically** (student loan interest rates, minimum payments and due dates) and **investment holdings**.

#### Get keys
1. Create an account at the [Plaid Dashboard](https://dashboard.plaid.com/).
2. Open **Developers → Keys** and copy your **client ID** and the **secret** for the environment you'll use:
   - **Sandbox** uses fake institutions and test data. It's perfect for trying things out. Use the test login `user_good` / `pass_good`.
   - **Production** connects to real institutions. Plaid requires you to request production access in the dashboard, and Plaid may charge for it. Review Plaid's current pricing and terms.
3. In Nexus Budget open **Settings → Plaid API keys**, paste the client ID and secret, choose the environment, and tap **Save Plaid keys**.

#### Connect
1. **Accounts → Add account**, then tap **Set up** next to Plaid.
2. Choose what you're connecting:
   - **Bank & cards**: transactions, plus liabilities (card APRs and minimums) where available.
   - **Investments**: brokerage and retirement holdings and balances.
   - **Loans**: student loans, mortgages and other loans with rates, minimums and due dates.
3. Tap **Continue to Plaid**, sign in to your institution in the browser tab, and finish. You're brought back to the app automatically; if not, tap **I've finished**.

**Read-only by design:** Nexus Budget only ever requests Plaid's `transactions`, `liabilities` and `investments` products. It never requests payment, transfer or account-verification products.

**Reconnecting:** if your bank asks you to sign in again, the connection shows **Needs reconnecting** in Settings. Tap **Reconnect** to repair it without losing history.

**Removing:** **Settings → Connections → Remove** revokes the connection on Plaid's side and deletes the token from your phone. You can keep the history as manual accounts or delete it.

---

## Troubleshooting

| Symptom | What to do |
|---|---|
| A downloaded file doesn't offer Nexus Budget when I open it | Use **Accounts → Add account → Import a statement file → Choose file** and pick it from Downloads. |
| "This file doesn't contain any account statements" | The file isn't an OFX/QFX/QBO statement. Download again and pick OFX, QFX or QBO, or use CSV. |
| "Needs reconnecting" | Settings → Connections → Reconnect. For SimpleFIN, create a new setup token. |
| An account shows as the wrong type | Open it → edit → change **Type**. Balances flip sign automatically if needed. |
| Transactions are in the wrong category | Change the category and tick **Always use…** to create a rule. Rules are listed in Settings → Categorization rules. |
| A transfer between my accounts counts as spending | Transfers are matched automatically when both accounts are in the app. Otherwise set the category to **Transfers**. |
| Payoff plan says "estimate" | Enter the real APR and minimum payment on the loan or card. |
| Plaid says my keys were rejected | Check that the secret matches the selected environment (Sandbox and Production have different secrets). |
