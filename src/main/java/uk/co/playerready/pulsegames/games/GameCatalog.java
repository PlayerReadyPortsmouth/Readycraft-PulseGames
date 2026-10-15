package uk.co.playerready.pulsegames.games;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameRegistry;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.games.avalanche.AvalancheGame;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsGame;
import uk.co.playerready.pulsegames.games.blockparty.BlockPartyGame;
import uk.co.playerready.pulsegames.games.buildbattle.BuildBattleGame;
import uk.co.playerready.pulsegames.games.deathrun.DeathRunGame;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import uk.co.playerready.pulsegames.games.elytra.ElytraRacingGame;
import uk.co.playerready.pulsegames.games.karts.PulseKartsGame;
import uk.co.playerready.pulsegames.games.kitpvp.KitPvpGame;
import uk.co.playerready.pulsegames.games.koth.KothGame;
import uk.co.playerready.pulsegames.games.luckypillars.LuckyPillarsGame;
import uk.co.playerready.pulsegames.games.parkour.ParkourGame;
import uk.co.playerready.pulsegames.games.partygames.PartyGamesGame;
import uk.co.playerready.pulsegames.games.prophunt.PropHuntGame;
import uk.co.playerready.pulsegames.games.readyplayerz.ReadyPlayerZGame;
import uk.co.playerready.pulsegames.games.skywars.SkywarsGame;
import uk.co.playerready.pulsegames.games.spleef.SpleefGame;
import uk.co.playerready.pulsegames.games.supermarket.SupermarketSweepGame;
import uk.co.playerready.pulsegames.games.tntrun.TntRunGame;
import uk.co.playerready.pulsegames.games.villagerdefense.VillagerDefenseGame;
import uk.co.playerready.pulsegames.games.volcano.VolcanoGame;

import java.util.List;

import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.location;
import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.optional;
import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.region;
import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.regions;
import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.setting;
import static uk.co.playerready.pulsegames.core.setup.SetupRequirement.spawns;

/** Registers every PulseGames minigame with its modes and setup requirements. */
public final class GameCatalog {

    private GameCatalog() {
    }

