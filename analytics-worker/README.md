# Tennis Record analytics Worker

The Worker accepts only version-one, typed analytics envelopes at `POST /v1/events/batch`. Copy `wrangler.toml.example` to the ignored `wrangler.toml` and fill binding IDs locally. Keep `ANALYTICS_INGESTION_ENABLED=false` until the manual deployment checklist and synthetic smoke exercise are complete.

Run `npm ci`, `npm run typecheck`, and `npm test` before a manual deployment. Do not commit credentials, binding IDs, event exports, `.dev.vars`, or the local Wrangler configuration.
