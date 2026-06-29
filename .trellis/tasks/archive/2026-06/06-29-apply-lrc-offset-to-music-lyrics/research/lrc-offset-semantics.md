# LRC Offset Semantics

## Question

How should `[offset:<signed milliseconds>]` adjust parsed LRC lyric timestamps?

## Reference

Wikipedia's LRC file format page describes `offset` as a global offset in milliseconds, prefixed with `+` or `-`, with `+` causing lyrics to appear sooner.

## Mapping for Nordic

Nordic stores parsed lyric timestamps as `MusicLyricsLine.startMillis`, where smaller values make lines become active sooner. Therefore:

* `[offset:+500]` should convert `[00:10.00]Line` to `startMillis = 9500`.
* `[offset:-500]` should convert `[00:10.00]Line` to `startMillis = 10500`.
* Adjusted values below zero should clamp to `0`.

## Implementation Note

Apply the offset only to plain LRC timestamp rows parsed from `lyrics.value`. Do not apply it to structured lyrics.
