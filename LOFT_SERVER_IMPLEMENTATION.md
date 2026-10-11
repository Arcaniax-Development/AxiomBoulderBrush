# Loft Wireframe Preview Server Implementation

This document describes the server-side work needed to support the Arceon x Axiom client Loft preview while preserving the existing server particle preview for players without the client mod.

## Goal

Keep the existing Loft workflow unchanged for normal players:

- No client mod: use the current server particle preview and existing `/loft` behavior.
- Client mod present: send the authoritative ordered frames to the client for a read-only rendered line preview.

The server stays authoritative. The current client does not move, add, remove, or commit Loft points.

## Current Scope: Preview Only

- The client renders server-provided curves and control points.
- The client does not expose editing controls.
- The client does not send `arceon:loft_commit`.
- `/loft set` must run the existing authoritative server placement immediately.
- Do not wait for client confirmation before placing.
- Client editing packets remain reserved for a future protocol revision.

## Protocol

Protocol version: `1`

All packets are custom payloads using namespace `arceon`.

Strings are encoded as:

```text
length:int
utf8Bytes[length]
```

UUIDs are encoded as:

```text
mostSignificantBits:long
leastSignificantBits:long
```

Loft frame data is encoded as an indexed flat point list:

```text
pointCount:int
repeat pointCount:
  pointId:int
  frameIndex:int
  pointIndex:int
  x:double
  y:double
  z:double
```

Important: `frameIndex` and `pointIndex` are part of the contract so the client can reconstruct the server's ordered frames.

## Packets

### `arceon:loft_hello`

Direction: client to server

Purpose: tells the server this player supports the rendered client Loft preview.

```text
protocolVersion:int
clientModVersion:string
```

Server behavior:

- If `protocolVersion == 1`, mark this player as `supportsLoftWireframe=true`.
- Store `clientModVersion` for debugging only.
- Clear the flag on disconnect.

### `arceon:loft_session_open`

Direction: server to client

Purpose: opens or updates the read-only client-side Loft preview.

```text
protocolVersion:int
sessionId:uuid
revision:int
world:string
lowPoly:boolean
points:indexed-flat-point-list
```

Server behavior:

- Send only to players with `supportsLoftWireframe=true`.
- `world` should identify the world/dimension for validation.
- `lowPoly=true` means the client previews straight-line interpolation.
- `lowPoly=false` means the client previews smooth curves.
- `revision` should increment if the same session is updated.

### `arceon:loft_commit`

Status: reserved for future editing; the current client does not register or send it.

The server must not require this packet for `/loft set` placement.

```text
protocolVersion:int
sessionId:uuid
revision:int
points:indexed-flat-point-list
```

Server behavior:

- Future implementations must validate everything before accepting edited frames.
- The current preview-only implementation must ignore this path for placement.

### `arceon:loft_cancel`

Direction: client to server

Purpose: clears an active preview when the client changes world or disconnects from the current level.

```text
protocolVersion:int
sessionId:uuid
```

Server behavior:

- If the session exists for that player, clear it.
- Do not place blocks.
- Optionally send `loft_result` with `accepted=true`, `code="cancelled"`.

### `arceon:loft_result`

Direction: server to client

Purpose: clears the preview or reports a server-side preview/placement result.

```text
protocolVersion:int
sessionId:uuid
accepted:boolean
code:string
message:string
```

Recommended codes:

```text
accepted
cancelled
timeout
bad_protocol
bad_session
bad_revision
bad_world
no_permission
too_many_frames
too_many_points
not_enough_frames
invalid_coordinates
placement_failed
```

## Server State

Track this per player:

```java
class PlayerLoftClientState {
    boolean supportsLoftWireframe;
    String clientModVersion;
    LoftWireframeSession activeSession;
}
```

Track active sessions:

```java
class LoftWireframeSession {
    UUID sessionId;
    int revision;
    String world;
    boolean lowPoly;
    long openedAtMillis;
    List<List<Vec3>> originalFrames;
}
```

Clear both support/session state on disconnect.

Clear active session on:

- authoritative placement completed
- cancel
- timeout
- player changes world
- player loses permission
- server-side Loft selection is manually cleared

