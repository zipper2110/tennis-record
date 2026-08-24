# UI smoke-test media

`ui-smoke.mp4` is the immutable video fixture shared by the automated UI flows.

## Provenance and permission

- Source: project-owner-provided file `D:\Timeline 1.mp4`.
- Copyright owner: not separately identified by the contributor.
- Redistribution permission: the project owner supplied this exact file and explicitly approved its inclusion and redistribution as part of this repository on 2026-08-25.
- License: no separate external license was asserted for the fixture; repository inclusion is based on the project owner's explicit authorization above.
- The checked-in file is an exact byte-for-byte copy of the approved source.

## Integrity and media metadata

- SHA-256: `87feface1dedc57e4c65d6a77afa17e7208802baf59392713022d5fb41c03db9`
- Byte size: `1,415,036`
- Container reported by FFprobe: `mov,mp4,m4a,3gp,3g2,mj2` (MP4-family container)
- Duration: `4.480000` seconds
- Video stream: H.264 (`h264`), 720 x 486
- Audio stream: AAC (`aac`) present
- Additional stream: one data stream present

The fixture contract permits an MP4 file from 3 through 15 seconds and no more than 5 MiB. Ordinary fast tests verify existence, extension, size, and SHA-256 without requiring FFprobe. The UI-flow runner performs codec, container, and duration checks.
