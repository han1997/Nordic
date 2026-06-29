# OpenSubsonic Structured Lyrics Timing

## Question

How should Nordic interpret `structuredLyrics.line.start` and `structuredLyrics.offset` from `getLyricsBySongId`?

## Reference

OpenSubsonic documentation for `lyricsList` and `structuredLyrics` shows `line.start` examples such as `2000`, `3001`, `2747`, and `6214`, all in millisecond scale. The `structuredLyrics` field table documents:

* `offset`: number, optional, OpenSubsonic field.
* Details: "The offset to apply to all lyrics, in milliseconds. Positive means lyrics appear sooner, negative means later. If not included, the offset must be assumed to be 0."

## Mapping for Nordic

* Preserve `line.start` as milliseconds before offset adjustment.
* Apply `structuredLyrics.offset` by subtracting the signed offset from each parsed line timestamp.
* Clamp adjusted timestamps below zero to `0`.
* Missing offset is `0`.

## Implementation Note

Nordic currently maps `NavidromeStructuredLyrics.line.start` but does not declare or apply `offset`. Add the DTO field and apply it in `NavidromeStructuredLyrics.toMusicLyrics(...)`.
