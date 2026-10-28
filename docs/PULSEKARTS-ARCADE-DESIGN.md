# PulseKarts arcade pack - design (28 October 2026, candidate 0.4.3)

Aim: collectable item boxes, boost panels, jumps, anti-gravity sections, drift tiers and a clearer HUD, without
changing Copper Canyon Raceway. **Every feature is optional per arena.** A map with none of the new entries drives
exactly as before (the only unconditional changes are the item table, roulette, drift tiers and HUD). Nothing here is
deployed or driven yet; see "Live drive" in the PR.

All timings are 20 Hz server ticks. Boost multipliers are the existing `KartPhysics` speed multiplier (top speed 12 b/s x
multiplier, hard clamp 2). Boosts no longer share one timer: each source (turbo, drift, panel, trick, star, bullet,
anti-gravity, bump) keeps its own factor and expiry, and the kart uses the **largest active factor**, so boosts never stack.

## Mechanics and numbers

| Feature | Rule |
|---|---|
| Item box (location) | Spinning, bobbing glass cube (`itemboxN` setting). Collected when the chassis path (swept segment) passes within **1.2 blocks**; burst particles + chime; **global** respawn after **80 ticks (4 s)** with a 10-tick scale-up fade-in. A kart already holding or rolling an item passes through without consuming the box. |
| Item box visual | PulseContent `readycraft:kart_pickup_box` through the world-visual bridge when the API and every racer's pack are ready; otherwise a plain `ItemDisplay` (cyan stained glass, aqua glow). Legacy `itemboxN` regions keep their existing region crossing and personal 5 s cooldown. |
| Roulette | **30 ticks** (1.5 s). Hotbar slot 1 cycles item icons at 4/4/4/5/6/7-tick steps (max 5 Hz, one small icon, no screen flash) with a tick sound; the item chosen at collection lands at the end. Right-click is ignored while rolling. Bots skip the roulette. |
| Boost panel | `boostN` region entry (or standing on a `boost-block` material) -> **1.5 s at x1.50**, rear trail + whoosh. Cooldown **20 ticks per panel per kart** (the block rule is one shared "block" panel). |
| Jump | `jumpN` region entry while grounded and >= 3 b/s -> vertical speed `5 + 5 x min(1.5, speed/12)` b/s (x `jump-power`). Gravity 24 b/s^2, so 12 b/s launches ~2.1 blocks high for ~0.83 s and ~10 blocks forward. **Air-time cap 30 ticks**: past it the kart is forced down. Pad cooldown 40 ticks per kart. |
| Trick | Rising edge of the jump/drift key **after 4 air ticks of a ramp launch** starts a 12-tick seat spin (30 degrees/tick, cosmetic: physics heading is untouched). Landing after a trick gives **0.8 s at x1.35**. Landing never needs the track: a landing on solid ground is simply driving; falling below `void-y` uses the existing `checkpoint-spawn` reset. |
| Anti-gravity | `antigravN` region: **x1.10**, glowing ribbon along the `antigrav-pathN` points inside that region, and kart-to-kart contact inside gives **both** karts **0.75 s at x1.30** (per-kart pair cooldown 40 ticks). **No spline hold and no tilt in this version** (see below). |
| Drift tiers | Held drift charges **20 / 50 / 80 ticks** (1 / 2.5 / 4 s). Release gives mini-turbo **x1.40 for 1.0 s**, super **x1.55 for 1.5 s**, ultra **x1.70 for 2.0 s**. Sparks blue / orange / purple plus a rising pling and the tier word in the HUD (never colour alone). |
| Banana | Dropped 2.5 blocks behind, armed after 10 ticks (owner-safe for 20), expires after 30 s, at most 24 live per match. Hit = 11-tick spin, speed x0.45, 30-tick slow, then the usual hit protection. Star karts destroy it; a shield pops instead. |
| Triple shells | Three uses of the existing shell, at least 10 ticks apart. |
| Star | 10 s: invulnerable to shells, bananas, lightning and bumps, speed x1.20, music-note particles. |
| Shield | Right-click to raise; blocks the next hit of any kind, then pops. Expires after 15 s. |
| Bullet | 8 s auto-pilot along the **bot route**, x1.60 speed, invulnerable. Only drawn if the arena has a valid `bot-waypointN` route **and** `bots-enabled: true` (the route is marked reviewed); otherwise it is simply not in the table (equivalent to a re-roll). Resets use `checkpoint-spawn`. |
| Safety | No item moves another kart sideways or upwards; items only slow, spin (in place, collision-checked) or shield. Bullet and star only add forward speed through the normal physics, so wall and void rules still apply. |

