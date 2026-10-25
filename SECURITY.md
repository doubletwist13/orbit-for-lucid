# Security

## Reporting a vulnerability

Please report privately via GitHub's "Report a vulnerability" (Security tab) rather than a public issue.

## Principles

- Credentials stay on the user's phone. No Orbit server, no analytics.
- Store only the Lucid refresh token, never the password, encrypted with an Android Keystore key. Backups are disabled.
- The watch stores no secrets. Unlock and climate changes require an on-watch confirmation. Lock does not, so anyone
  who can use an unlocked watch can lock the car.
- Never log tokens, email addresses or VINs.
- The phone app only listens to messages from this watch app's ID, and only accepts a fixed list of commands.

## Repository hygiene

- Never commit: tokens, passwords, VINs, real API responses, signing keystores, or Garmin `developer_key` files. These
  are covered by `.gitignore`, but check before you commit.
- Fixtures must be anonymized.
- A gitleaks pre-commit hook is provided in `.githooks/` (`git config core.hooksPath .githooks`). If a secret is ever
  committed, treat it as compromised: rotate it, then clean history.
- The Garmin SDK, simulator and sample code must never be committed (see PLAN.md).
