# Analytics release checklist

Keep analytics disabled until all items below are verified:

- Privacy URL and notice version match the release properties.
- D1 is EU-jurisdiction with no read replicas.
- The Worker rate limit, `410` switch, retention, recovery procedure, sampled logs, and synthetic smoke test are verified.
- Packaging passes all three analytics properties together only after the backend and privacy release gate.
- Roll out in order: disabled backend, local synthetic smoke, small pre-release consent cohort, then general release.
