# ReadyPlayerZ custom undead: PulseContent integration

Status: engineering contract, 15 October 2026. Aurora approved the four visual concepts on this date. Asset production is proceeding separately in PulseContent; nothing is installed or accepted on clients by this document.

## Existing callable contract

Verified against PulseContent source in the dungeon prototype worktree:

- `PulseContentPlugin.spawnEntity(String id, Location location)` returns Bukkit `Entity` and throws on failure. Call only on the main server thread. It has no owning-plugin parameter, scale parameter, audience list or closeable handle.
- **Pass a bare entity definition key**, for example the proposed `readyz_rustwalker`. EntityRuntime matches `Definition.id()` exactly. `readycraft:readyz_rustwalker` is not accepted by this method; namespaced identifiers belong to the separate visual/item APIs.
- The method requires a running, active content revision and, when `require-bedrock-ready` is true (the default), a matching ready bridge. This is not a per-player readiness check. For a custom-only encounter, separately check `isContentReady(Player)` for every participant; that validates the current Java pack revision or Bedrock session revision/fingerprint.
- EntityRuntime calls MythicMobs `spawnMythicMob(mythicId, location, 1)`. Validate the result is the expected living base type before applying game health, AI targets or tracking. A null result is rejected. A failed/incorrect result must not leave an untracked entity behind.
- ReadyPlayerZ deliberately uses `hasVisual(fullyQualifiedId)` as an explicit eligibility gate before spawning. It verifies the current catalogue namespace/definition and requires `minigames-visuals.enabled` plus the exact ID in `minigames-visuals.allowed-content`, even though this is an encounter rather than a player visual. After this succeeds and participants are ready, pass only the suffix to `spawnEntity`. This gate alone does not prove Mythic has activated that definition; handle spawn failure as well.
- Entity definitions accept base types `HUSK`, `ZOMBIE` and `COW`. Prefer adult `ZOMBIE` or `HUSK` for these enemies, with game behaviour and targeting controlled consistently by the encounter code.

The optional entity runtime currently requires tested BetterModel **3.5.0**, MythicMobs **5.13.0**, and an enabled GeyserModelEngine bridge. BetterModel must use unobfuscated `bettermodel` namespace, ZIP output and its default `BetterModel/build` output location. PulseContent `entities.enabled` must be configured before runtime startup. These are inspected source requirements, not proof that a particular server currently satisfies them.

## Ownership and cleanup

PulseContent registers each successful managed Mythic spawn, marks it nonpersistent and records `pulsecontent:entity_id` in its persistent-data container. Mythic/BetterModel owns model attachment. PulseGames should retain the exact returned entity UUID in its match-owned enemy collection and remove those entities on match end, abort, empty match, disable and failed setup. Do not sweep other entities or rely solely on world unload.

There is no returned death/visual handle to close for a Zombie or other base mob. On normal death PulseGames retires its own UUID/wave bookkeeping; PulseContent retires its loot entry and Mythic/BetterModel is responsible for the attached model death lifecycle. There is no public owner-aware removal API in this contract. Calling `Entity.remove()` is the current game-side removal operation. PulseContent's bookkeeping reaps absent entities every 200 ticks; its own shutdown removes all still-tracked encounter entities. Verify removal also releases Mythic/BetterModel/Geyser render state on both clients; source ownership alone is not a client cleanup acceptance result.

Normal death appends configured catalogue drops once through a HIGHEST-priority listener. Removal does not pay drops. For ReadyPlayerZ, use **empty catalogue drops** so match progression/rewards remain authoritative and listener ordering cannot leak content items into the minigame. Generated Mythic definitions already suppress ordinary drops, random equipment and sunburn.

## Scale and collision

Neither the catalogue schema nor `spawnEntity` accepts runtime scale. Do not add a `scale` field: the strict catalogue parser rejects it. The separate prop/visual attachment path's scaling support is not this entity-spawn contract.

The pinned BetterModel 3.5.0 entity scaler multiplies model geometry by Bukkit entity scale, and GeyserModelEngine forwards that scale to Bedrock. Therefore normalize Foreman source coordinates, bone origins and animation translations by 1.5, and Plated by 1.1; the game applies those factors once at runtime. Intended resulting model heights are 2.7 and 2.0625 blocks respectively. Native collision is also scaled: the boss needs three-block-high routes. The broad visual shoulders exceed the native body width, so verify corners, damage reach and model/hitbox agreement on both clients. Do not enlarge damage reach to compensate for artwork.

