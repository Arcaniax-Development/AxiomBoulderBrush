# Loft Point Dragging Plan

## Objective

Add optional client-side point dragging without changing the current preview-only default behavior.

- Left-click a control point to pick it up.
- While held, the point follows the player's camera and the complete Loft preview updates locally in real time.
- Right-click places the point and submits one authoritative move to the server.
- Left-click while holding cancels and restores the original point.
- Backspace or Delete removes the control point currently under the crosshair.
- The server remains authoritative and `/loft set` continues to place from the server selection.
- Point dragging is available only in Creative or Spectator mode.

## Compatibility Strategy

Keep protocol `1` as read-only preview.

Introduce protocol `2` for point movement. The server should support both:

- Protocol 1: live read-only wireframe preview.
- Protocol 2: preview plus point dragging.
- No client hello: existing server particle preview.

The server must not assume every wireframe-capable client supports dragging.

## Game Mode Eligibility

Point hovering, pickup, dragging, and placement must work only while the player is in:

- Creative mode
- Spectator mode

In Survival or Adventure mode:

- Do not raycast Loft point hitboxes.
- Do not highlight draggable points.
- Do not consume attack/use input.
- Do not send point-move packets.
- Do not send point-delete packets.

If the player changes out of Creative/Spectator while dragging, cancel immediately and restore the authoritative point position.

The client check is for interaction behavior only. The server must independently validate the player's current game mode before accepting `loft_point_move`.

## Client State Machine

```text
PREVIEW
  left-click point -> DRAGGING
  backspace/delete while hovering point -> AWAITING_SERVER_DELETE

DRAGGING
  every render frame -> update temporary point and local geometry
  left-click -> restore original point -> PREVIEW
  right-click -> send point move -> AWAITING_SERVER
  world/session change -> cancel locally -> PREVIEW

AWAITING_SERVER
  accepted snapshot/result -> replace authoritative snapshot -> PREVIEW
  rejected result -> restore original point -> PREVIEW
  newer conflicting revision -> use server snapshot -> PREVIEW

AWAITING_SERVER_DELETE
  accepted snapshot/result -> remove point from preview -> PREVIEW
  rejected result -> keep authoritative point -> PREVIEW
```

Only consume attack/use input when a point is picked or currently held. Normal Minecraft input must remain unchanged otherwise.

Only consume Backspace/Delete when all of these are true:

- No chat, inventory, menu, or other screen is open.
- Player is in Creative or Spectator mode.
- Client is in `PREVIEW`, not currently dragging.
- The crosshair ray intersects a Loft point hitbox.

## Point Hit Testing

Each control point has a client-only AABB centered on its coordinate:

```text
size: 0.5 x 0.5 x 0.5
minimum: point - (0.25, 0.25, 0.25)
maximum: point + (0.25, 0.25, 0.25)
```

On left-click:

1. Build a ray from camera position along camera look direction.
2. Test every control-point AABB within the configured interaction range.
3. Choose the nearest positive ray intersection.
4. Store its `pointId`, `frameIndex`, `pointIndex`, original position, session revision, and camera distance.

Use packet indices only as validation context. `pointId` identifies the selected point across live snapshots.

## Camera-Locked Movement

Preserve the distance measured when the point was picked up:

```text
temporaryPosition = cameraPosition + cameraLookDirection * grabDistance
```

Recalculate this every rendered frame so camera rotation and movement remain visually smooth.

Initial version:

- No block snapping.
- No mouse-wheel distance adjustment.
- No intermediate movement packets.

Those can be added later without changing the basic move packet.

## Live Local Preview

Maintain two client snapshots:

- `authoritativeFrames`: latest complete server snapshot.
- `previewFrames`: authoritative frames plus the temporary held-point override.

While dragging:

1. Update only the temporary point coordinate every render frame.
2. Mark preview geometry dirty when movement exceeds a small epsilon such as `0.0025` blocks.
3. Rebuild using the exact server Bezier solver.
4. Atomically replace the cached render snapshot.
5. Never send intermediate coordinates to the server.

Server live snapshots received while dragging:

- Same session and same/older revision: keep the local held-point override.
- Newer revision containing the same point: merge other server changes, then reapply the held-point override.
- Session changed, point removed, or world changed: cancel dragging and accept the server snapshot.

## Performance Plan

The normal preview remains fully cached per server revision.

During dragging only:

- Rebuild at render-frame rate when the point actually moves.
- Reuse temporary arrays/lists where practical to reduce garbage collection.
- Keep rails and frame curves at normal adaptive quality.
- Temporarily reduce intermediate cross-sections if geometry generation exceeds the frame budget.
- Restore full preview quality immediately after cancel, placement, or server acknowledgement.

Suggested budget:

```text
target geometry rebuild: under 3 ms
soft fallback: fewer intermediate cross-sections
hard cap: existing rail/section sample caps
```

Log rebuild duration and segment count under `[LOFT DEBUG]` while developing.

## Input Integration