## Preview Mode Selection

Start the client preview as soon as the player's Loft selection gets its first point. Do not wait for `/loft set`.

On the first Loft point:

```java
if (playerState.supportsLoftWireframe) {
    openClientLoftSession(player, currentLoftSelection);
} else {
    runExistingParticlePreview(player);
}
```

This preserves the current particle workflow for:

- vanilla players
- players without Arceon x Axiom
- players whose client does not send `arceon:loft_hello`
- players on an unsupported protocol

## Live Selection Synchronization

The server must send the current full Loft selection after every selection edit:

- right-click adds a point to the current frame
- left-click starts a frame and adds its first point
- `/loft remove`
- `/loft remove -c`
- any other server-side point/frame mutation

Normal selection edits must not clear the active wireframe session. They update that session in place. Only clear it when the selection becomes empty, the player explicitly cancels, changes world, disconnects, times out, or completes placement.

For the first point, create the session and send `arceon:loft_session_open`.

For later edits:

1. Keep the same `sessionId` for the lifetime of that selection.
2. Increment `revision`.
3. Send `arceon:loft_session_open` again with the complete updated selection.
4. Keep existing point IDs stable so the client can preserve its selected handle.

The client treats repeated `loft_session_open` packets with the same session ID as live snapshot updates. A separate update packet is not required.

```java
void onLoftSelectionChanged(Player player) {
    PlayerLoftClientState state = getClientState(player);
    if (!state.supportsLoftWireframe) {
        renderExistingParticlePreview(player);
        return;
    }

    LoftWireframeSession session = state.activeSession;
    if (session == null) {
        session = createSession(player);
        state.activeSession = session;
    } else {
        session.revision++;
    }

    session.originalFrames = copyCurrentLoftFrames(player);
    sendLoftSessionOpen(player, session);
}
```

If the selection becomes empty, clear the session and send an accepted `loft_result` with code `selection_cleared` so the client removes its preview.

`/loft set` should use the server's current authoritative selection and place immediately through the existing Loft placement path. It must not wait for `loft_commit`. After placement, clear the preview with `loft_result` using `accepted=true` and code `accepted` or `placed`.

### Expected Live Log Sequence

The server log should begin while points are being selected, before `/loft set`:

```text
selection changed ... frames=1 points=1
session created ... revision=1
session payload sent ... channel=arceon:loft_session_open

selection changed ... frames=1 points=2
session updated ... revision=2
session payload sent ... channel=arceon:loft_session_open

selection changed ... frames=2 points=3
session updated ... revision=3
session payload sent ... channel=arceon:loft_session_open
```

When `/loft set stone` is finally run, the expected behavior is:

```text
placement requested ... existingSession=<same UUID> revision=<current revision> material=stone
```

There should not be a new `session created` entry at `/loft set` if a live selection session already exists.

If logs instead show selection edits clearing the session and `/loft set` creating it, the implementation is still using the old non-live lifecycle.

## Opening A Client Session

Immediately when the player has their first Loft point:

1. Create `sessionId = UUID.randomUUID()`.
2. Set `revision = 1`.
3. Copy the current ordered Loft selection frames.
4. Assign stable `pointId` values.
5. Store the session in player state.
6. Send `arceon:loft_session_open`.

Point IDs only need to be stable within the session. New client-inserted points may come back with negative IDs. The server should accept negative IDs as client-created temporary IDs, then rebuild its own authoritative IDs if needed.

## Future Editing Protocol (Inactive)

The following commit design is retained only for future client editing. It is not part of the current preview-only implementation. Servers must not wait for this packet when handling `/loft set`.

If editing is enabled in a future protocol, validate `arceon:loft_commit` as follows:

1. Check `protocolVersion == 1`.
2. Check player has an active session.
3. Check `sessionId` matches the active session.
4. Check `revision` matches the active session revision.
5. Check player is still in the same world/dimension.
6. Check permission `arceon.axiom.loft`.
7. Decode and rebuild ordered frames.
8. Reject if fewer than 2 non-empty frames.
9. Enforce frame count limit.
10. Enforce points-per-frame limit.
11. Enforce total point count safety limit.
12. Reject NaN/infinite coordinates.
13. Optionally reject points too far from player or outside allowed build bounds.
14. Replace/update server-side Loft selection with edited frames.
15. Run existing server-side Loft placement/generation.
16. Send `loft_result accepted=true`.
17. Clear session.

