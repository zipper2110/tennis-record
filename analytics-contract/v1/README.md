# Analytics contract v1

These synthetic fixtures define the version-one desktop-to-ingestion payload. Both the Kotlin client tests and Worker tests consume them. They contain no production identifiers, paths, media data, or user-entered values.

`valid-batches.json` contains accepted envelope examples, including each approved event type. `invalid-batches.json` contains schema-boundary examples that must be rejected without echoing submitted values.
