# Security policy

Nexus Budget handles sensitive financial data, so security reports are taken seriously.

## Reporting a vulnerability

Please **don't open a public issue**. Instead, use GitHub's [private vulnerability reporting](https://github.com/booned976/Nexus-Budget/security/advisories/new) for this repository. Include:

- a description of the issue and its impact,
- steps to reproduce or a proof of concept,
- the app version and Android version.

You'll get an acknowledgement as soon as possible, and credit in the release notes if you'd like.

## Scope

In scope: anything that could expose stored data or credentials, weaken read-only guarantees, bypass app lock, leak data over the network, or let untrusted provider/AI content execute actions without user approval.

Out of scope: vulnerabilities in third-party services (SimpleFIN, Plaid, Anthropic). Report those to the respective provider.

See [docs/PRIVACY.md](docs/PRIVACY.md) for how data is stored and protected.