Recommended default limits:

```text
maxFrames = 50
maxPointsPerFrame = 50
```

Bypass permission should use the same existing bypass behavior as the current Loft tool.

### Rebuilding Frames From A Future Commit

The commit payload is flat, so rebuild like this:

```java
Map<Integer, List<IndexedPoint>> framesByIndex = new TreeMap<>();

for (DecodedPoint point : decodedPoints) {
    if (point.frameIndex < 0 || point.pointIndex < 0) reject();
    if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) reject();

    framesByIndex
        .computeIfAbsent(point.frameIndex, ignored -> new ArrayList<>())
        .add(new IndexedPoint(point.pointIndex, point.pointId, point.x, point.y, point.z));
}

List<List<Vec3>> frames = new ArrayList<>();
for (List<IndexedPoint> points : framesByIndex.values()) {
    points.sort(Comparator.comparingInt(IndexedPoint::pointIndex));

    List<Vec3> frame = new ArrayList<>();
    Set<Integer> usedPointIndices = new HashSet<>();
    for (IndexedPoint point : points) {
        if (!usedPointIndices.add(point.pointIndex)) reject();
        frame.add(new Vec3(point.x, point.y, point.z));
    }

    if (!frame.isEmpty()) {
        frames.add(frame);
    }
}
```

Recommended: reject duplicate `frameIndex + pointIndex` pairs instead of silently resolving them.

### Future Editable Selection Integration

The current server selection supports add/remove but not moving an existing point. Add one internal method/API that replaces the player’s whole Loft selection:

```java
void setLoftSelection(Player player, String world, List<List<Vec3>> frames);
```

Then a future commit can do:

```java
setLoftSelection(player, session.world, editedFrames);
runExistingLoftSet(player, session.lowPoly);
```

Do not let the client place blocks directly. The server should run exactly the same Loft placement/generation logic used by `/loft set`.

## Timeout And Fallback

Recommended session timeout: 2 to 5 minutes.

On timeout:

- Clear session.
- Send `loft_result accepted=false code="timeout"` if the player is still online.
- Do not place blocks.

Fallback behavior:

- `/loft set` places from the server selection without any client commit packet.
- If sending `loft_session_open` fails, use existing particle preview or cancel based on current command flow.
- If `loft_hello` is absent, never alter current particle preview behavior.

## Low-Poly Flag

The server should pass the current Loft mode:

- normal smooth Loft: `lowPoly=false`
- `/loft set -p` or preview in low-poly mode: `lowPoly=true`

The client uses this only for preview. Server placement must still use the server’s authoritative mode.

## Test Checklist

### Player Without Client Mod

- Does not send `arceon:loft_hello`.
- Server uses existing particle preview.
- Existing `/loft set` still works.
- Existing remove commands still work.

### Player With Client Mod

- Client sends `arceon:loft_hello`.
- Server marks player as wireframe-capable.
- Adding the first point makes the server send `arceon:loft_session_open` without `/loft set`.
- Every later selection edit sends a higher-revision full snapshot.
- Client opens a read-only rendered line preview.
- Every server-side point/frame edit updates the preview.
- Client input does not modify the selection.
- `/loft set` places immediately from the server selection.
- Placement result clears the client preview.
- Cancel clears session and places nothing.

### Mixed Server

- Player A with client gets the rendered line preview.
- Player B without client gets particle preview.
- Their sessions/previews do not affect each other.
- Disconnect clears player capability and active session.

## Notes For Client Compatibility

The current client build supports both:

- Minecraft `1.21.11`
- Minecraft `26.1.2`

Release jars are produced by `buildAll` and collected into:

```text
build/releases/
```

The client preview is independent from Axiom tool windows. It only activates after receiving `arceon:loft_session_open`.