    public static void registerAll(GameRegistry registry) {
        registry.register(new GameType("quake", "Quake",
                "Fast arena shooting: choose a railgun or scattergun, dash and leap!", Material.IRON_HOE,
                List.of(GameMode.ffa("classic", "Classic", 2, 16)),
                uk.co.playerready.pulsegames.games.quake.QuakeGame::new,
                List.of(spawns(4, "safe fallback spawn points; spread across the arena"),
                        optional(region("spawn-area", "playable random-spawn volume; feet and head space, excluding roof/lobby")),
                        setting("void-y", "fall-off respawn level"),
                        optional(setting("target-kills", "winning side score, default 20")),
                        optional(setting("time-limit", "round length in seconds, default 300"))), true));
        registry.register(new GameType("dodgeball", "Dodgeball",
                "Dodge, aim and throw! Three hits and you are out.", Material.SNOWBALL,
                List.of(GameMode.ffa("solo", "Solo", 2, 12),
                        GameMode.teams("teams", "Teams", 2, 4, 2)),
                uk.co.playerready.pulsegames.games.dodgeball.DodgeballGame::new,
                List.of(spawns(2, "opposite ends of an enclosed court"),
                        setting("void-y", "fall-off elimination level"),
                        optional(setting("throw-cooldown-ticks", "throw delay in ticks (1–40; default 7)"))), true));
        registry.register(new GameType("dropper", "Dropper",
                "Fall through obstacles and land in the water!", Material.WATER_BUCKET,
                List.of(GameMode.ffa("practice", "Practice", 1, 1),
                        GameMode.ffa("race", "Race", 2, 12)),
                uk.co.playerready.pulsegames.games.dropper.DropperGame::new,
                List.of(spawns(1, "first launch platform"),
                        region("finish", "final water landing pool"),
                        optional(regions("checkpoint", 1, "intermediate water pools, in order")),
                        optional(location("checkpoint-spawn1", "next launch after checkpoint1; repeat per checkpoint")),
                        setting("void-y", "missed landing retry level")), true));
        registry.register(new GameType("spleef", "Spleef",
                "Dig the floor out from under everyone else!", Material.DIAMOND_SHOVEL,
                List.of(GameMode.ffa("classic", "Classic", 2, 12),
                        GameMode.ffa("decay", "Decay", 2, 12),
                        GameMode.ffa("splegg", "Splegg", 2, 12)),
                SpleefGame::new,
                List.of(spawns(4, "spread around the floor"),
                        regions("floor", 1, "the breakable snow floor layer(s)"),
                        setting("void-y", "Y level below the floor that eliminates players")),
                false));

        registry.register(new GameType("tntrun", "TNT Run",
                "The floor crumbles behind you - keep moving!", Material.TNT,
                List.of(GameMode.ffa("classic", "Classic", 2, 16),
                        GameMode.ffa("doublejump", "Double Jump", 2, 16)),
                TntRunGame::new,
                List.of(spawns(4, "spread around the top layer"),
                        setting("void-y", "Y level below the lowest layer")),
                false));

        registry.register(new GameType("blockparty", "Block Party",
                "Stand on the called color before the floor vanishes!", Material.MAGENTA_WOOL,
                List.of(GameMode.ffa("classic", "Classic", 2, 20),
                        GameMode.ffa("hardcore", "Hardcore", 2, 20)),
                BlockPartyGame::new,
                List.of(spawns(1, "on the dance floor"),
                        region("floor", "the color dance floor (1 block tall)"),
                        setting("void-y", "Y level just below the floor")),
                false));

        registry.register(new GameType("avalanche", "Avalanche",
                "Dodge the falling blocks - survive the storm!", Material.SNOW_BLOCK,
                List.of(GameMode.ffa("classic", "Classic", 2, 16)),
                AvalancheGame::new,
                List.of(spawns(1, "on the survival platform"),
                        region("arena", "the platform area; blocks fall from its top Y")),
                false));

        registry.register(new GameType("volcano", "Volcano",
                "The lava is rising - climb for your life!", Material.MAGMA_BLOCK,
                List.of(GameMode.ffa("classic", "Classic", 2, 16),
                        GameMode.ffa("eruption", "Eruption", 2, 16)),
                VolcanoGame::new,
                List.of(spawns(1, "at the foot of the mountain"),
                        region("arena", "full climbable area, bottom = first lava level")),
                false));

        registry.register(new GameType("koth", "King of the Hill",
                "Hold the hill to score - fight off everyone else!", Material.GOLDEN_HELMET,
                // Teams needs 2*teamSize to start: below that everyone lands on one team,
                // nobody can be damaged, and all four "win" when the clock runs out.
                List.of(GameMode.ffa("solo", "Solo", 2, 12),
                        GameMode.teams("teams", "Teams", 8, 16, 4)),
                KothGame::new,
                List.of(spawns(4, "around the hill (team games: spawn N = team N)"),
                        region("hill", "the capture zone on top")),
                false));

        registry.register(new GameType("duels", "Duels",
                "1v1, best of three rounds. Prove yourself!", Material.IRON_SWORD,
                List.of(GameMode.ffa("classic", "Classic", 2, 2),
                        GameMode.ffa("op", "OP", 2, 2),
                        GameMode.ffa("sumo", "Sumo", 2, 2),
                        GameMode.ffa("legacy-classic", "Classic combat · Regular PvP", 2, 2),
                        GameMode.ffa("legacy-sumo", "Classic combat · Sumo", 2, 2),
                        GameMode.ffa("modern-classic", "Modern combat · Regular PvP", 2, 2),
                        GameMode.ffa("modern-sumo", "Modern combat · Sumo", 2, 2),
                        GameMode.ffa("modern-crystal", "Modern combat · Crystal", 2, 2),
                        GameMode.ffa("modern-mace", "Modern combat · Mace", 2, 2)),
                DuelsGame::new,
                List.of(spawns(2, "two facing spawn points"),
                        setting("void-y", "fall-off level (sumo ring-out)"),
                        optional(region("combat", "Crystal placement area; use only on an approved Crystal map")),
                        optional(setting("crystal-enabled", "opt in this map to Crystal Duels")),
                        optional(setting("mace-enabled", "opt in this map to Mace Duels"))),
                false));

        registry.register(new GameType("kitpvp", "Kit PvP",
                "Free-for-all arena fighting with respawns.", Material.NETHERITE_SWORD,
                List.of(GameMode.ffa("ffa", "FFA", 2, 16),
                        GameMode.ffa("oitc", "One in the Chamber", 2, 12)),
                KitPvpGame::new,
                List.of(spawns(4, "spread around the arena")),
                false));

        registry.register(new GameType("parkour", "Parkour Race",
                "Race the course - falling sends you back!", Material.RABBIT_FOOT,
                List.of(GameMode.ffa("sprint", "Sprint", 2, 16),
                        GameMode.ffa("elimination", "Elimination", 3, 16)),
                ParkourGame::new,
                List.of(spawns(1, "at the start line"),
                        regions("checkpoint", 1, "course checkpoints, in order"),
                        region("finish", "the finish area"),
                        setting("void-y", "fall level that returns you to your checkpoint")),
                false));

        registry.register(new GameType("elytra", "Elytra Racing",
                "Rocket-boosted gliding through checkpoint rings.", Material.ELYTRA,
                List.of(GameMode.ffa("sprint", "Sprint", 2, 12),
                        GameMode.ffa("grandprix", "Grand Prix", 2, 12)),
                ElytraRacingGame::new,
                List.of(spawns(1, "at the launch platform (high up!)"),
                        regions("checkpoint", 1, "big ring/gate volumes, in order"),
                        region("finish", "the finish gate"),
                        setting("void-y", "crash level that resets you")),
                false));

        registry.register(new GameType("karts", "PulseKarts",
                "Custom kart racing with shells, turbo and lightning!", Material.MINECART,
                List.of(GameMode.ffa("sprint", "Sprint", 2, 12),
                        GameMode.ffa("cup", "Cup (3 laps)", 2, 12)),
                PulseKartsGame::new,
                List.of(spawns(2, "clear kart grid slots, at least two blocks apart, facing along the track"),
                        regions("checkpoint", 1, "track checkpoints, in order"),
                        region("finish", "the finish line (drive through it each lap)"),
                        regions("itembox", 1, "item box pickup zones on the track"),
                        optional(location("checkpoint-spawn1", "safe kart reset with heading; repeat for each checkpoint")),
                        setting("void-y", "off-track fall level")),
                false));

        registry.register(new GameType("skywars", "Skywars",
                "Loot your island, bridge out, fight to the last!", Material.GRASS_BLOCK,
                List.of(GameMode.ffa("solo", "Solo", 2, 12),
                        GameMode.teams("doubles", "Doubles", 4, 16, 2),
                        GameMode.ffa("insane", "Insane", 2, 12)),
                SkywarsGame::new,
                List.of(spawns(4, "one per island (team games: spawn N = team N)")),
                false));

        registry.register(new GameType("bedwars", "Bedwars",
                "Defend your bed, destroy theirs!", Material.RED_BED,
                List.of(GameMode.teams("duos", "Duos", 4, 8, 2),
                        GameMode.teams("quads", "Quads", 8, 16, 4)),
                BedwarsGame::new,
                List.of(spawns(2, "one per team base (spawn N = team N)"),
                        regions("bed", 2, "one region around each team's bed (bedN = team N)"),
                        location("generator1", "team 1 resource generator (repeat per team)"),
                        optional(setting("diamond-generators", "set with /pulse setup addloc diamond-generators"))),
                false));

        registry.register(new GameType("buildbattle", "Build Battle",
                "Build to the theme, then vote for the best!", Material.CRAFTING_TABLE,
                List.of(GameMode.ffa("classic", "Classic", 2, 8),
                        GameMode.ffa("speed", "Speed", 2, 8)),
                BuildBattleGame::new,
                List.of(spawns(1, "anywhere (players are moved to plots)"),
                        regions("plot", 2, "one build plot per player")),
                false));

        registry.register(new GameType("deathrun", "Death Run",
                "Runners race the course; Deaths trigger the traps!", Material.SKELETON_SKULL,
                List.of(GameMode.ffa("classic", "Classic", 3, 16),
                        GameMode.ffa("doubletrouble", "Double Trouble", 4, 16)),
                DeathRunGame::new,
                List.of(spawns(1, "runner start line"),
                        regions("trap", 1, "blocks that vanish when triggered (trapN)"),
                        regions("trigger", 1, "what the Death clicks to fire trapN (triggerN)"),
                        region("finish", "the runners' goal"),
                        location("death-spawn", "where the Death(s) start")),
                false));

        registry.register(new GameType("villagerdefense", "Villager Defense",
                "Protect the Mayor from waves of zombies!", Material.EMERALD,
                List.of(GameMode.ffa("easy", "Easy", 1, 8),
                        GameMode.ffa("hard", "Hard", 2, 8)),
                VillagerDefenseGame::new,
                List.of(spawns(1, "the defenders' spawn"),
                        location("villager", "where the Mayor stands"),
                        regions("mobspawn", 1, "zombie spawn areas around the village")),
                false));

        registry.register(new GameType("readyplayerz", "ReadyPlayerZ",
                "Round-based zombie survival - points, perks, teamwork!", Material.ZOMBIE_HEAD,
                List.of(GameMode.ffa("classic", "Classic", 1, 8),
                        GameMode.ffa("frenzy", "Frenzy", 1, 8)),
                ReadyPlayerZGame::new,
                List.of(spawns(1, "the squad spawn room"),
                        regions("mobspawn", 1, "zombie entry points (windows, doors...)")),
                false));

        registry.register(new GameType("luckypillars", "Lucky Pillars",
                "Random lucky drops - bridge and brawl!", Material.SPONGE,
                List.of(GameMode.ffa("classic", "Classic", 2, 12),
                        GameMode.ffa("chaos", "Chaos", 2, 12)),
                LuckyPillarsGame::new,
                List.of(spawns(2, "one per pillar top"),
                        setting("void-y", "fall level between the pillars")),
                false));

        registry.register(new GameType("prophunt", "Prop Hunt",
                "Hide as a block... or hunt them all down!", Material.HAY_BLOCK,
                List.of(GameMode.ffa("classic", "Classic", 2, 16),
                        GameMode.ffa("infection", "Infection", 2, 16)),
                PropHuntGame::new,
                List.of(spawns(1, "spawn 1 = seeker release point; add more for hiders"),
                        optional(setting("decoy-limit", "decoys per hider: 1-3 (default 3)")),
                        optional(setting("decoy-seconds", "decoy lifetime: 10-120 seconds (default 60)")),
                        optional(setting("decoy-cooldown", "decoy cooldown: 2-60 seconds (default 12)")),
                        optional(setting("sprint-cooldown", "role sprint cooldown: 5-120 seconds (default 25)")),
                        optional(setting("pulse-cooldown", "seeker pulse cooldown: 5-120 seconds (default 25)")),
                        optional(setting("pulse-radius", "seeker pulse radius: 4-24 blocks (default 12)"))),
                false));

        registry.register(new GameType("supermarket", "Supermarket Sweep",
                "Grab your shopping list and race to checkout!", Material.CHEST_MINECART,
                List.of(GameMode.ffa("classic", "Classic", 2, 12),
                        GameMode.ffa("rush", "Rush", 2, 12)),
                SupermarketSweepGame::new,
                List.of(spawns(1, "at the shop entrance"),
                        region("checkout", "the checkout zone"),
                        optional(setting("item-pool", "custom item pool (list in the arena file)"))),
                false));

        registry.register(new GameType("partygames", "Party Games",
                "Quick-fire mini rounds - most points wins!", Material.FIREWORK_ROCKET,
                List.of(GameMode.ffa("classic", "Classic", 2, 16)),
                PartyGamesGame::new,
                List.of(spawns(4, "the central hub spawns"),
                        region("floor", "Color Rush dance floor"),
                        region("sumo", "Sumo platform"),
                        region("finish", "Sprint finish area"),
                        setting("void-y", "fall level below the mini-game areas")),
                false));
    }
}
