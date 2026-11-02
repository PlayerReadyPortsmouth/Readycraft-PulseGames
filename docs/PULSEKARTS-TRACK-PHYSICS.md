# PulseKarts configurable track physics

Local candidate, 31 October 2026. No server installation or playtest is implied by this document. Existing maps without the settings below keep their original physics. Prepared for AurorasChaos (on behalf of Player Ready).

## Trial access

The server configuration must explicitly enable `karts.track-physics.enabled`. Every human racer must be listed by UUID in `karts.track-physics.canary-players`, unless `broad: true` is explicitly set. Empty allowlist admits nobody. A configured map is refused when access is off; it is not silently played with different physics. Removing access during a race ends it rather than changing physics mid-flight.

Owner: Aurora / ReadyCraft. Review: 14 November 2026. Remove the temporary rollout gate after Java/Bedrock mixed-client acceptance and stable rollout; retain authoring data. Turning it off blocks configured tracks and ends active configured races. Legacy maps remain available.

## Jump pads

Create `jump1`, `jump2`, etc. regions consecutively. Volumes cover the kart contact point above the road, including its take-off lip. Any per-pad setting opts that pad into the new profile; unconfigured pads retain `jump-power` behaviour.

```yaml
settings:
  jump1-launch-speed: 9
  jump1-gravity: 8
  jump1-air-ticks: 100
  jump1-min-speed: 3
  jump1-cooldown: 40
  jump1-air-steering: 0.25
  jump1-yaw: 0
```

Yaw uses Minecraft degrees: 0 is +Z, 90 is -X, -90 is +X and 180 is -Z. Omit yaw to retain the approach heading. When supplied, it also rejects wrong-way approaches. Launch speed is vertical blocks/second; horizontal speed comes from the approach. Air steering is a fraction of ordinary steering; throttle does not accelerate or brake an active configured flight. Landing resumes ordinary control. One fresh jump press while airborne performs a trick; simply holding the drift button is not a fresh press.

The example is approximately a 2.2-second flight with a 4.84-block apex on a level unobstructed landing in the discrete solver. Actual range depends on entry speed, steering and obstacles. Use a wide landing and test minimum/normal/boosted entry speeds. Pad triggering uses the swept path and previous ground support so a lip crossing can launch after the leading movement leaves the floor. Flying over a pad does not launch again.

| Field | Default | Allowed |
|---|---:|---|
| launch-speed | 9 | 1–30 blocks/second |
| gravity | 8 | 2–48 blocks/second² |
| air-ticks | 100 | 10–200 whole ticks |
| min-speed | 3 | 0–24 blocks/second |
| cooldown | 40 | 1–400 whole ticks |
| air-steering | 0.25 | 0–0.5 |
| yaw | approach heading | -180–180 degrees |

The airtime limit forces descent; it is not a target flight duration. A too-short limit can truncate the arc. World-up jump pads do not launch attached wall/ceiling karts: finish a surface section onto upright road before a jump. Arbitrary surface-normal launches are not part of this version.

## Boost pads

Create `boost1`, `boost2`, etc. regions consecutively. Configure each independently:

```yaml
settings:
  boost1-factor: 1.6
  boost1-ticks: 32
  boost1-cooldown: 30
  boost1-yaw: 0
```

Factor is 1.01–2 (default 1.45), duration 1–200 ticks (default 24), cooldown 1–400 ticks (default 20). Optional yaw has the same direction convention as jumps. Attached surface panels without yaw use the kart's three-dimensional forward direction, so panels work when travelling vertically. On surfaces, leave yaw absent unless a horizontal world direction is intentional.

A pad activates on forward entry, not while parked within it. Fast crossings are swept. Boost sources use the strongest active multiplier, capped at 2; they do not multiply together. A boost raises the available driving speed, rather than instantly teleporting the kart or setting a fixed speed. Author enough approach distance for a launch-speed target. A configured jump preserves its launch speed during flight, including boost-derived approach speed.

`boost-block` remains available. On advanced tracks a continuous material strip requires leaving it before another activation; neighbouring blocks do not repeatedly refresh the effect. Legacy tracks retain their previous material behaviour.

## Surface-driving anti-gravity

`antigrav1` regions and `antigrav-pathN` remain the old speed/glow/ribbon system. Real surface driving uses a separate explicit contact ribbon. Each node has seven comma-separated values:

`x,y,z,normalX,normalY,normalZ,halfWidth`

Put nodes in driving order in a semicolon-separated `antigrav-surface1` setting. Coordinates describe the centre of the chassis contact plane, not a rail floating through the middle of its body. The normal points away from the road towards the driver. Normal (0,1,0) is an ordinary floor; (1,0,0) is a wall; (0,-1,0) is a ceiling. Half-width is measured sideways in the local surface plane.

```yaml
settings:
  antigrav-surface1: "0,64,0,0,1,0,5; 0,64,4,0,1,0,5; 0,64,8,0,1,0,5"
```

This minimal example is a flat section. A full rotation data example is provided in `examples/kart-surface-settings.json`; it contains contact data only, not a world template or a playable map. The solver uses the authored ribbon as the supporting surface, so builders must align visible road geometry with it. Blocks still obstruct the oriented chassis. No chunk generation occurs during driving.

Surface constraints: at most 32 sections, 2–256 nodes per section, 0.25–16 blocks between nodes, half-width 2–32 blocks, bounded adjacent direction/normal changes and finite coordinates within ±30 million blocks. Entry and exit must be upright and horizontal. Add intermediate nodes around bends and banks. The parser rejects malformed/discontinuous data instead of approximating it silently.

A kart attaches only near a section's start, facing forward and supported; it cannot snap to the nearest distant overpass. Steering works in the local surface plane. The chassis rotates with the surface, and collision uses its oriented body rather than an upright box. Driving off the side releases adhesion. If there is insufficient room to resume an upright chassis, recovery uses the existing safe reset path. Surface ends should feed a wide upright run-out.

Bots use the same surface simulation and centre-seeking local steering while attached. Their ordinary ordered `bot-waypointN` route remains required and should follow the surface at close intervals; this does not implement branch graphs or route choice. Track positions and checkpoint order retain the existing race rules.

## Visual and rider contract

Surface tracks require PulseContent's versioned pose API in addition to ordinary kart readiness. Old renderers must refuse these tracks instead of showing an upright kart on a wall. Java and Bedrock rotation require matching companion renderer/pack support; build and acceptance status is recorded in the handoff.

The model can pitch, roll and invert. Vanilla seated player bodies and cameras do not acquire arbitrary roll from a server-side model transform. The camera remains upright; a client mod would be a separate feature. Validate rider placement, visibility, motion comfort and ceiling clearance on both editions before offering these tracks publicly.

## Acceptance course

Start with a wide test area containing two independently configured jumps, a straight boost, a boosted jump approach, a gently banked section and a complete floor/wall/ceiling/floor twist. Keep checkpoints and resets on upright approaches/exits. Test forward and reverse approaches, stopped karts, high-speed thin-pad crossings, low/high entry speeds, tricks, obstacles, leaving the edge, reconnect and forced reset. Verify mixed clients see the same pose and follow the same path.

A valid config or passing simulation test does not prove a map is driveable. Branching checkpoints, lap-time boards, advanced item trajectories on walls and full rider/camera roll are separate work. Keep the trial off until its matching artifacts and actual game acceptance are verified.
