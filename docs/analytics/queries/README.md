# Analytics SQL queries

Queries are the product reporting interface; there is no custom dashboard. Run them manually with:

`npx wrangler d1 execute <database-name> --remote --file <query-file>`

Do not export production raw events to a development machine. Opted-out users are absent and incomplete sessions may be undercounted.
