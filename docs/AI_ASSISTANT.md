# The AI money coach

The **Ask AI** tab is an optional financial coach powered by [Claude](https://www.anthropic.com/claude), made by Anthropic. It answers questions about your money using your real numbers, builds plans, and can suggest budget changes or new goals that you approve with one tap.

## Setting it up

1. Create an account in the [Anthropic Console](https://console.anthropic.com/) and add credit or a payment method.
2. Create an API key under **Settings → API keys**.
3. In Nexus Budget, open **Ask AI** (or **Settings → AI assistant**) and paste the key.

The key is encrypted with your phone's secure hardware (Android Keystore) and is only ever sent to Anthropic's API. Nexus Budget has no servers of its own, so there's no middleman.

**Cost:** Anthropic charges your API account per use. A typical question costs a few cents, and more with a larger model or **Thorough** depth. You can set spending limits in the Anthropic Console.

## Things to ask

- "Make me a plan to pay off my debt faster."
- "Where did my money go this month?"
- "How big should my emergency fund be, and how long will it take?"
- "Build me a realistic budget."
- "Which subscriptions should I cut?"
- "Can I afford a $500 purchase this month?"
- "How should I handle my student loans?"
- "Am I on track for my goals?"

Every recommendation on the **Plans** screen, and the debt payoff planner, also has an **Ask AI** button that opens the conversation with the right question already asked.

## How it works

The assistant doesn't receive a dump of your data. Instead it has a set of **tools** that run **on your phone** against the app's local database, and it calls only the ones it needs to answer your question:

| Tool | What it returns |
|---|---|
| `get_financial_overview` | Net worth, cash, debt, 3-month income and spending averages, savings rate, emergency fund coverage, safe-to-spend, this month's budget totals, top recommendations |
| `list_accounts` | Accounts with type, institution, balance, and for debts the APR, minimum payment, due day and credit limit |
| `list_categories` | Category ids and names |
| `get_spending_by_category` | One month's spending per category versus budget and 3-month average |
| `get_monthly_trend` | Income, spending and net per month |
| `search_transactions` | Individual transactions matching filters (only if you allow transaction details) |
| `get_budget_status` | This month's budget by category with pace and status |
| `get_recurring_charges` | Detected bills, subscriptions and paychecks |
| `get_goals` | Goals with progress and projections |
| `simulate_debt_payoff` | Avalanche or snowball simulation with an extra monthly amount |
| `debt_extra_needed` | Extra per month needed to be debt-free by a target |
| `project_goal` | What-if savings projection |
| `get_cash_flow_forecast` | Projected checking balance and upcoming bills |
| `propose_budget_changes` / `propose_goal` | Suggestions you can **Apply** or dismiss. Nothing changes without your tap. |

The debt payoff, goal and forecast numbers come from the same tested finance engine the rest of the app uses, so the AI explains and plans around real calculations instead of doing arithmetic from memory.

**AI categorization:** On **Budget → Transactions**, **Categorize with AI** sends the merchant text and amounts of transactions the local rules couldn't categorize, and applies the results. Only uncategorized transactions are sent.

## Privacy controls

In **Settings → AI assistant**:

- **Model**: Claude Opus 5 (recommended), Claude Fable 5.1 (most capable, costs more), Claude Sonnet 5 (faster and cheaper) or Claude Haiku 4.5 (fastest and cheapest).
- **Response depth**: Quick, Balanced or Thorough. Deeper answers take longer and cost more.
- **Share transaction details**: when off, the assistant can only see totals by category. It can't search individual transactions or see merchant names.

What is sent to Anthropic: your questions, the assistant's replies, and the results of the tools it calls during your conversation. Account numbers are never stored by the app and never sent. Anthropic's handling of API data is covered by its [privacy policy](https://www.anthropic.com/legal/privacy) and commercial terms.

## Limits

- The assistant is an educational tool, **not a licensed financial, tax or legal advisor**. For big or irreversible decisions (bankruptcy, taxes, refinancing, large investments) check with a qualified professional.
- AI can make mistakes. The numbers shown on the app's own screens are the source of truth.
- The assistant can't move money, pay bills or contact your bank. Connections are read-only.