### Position-aware item table

Place is by checkpoint progress with distance-to-next-gate as the tie-break. Tier: leader = place fraction <= 0.34
(places 1-4 of 12), back = >= 0.67 (places 9-12), middle otherwise. Weights out of 100; trickster doubles turbo and
lightning weights; an unavailable item (bullet without a route, star/bullet for bots unless a human holds one) drops its
weight and the rest renormalise.

| Item | Leader | Middle | Back |
|---|---:|---:|---:|
| Banana | 30 | 15 | 0 |
| Shell | 25 | 20 | 5 |
| Turbo | 20 | 20 | 15 |
| Shield | 20 | 10 | 5 |
| Triple shells | 5 | 15 | 15 |
| Star | 0 | 10 | 20 |
| Lightning | 0 | 5 | 15 |
| Bullet | 0 | 5 | 25 |

## HUD and feel

Action bar (every 10 ticks, never over a fresh notice): `L2/3 P4/8 Banana BOOST` - lap, place, held item, boost or drift
tier. A soft chime when a place is gained and a low note when lost (stable for 10 ticks, at most once per 3 s). Entering
the last lap of a multi-lap race shows the title `FINAL LAP!` (0.25 s in, 1.5 s, 0.5 s out). Titles stay under 12
characters. **Photosensitivity:** at most one lightning flash per 100 ticks (extra strikes slow the target but show
particles instead); no new strobing: roulette icons change at most 5 Hz in one slot, particles are continuous trails not
pulses, the boost ring and sparks are small.

## Arena entries (all optional)

| Entry | Kind | Meaning |
|---|---|---|
| `itembox1..N` | location | Box position on the racing line (feet height; the cube floats 0.9 above). If any exist they replace `itembox` regions. At most 32 boxes. |
| `boost1..N` | region | Boost panel. |
| `boost-block` | setting | Block material name(s), comma separated, e.g. `BLUE_GLAZED_TERRACOTTA`. |
| `jump1..N` | region | Launch pad at the ramp lip. |
| `jump-power` | setting | 0.5-1.5 (default 1) vertical scale for every jump. |
| `antigrav1..N` | region | Anti-gravity section. |
| `antigrav-path1..M` | location | Ordered rail points; each belongs to the `antigravN` region containing it. Visual ribbon only. |

Validation refuses the race only for malformed optional entries (gaps in numbering, unknown block, power out of range).

## Anti-gravity: what was and was not built

The kart is a server-teleported armour stand with a custom 20 Hz physics model and the PulseContent visual follows yaw
only. Holding it to a spline would fight wall collision, step-ups and the Bedrock link, and tilting the model needs a
visual API that does not exist. This version is therefore speed + glowing ribbon + bump boost: the "wall/ceiling run" is
the map builder's decor beside a normal drivable floor.

## Bots

Bots collect boxes they pass (build boxes on the driving line), use shell, triple shell, banana (opponent close behind),
shield and turbo, and receive star or bullet only when a human currently holds one. Over `jumpN` they stop probing for
drops and hold throttle in the air; boosted they raise their target speed by 60% of the boost; they never use panels
specially, because the route already crosses them.

## Bedrock notes

`ItemDisplay` rendering through Geyser depends on the proxy version, so each box also emits an `END_ROD` marker column
(cross-platform). Titles are two words, action bar under ~35 characters, hotbar icons are plain items, and the jump key
is the same input already used for drifting. The trick spin rotates the seat yaw only; whether the Bedrock kart link
follows it needs a live check.