Primary source: [BetterModel entity scaler](https://github.com/toxicity188/BetterModel/blob/3.5.0/api/src/main/java/kr/toxicity/model/api/tracker/ModelScaler.java#L162), [GeyserModelEngine scale forwarding](https://github.com/GeyserExtensionists/GeyserModelEngine/blob/5af2f6873702359dcaaf09c7dbd44aaa507a5304/paper/src/main/java/re/imc/geysermodelengine/managers/model/propertyhandler/BetterModelPropertyHandler.java#L28). The dependency-locked jars were also inspected; actual client acceptance remains required.

## Approved original art direction

These keys identify the approved designs; availability depends on the separately validated content pack. The logical namespace may be `readycraft`; callers still use the bare key shown below.

| Proposed key | Visual concept | Readable gameplay identity |
|---|---|---|
| `readyz_rustwalker` | Mossy Rustwalker maintenance worker, green overalls, asymmetric sleeves | Ordinary slow undead, readable walking silhouette |
| `readyz_runner` | Lean courier, orange shoulder fabric, forward posture | Fast threat distinguishable without relying on colour alone |
| `readyz_plated` | Plated scrapyard guard, blue-grey metal shoulder and forearm plates | Durable enemy with visibly heavier silhouette |
| `readyz_foreman` | Oversized Foreman boss, purple-grey armour with gold accents | Boss silhouette with clear attack wind-up and vulnerable-state cues |

Keep faces stylised and non-graphic. Retain recognisable role silhouettes at low brightness and on mobile screens. The boss should look deliberately built at its size, rather than an ordinary zombie stretched uniformly. Names and palettes remain changeable during visual review.

The gameplay prototype includes walker, runner, brute, venom and boss roles. Plated maps to brute; venom can use a vanilla zombie until it has separate approved artwork. No summoning mechanic or boss phases are implemented. Configure optional models with flat arena keys `zombie-model-walker`, `zombie-model-runner`, `zombie-model-brute`, `zombie-model-venom` and `zombie-model-boss`, using fully qualified catalogue IDs. Unset keys use native zombies.

## Authoring contract

Merge approved definitions into the existing `content.json` `entities` object; never replace the whole catalogue. Each definition permits only `name`, `model`, `type`, `health`, `damage`, `speed` and `drops`. Current bounds: 32 entity definitions total; health 1–1024, damage 0–100, speed 0–1. Higher wave scaling must explicitly validate native attribute limits and remain separate from art metadata.

Models use the strict portable Blockbench 4.x free-format subset:

- One embedded PNG, power-of-two dimensions from 16 to 512, no more than 4 MiB; model source no more than 8 MiB.
- One to 256 cuboids, at most 64 bones, nesting depth at most 12. Six explicit UV faces; no static element/bone rotation, inflation, external texture references or arbitrary effects tracks.
- Exactly the supported animation family: `idle`, `walk`, `attack`, `hurt`, `death`. All five are required. Linear bone position/rotation/scale keyframes only; at most 4096 keyframes overall. Idle/walk should loop; attack/hurt/death should play once.
- Cube coordinates and origins remain within the compiler's -64 to 64 bounds. Geometry and animation acceptance must pass the actual compiler, not merely Blockbench export.
- Combined compiled entity assets are bounded to 24 MiB.

Renderer IDs are derived from namespace, entity key and a source hash. Do not hard-code their suffix or assume a Geyser namespace. Inspect the generated bridge registry when installing. Attribute original work to `AurorasChaos (on behalf of Player Ready)` and preserve any third-party licensing if subsequently introduced.

## Production acceptance and activation

1. Approve an original visual preview with front, side and rear views and human-scale comparison, including the boss. No asset production before this approval.
2. Compile each source through PulseContent and verify deterministic pack output, required animation names, textures and catalogue bounds. Review the merged catalogue to preserve all existing cosmetics, props and karts.
3. Inspect generated Mythic configuration and confirm empty drops and expected base types. Confirm all models load in BetterModel; add each exact fully qualified ID to `minigames-visuals.allowed-content` and enable that visual gate before enabling its match spawn mapping. This gate is intentional ReadyPlayerZ eligibility, not a substitute for entity-runtime readiness.
4. Stage matching Java/Bedrock content. New or changed entity models require the Geyser host restart and Bedrock reconnect under the existing import path; stats-only changes retain registry IDs. Obtain fresh restart permission.
5. On Java and Bedrock, verify pack readiness, correct model rather than a vanilla fallback, textured surfaces, walk/attack/hurt/death timing, rotation, size and collision, short-range and distant views, and several enemies together.
6. Verify kills count once, death does not leak items, cancel/quit/empty match cleans every owned entity, reconnect reveals no ghost models, and no custom enemy survives instance cleanup.
7. Exercise an unavailable model/provider/pack case. Report the error clearly and either refuse the custom encounter or use an explicitly configured vanilla fallback; never describe a fallback as custom content acceptance.

Source references: PulseContent `paper/.../PulseContentPlugin.java`, `paper/.../EntityRuntime.java`, `src/main/java/.../EntityCatalogue.java`, `src/main/java/.../entity/EntityModelCompiler.java`, and `examples/clockwork-encounter/README.md`.