Create a small module-specific input adapter because Minecraft/Fabric input APIs differ between 1.21.11 and 26.1.2.

Shared controller responsibilities:

- Hit testing
- Drag state
- Temporary coordinates
- Cancel/place behavior
- Packet submission

Version-specific adapter responsibilities:

- Detect left/right click before vanilla handling.
- Consume the click only when Loft interaction handles it.
- Provide camera position/look direction.

Do not integrate through Axiom tool windows.

## Packet Contract

### `arceon:loft_point_move`

Direction: client to server

```text
protocolVersion:int             // 2
sessionId:uuid
revision:int
pointId:int
frameIndex:int
pointIndex:int
x:double
y:double
z:double
```

The client sends this once on right-click placement.

No packet is sent when left-click cancels a drag.

### Result

Reuse `arceon:loft_result`:

```text
accepted=true, code="point_moved"
accepted=false, code="bad_revision"
accepted=false, code="bad_point"
accepted=false, code="bad_world"
accepted=false, code="no_permission"
accepted=false, code="invalid_coordinates"
```

After accepting a move, the server increments the revision and sends a complete `loft_session_open` snapshot containing the authoritative point position.

### `arceon:loft_point_delete`

Direction: client to server

```text
protocolVersion:int             // 2
sessionId:uuid
revision:int
pointId:int
frameIndex:int
pointIndex:int
```

The client sends this once when Backspace or Delete is pressed while looking at a point.

The server validates it exactly like a move request except no coordinates are supplied. On acceptance:

1. Remove the authoritative point.
2. Remove its frame if that frame becomes empty, matching the existing Loft selection rules.
3. Increment the revision.
4. Send `loft_result` with `accepted=true`, `code="point_deleted"`.
5. Send the complete updated `loft_session_open` snapshot.
6. If the whole selection becomes empty, clear the session and preview instead.

Recommended delete rejection codes:

```text
bad_revision
bad_point
bad_world
no_permission
invalid_game_mode
```

## Server Validation

Validate before mutating the Loft selection:

1. Protocol version supports point movement.
2. Player capability includes dragging.
3. Session ID belongs to the player.
4. Revision matches the active session.
5. Player is still in the session world.
6. Player is currently in Creative or Spectator mode.
7. Player has `arceon.axiom.loft` permission.
8. Point ID exists exactly once.
9. Frame and point indices match that point ID.
10. Coordinates are finite.
11. Coordinates are inside world/build limits.
12. Optional maximum movement/range policy passes.

On acceptance:

1. Update the server's authoritative Loft selection.
2. Increment session revision.
3. Send `loft_result(point_moved)`.
4. Broadcast/send the complete updated session snapshot to that player.

On rejection:

1. Do not mutate the server selection.
2. Send a rejected `loft_result`.
3. Resend the current authoritative session snapshot.

## `/loft set` Behavior

`/loft set` remains server-authoritative and does not wait for a client commit.

- If no point is being dragged, place immediately from the current server selection.
- Opening chat/commands while dragging should cancel the local drag first.
- A move awaiting server acknowledgement should either finish first or cause `/loft set` to use the last accepted server position.

## Visual Feedback

While hovering a point:

- Highlight its 0.5-block hitbox or marker.

While dragging:

- Use a distinct marker color.
- Keep its `frame#point` label visible.
- Optionally draw a thin camera-to-point guide line.

While awaiting acknowledgement:

- Keep the point at its optimistic local position.
- Use a temporary pending color until the server snapshot arrives.

## Implementation Order

1. Add shared drag state and point AABB raycasting.
2. Add hover/held visual states without moving points.
3. Add local camera-locked point override.
4. Add frame-rate geometry rebuild with timing logs.
5. Add version-specific click interception for both targets.
6. Add protocol 2 capability negotiation and `loft_point_move` packet.
7. Add hover deletion and the `loft_point_delete` packet.
8. Add server validation/update/result handling.
9. Add rejection, revision-conflict, disconnect, and world-change recovery.
10. Performance-test large 50x50 selections.

## Acceptance Tests

- Left-click selects the nearest point AABB only.
- Backspace and Delete remove only the point currently under the crosshair.
- Backspace/Delete do nothing when no point is targeted or a GUI is open.
- Accepted deletion removes empty frames according to server selection rules.
- Rejected deletion restores/keeps the authoritative snapshot.
- Creative and Spectator players can interact with points.
- Survival and Adventure players cannot highlight, pick up, move, or place points.
- Changing to Survival/Adventure during a drag cancels it immediately.
- Ordinary attacks work when no point is targeted.
- Held point follows camera smoothly at high frame rate.
- Entire Bezier wireframe updates while moving.
- Right-click submits exactly one packet.
- Accepted move persists in the next server snapshot.
- Left-click cancellation sends no move packet and restores the point.
- Rejection restores the authoritative server point.
- Opening chat while dragging cancels safely.
- Revision conflict cannot overwrite newer server changes.
- Protocol 1 clients remain preview-only.
- Players without the mod retain particle previews.
- Both Minecraft targets behave identically.
