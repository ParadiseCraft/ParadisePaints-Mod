# ParadisePaints protocol 1

Transport: Minecraft play custom payload / Paper plugin messaging.
Channel: `paradisepaints:paint`. No Fabric-specific length prefix inside the body.
Integers are big-endian. UUIDs are two signed 64-bit words (most, least).
Each message starts with an unsigned one-byte opcode. No trailing fields are allowed.

| Opcode   | Direction        | Body after opcode                                          | Total bytes |
|----------|------------------|------------------------------------------------------------|-------------|
| 0 HELLO  | Both             | int32 protocol version (1)                                 | 5           |
| 1 OPEN   | Server to client | UUID session, 16384 pixels, 256 int32 ARGB palette entries | 17425       |
| 2 SAVE   | Client to server | UUID session, 16384 pixels                                 | 16401       |
| 3 RESULT | Server to client | UUID session, byte result (0 rejected, 1 committed)        | 18          |
| 4 DRAFT  | Client to server | UUID session, 16384 pixels                                 | 16401       |

Pixels are row-major, unsigned map color indexes 0–247. Indexes 0–3 are
transparent. The editor eraser writes 0. Canvas dimensions are fixed at 128x128.
The protocol has an 18000-byte ceiling and does not accept client map IDs,
author names, permissions, or arbitrary dimensions.

The server initiates HELLO after joining (and when an interaction finds no handshake).
The client advertises its own protocol version even on mismatch. The server rejects
incompatible versions with an installation message. OPEN requires permissions, ownership, a reachable easel containing the original map,
and an exclusive painting lock. The random session UUID is bound to the sending
player and server-selected map ID.

DRAFT is best-effort, at most one outstanding storage write per session; it never
sends RESULT. RESULT belongs only to final SAVE requests. SAVE
is acknowledged after durable painting persistence. Successful SAVE closes the
editor. A repeated completed SAVE with the same UUID is acknowledged without
issuing another item. Invalid state rejects saving; storage failures allow retry.
The client waits 200 client ticks (normally ten seconds) before offering retry.
Once SAVE is submitted, the canvas is frozen and every retry uses the identical
snapshot, so a late success cannot discard newer edits. After rejection or timeout,
the player may close without confirmation; changes beyond the last durable draft
are not guaranteed. Reopening through the normal interaction creates a new session
once any outstanding write finishes. No player command is needed to recover.

The client sends drafts every 100 client ticks. Reconnection creates a new
session UUID and may restore a stored draft only if its base painting revision
still matches. Disconnect releases its lock after any in-flight write finishes.
The original map remains in the easel inventory throughout editing and after SAVE.
The server rechecks the easel, world, reach, map identity and author permissions for
DRAFT and SAVE. Retrieval requires a separate sneak-right-click with an empty hand.
MapLockPlus copy protection is independent of the exclusive editing-session lock.

Changing this layout requires updating both implementations and a protocol-version
decision. A handshake and session token never substitute for authorization.
