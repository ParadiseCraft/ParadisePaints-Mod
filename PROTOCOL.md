# ParadisePaints protocol 3

Transport: Minecraft play custom payload / Paper plugin messaging.
Channel: `paradisepaints:paint`. No Fabric-specific length prefix inside the body.
Integers are big-endian. UUIDs are two signed 64-bit words (most, least).
Each message starts with an unsigned one-byte opcode. No trailing fields are allowed.
Strings are unsigned-int16 byte length followed by strict UTF-8 bytes.

| Opcode | Direction | Body after opcode | Total bytes |
|---|---|---|---|
| 0 HELLO | Both | int32 protocol version (3) | 5 |
| 1 OPEN | Server to client | UUID session, string title, byte pigments-enabled, int32 red/green/blue stock, 16384 pixels, 256 int32 ARGB palette entries | 17440 + title bytes |
| 2 SAVE | Client to server | UUID session, string title, 16384 pixels | 16403 + title bytes |
| 3 RESULT | Server to client | UUID session, byte result (0 rejected, 1 committed, 2 insufficient pigments) | 18 |
| 4 DRAFT | Client to server | UUID session, 16384 pixels | 16401 |
| 5 GALLERY | Server to client | UUID request, int32 page/pages/total/entries, 256 int32 ARGB palette entries | 1057 |
| 6 GALLERY_ENTRY | Server to client | UUID request, int32 map, int64 created/updated, byte blocked, int64 blocked-at or -1, strings title/author/author-UUID/moderator/reason, 16384 pixels | 16440 + string bytes |

Pixels are row-major, unsigned map color indexes 0–247. Indexes 0–3 are
transparent. The editor eraser writes 0. Canvas dimensions are fixed at 128x128.
The packet ceiling is 18000 bytes. Painting titles are 1–32 Unicode code points,
contain no control characters, and occupy at most 128 UTF-8 bytes.

The server initiates HELLO after joining and the client advertises its own version
even on mismatch. OPEN requires permissions, ownership, a reachable easel containing
the original map, and an exclusive painting lock. The random session UUID is bound
to the sending player and server-selected map ID. SAVE may change the title; the
server validates both the title and pixels. DRAFT changes only pixels. When RGB
pigments are enabled for the session, OPEN includes the server-owned balances and
SAVE atomically debits the positive RGB channel differences from the last committed
canvas. Transparent pixels have zero RGB cost; erasing never refunds pigment.

GALLERY and GALLERY_ENTRY are server-initiated moderation data. There is deliberately
no client-to-server gallery opcode. The server sends them only while executing
`/pp paintings` for a player who currently has `paradisepaints.admin` and a
protocol-3 handshake. One GALLERY header is followed by zero to six matching entries.
The request UUID prevents entries from an older page being added to a newer screen.
Opening a local screen or sending arbitrary mod packets never grants gallery access
or moderation authority. Blocking and unblocking remain server commands with a
fresh permission check.

DRAFT is best-effort, at most one outstanding storage write per session; it never
sends RESULT. RESULT belongs only to final SAVE requests. SAVE is acknowledged
after durable painting persistence. Successful SAVE closes the editor. A repeated
completed SAVE with the same UUID is acknowledged without issuing another item.
Invalid state rejects saving; storage failures allow retry. The client waits 200
client ticks (normally ten seconds) before offering retry. Once SAVE is submitted,
the canvas is frozen and every retry uses the identical snapshot.

Reconnection creates a new session UUID and may restore a stored draft only if its
base painting revision still matches. Disconnect releases its lock after any
in-flight write finishes. The original map remains in the easel inventory throughout
editing and after SAVE. The server rechecks the easel, world, reach, map identity,
permissions, authorship and moderation status for DRAFT and SAVE. Retrieval requires
a separate sneak-right-click with an empty hand. MapLockPlus copy protection is
independent of the exclusive editing-session lock.

Changing this layout requires updating both implementations and a protocol-version
decision. A handshake and session token never substitute for authorization.
