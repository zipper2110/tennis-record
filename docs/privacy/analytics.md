---
layout: page
title: Optional analytics privacy notice
permalink: /privacy/analytics/
---

# Optional analytics privacy notice

Effective date: 2026-09-03. Notice version: 1.

Tennis Record sends optional product analytics only after you choose **Enable analytics**. You can withdraw consent at any time from the Privacy control in the application; collection stops immediately and unsent in-memory events are discarded.

## What we collect

We collect a temporary, per-process random session ID; event sequence number; elapsed time since consent; application version; broad operating-system family; and a small, fixed list of product events. Some events carry only closed categories such as source-video result, adjustment category, export container, encoder family, export failure category, or export duration.

We do not collect video or audio, filenames, paths, project identifiers or names, player data, scores, wall-clock event timestamps, exception messages, stack traces, device identifiers, headers, query strings, IP addresses, or free-form text. We do not sell data or use it for advertising.

## Providers and retention

GitHub Pages publishes this notice. Cloudflare Workers receives analytics requests and writes accepted events to Cloudflare D1 storage configured in the EU with no read replicas. A request may transiently be handled at a Cloudflare edge outside the EU before the Worker writes to EU D1 storage.

Workers Logs are sampled at a low rate and retained for seven days. Raw accepted analytics events are deleted after 90 days. Cloudflare D1 Time Travel may retain recoverable copies for up to 30 additional days. This page contains no tracker, script, form, or external font.

## Your choices and contact

Declining or dismissing the choice leaves all Tennis Record features available. Contact us at [leetvin@gmail.com](mailto:leetvin@gmail.com).

Read [Cloudflare's privacy policy](https://www.cloudflare.com/privacypolicy/) and [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-privacy-statement).

## Change history

- 2026-09-03 — Version 1 published for optional, consent-based product analytics.
