package uk.co.playerready.pulsegames.games.bedwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bedwars: defend your bed, break theirs. No bed = no respawn.
 * Modes: duos, quads.
 *
 * Arena requirements: one spawn per team, regions bed1..bedN (one per team),
 * settings generator1..generatorN ("x,y,z" per team) and diamond-generators (list).
 */
public final class BedwarsGame extends MiniGame {

    private static final class ShopHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record ShopItem(ItemStack product, Material currency, int cost) {}

    private static final List<ShopItem> SHOP = List.of(
            new ShopItem(new ItemStack(Material.WHITE_WOOL, 16), Material.IRON_INGOT, 4),
            new ShopItem(new ItemStack(Material.OAK_PLANKS, 16), Material.GOLD_INGOT, 4),
            new ShopItem(new ItemStack(Material.END_STONE, 12), Material.IRON_INGOT, 24),
            new ShopItem(new ItemStack(Material.STONE_SWORD), Material.IRON_INGOT, 10),
            new ShopItem(new ItemStack(Material.IRON_SWORD), Material.GOLD_INGOT, 7),
            new ShopItem(new ItemStack(Material.DIAMOND_SWORD), Material.DIAMOND, 4),
            new ShopItem(new ItemStack(Material.CHAINMAIL_CHESTPLATE), Material.IRON_INGOT, 30),
            new ShopItem(new ItemStack(Material.IRON_CHESTPLATE), Material.GOLD_INGOT, 12),
            new ShopItem(new ItemStack(Material.BOW), Material.GOLD_INGOT, 12),
            new ShopItem(new ItemStack(Material.ARROW, 6), Material.GOLD_INGOT, 2),
            new ShopItem(new ItemStack(Material.GOLDEN_APPLE), Material.GOLD_INGOT, 3),
            new ShopItem(new ItemStack(Material.SHEARS), Material.IRON_INGOT, 20),
            new ShopItem(new ItemStack(Material.IRON_PICKAXE), Material.IRON_INGOT, 25),
            new ShopItem(new ItemStack(Material.TNT), Material.GOLD_INGOT, 8));

    private static final org.bukkit.Color[] TEAM_COLORS = {
            org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.LIME, org.bukkit.Color.YELLOW,
            org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.WHITE, org.bukkit.Color.GRAY
    };

    private final Map<Integer, Boolean> bedAlive = new HashMap<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    private final Set<Integer> sharpnessTeams = new HashSet<>();
    private final Set<Integer> protectionTeams = new HashSet<>();
    private final Set<Integer> hasteTeams = new HashSet<>();
    private List<Cuboid> bedRegions = List.of();
    private int generatorTier = 1;

    public BedwarsGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        bedRegions = game.arena().regionsByPrefix("bed");
        for (GameTeam team : game.teams()) bedAlive.put(team.index(), true);
        game.alivePlayers().forEach(this::equipBase);
        game.broadcast("Protect your bed! If it's destroyed you can't respawn.");
        game.broadcast("<gray>Generators upgrade at <yellow>5:00</yellow> and <yellow>10:00</yellow>!");
    }

    private void equipBase(Player player) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        if (sharpnessTeams.contains(teamIndex)) {
            sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
        }
        player.getInventory().setItem(0, sword);
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Item Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(8, shop);
        // Team-colored leather armor.
        org.bukkit.Color color = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
        player.getInventory().setChestplate(dyed(Material.LEATHER_CHESTPLATE, color, protectionTeams.contains(teamIndex)));
        player.getInventory().setLeggings(dyed(Material.LEATHER_LEGGINGS, color, protectionTeams.contains(teamIndex)));
        player.getInventory().setBoots(dyed(Material.LEATHER_BOOTS, color, protectionTeams.contains(teamIndex)));
        if (hasteTeams.contains(teamIndex)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                    0, false, false));
        }
    }

    /** Applies owned team upgrades to a player's current gear in place. */
    private void applyUpgrades(Player player, int teamIndex) {
        if (sharpnessTeams.contains(teamIndex)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType().name().endsWith("_SWORD")) {
                    item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
                }
            }
        }
        if (protectionTeams.contains(teamIndex)) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null) {
                    armor.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
                }
            }
        }
        if (hasteTeams.contains(teamIndex)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                    0, false, false));
        }
    }

    private ItemStack dyed(Material material, org.bukkit.Color color, boolean protect) {
        ItemStack item = new ItemStack(material);
        item.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class, meta -> meta.setColor(color));
        if (protect) item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
        return item;
    }

    /** Tiered resource generators driven by game time. */
    @Override
    public void onSecond(int gameTime) {
        if (gameTime == 300 || gameTime == 600) {
            generatorTier = gameTime == 300 ? 2 : 3;
            game.broadcast("<aqua><b>GENERATORS UPGRADED</b></aqua> <gray>to Tier " + generatorTier + "!");
        }
        int ironEvery = generatorTier >= 2 ? 1 : 2;
        int goldEvery = generatorTier >= 3 ? 3 : generatorTier == 2 ? 5 : 8;
        int diamondEvery = generatorTier >= 3 ? 20 : generatorTier == 2 ? 25 : 30;
        if (gameTime % ironEvery == 0) dropAtTeamGenerators(Material.IRON_INGOT);
        if (gameTime % goldEvery == 0) dropAtTeamGenerators(Material.GOLD_INGOT);
        if (gameTime % diamondEvery == 0) {
            for (String raw : game.arena().settings().getStringList("diamond-generators")) {
                Location loc = uk.co.playerready.pulsegames.core.util.LocUtil.parse(raw, game.world());
                drop(loc, Material.DIAMOND);
                game.world().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 5, 0.3, 0.5, 0.3);
            }
        }
    }

    private void dropAtTeamGenerators(Material material) {
        for (GameTeam team : game.aliveTeams()) {
            Location gen = game.arena().settingLocation("generator" + (team.index() + 1), game.world());
            if (gen != null) drop(gen, material);
        }
    }

    private void drop(Location location, Material material) {
        var item = game.world().dropItem(location.clone().add(0, 0.5, 0), new ItemStack(material));
        item.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
    }

    // ---- beds -----------------------------------------------------------------

    @Override
    public boolean canBreak(Player player, Block block) {
        if (block.getType().name().endsWith("_BED")) {
            int bedTeam = bedTeamIndex(block.getLocation());
            GameTeam team = game.teamOf(player);
            if (bedTeam < 0) return false;
            if (team != null && team.index() == bedTeam) {
                player.sendMessage(Text.msg("<red>You can't break your own bed!"));
                return false;
            }
            destroyBed(bedTeam, player);
            return true;
        }
        return playerBlocks.contains(block.getLocation());
    }

    private int bedTeamIndex(Location location) {
        for (int i = 0; i < bedRegions.size(); i++) {
            if (bedRegions.get(i).contains(location)) return i;
        }
        return -1;
    }

    /** @param breaker null when an explosion took the bed rather than a player's pickaxe. */
    private void destroyBed(int teamIndex, Player breaker) {
        if (!bedAlive.getOrDefault(teamIndex, false)) return;
        bedAlive.put(teamIndex, false);
        GameTeam victimTeam = game.teams().stream().filter(t -> t.index() == teamIndex).findFirst().orElse(null);
        String teamName = victimTeam != null ? victimTeam.coloredName() : "A team";
        String culprit = breaker != null ? "<yellow>" + breaker.getName() + "</yellow>" : "<gray>an explosion</gray>";
        game.broadcast("<red><b>BED DESTROYED!</b></red> " + teamName + "'s</gray> bed was broken by "
                + culprit + "!");
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
        }
    }

    /**
     * TNT is a shop item, so beds do get blown up. Route those through destroyBed or the
     * team keeps respawning off a bed that is no longer there.
     */
    @Override
    public boolean canExplode(Block block) {
        if (block.getType().name().endsWith("_BED")) {
            int bedTeam = bedTeamIndex(block.getLocation());
            if (bedTeam < 0) return false;
            destroyBed(bedTeam, null);
            return true;
        }
        return playerBlocks.contains(block.getLocation());
    }

    /** One bed region per team: a map with fewer beds than teams can never end. */
    @Override
    public int maxTeams() {
        return game.arena().regionsByPrefix("bed").size();
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        playerBlocks.add(block.getLocation());
        return true;
    }

    // ---- respawning ------------------------------------------------------------

    @Override
    public void onDeath(Player victim, Player killer) {
        if (killer != null) {
            game.broadcast("<red>" + victim.getName() + "</red> was killed by <yellow>" + killer.getName() + "</yellow>");
            game.stats().addKill(killer, game.type().id());
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        GameTeam team = game.teamOf(victim);
        boolean canRespawn = team != null && bedAlive.getOrDefault(team.index(), false);
        if (canRespawn) {
            game.respawn(victim, 5);
        } else {
            victim.sendMessage(Text.msg("<red>Your bed is gone - you're out!"));
            game.eliminate(victim);
        }
    }

    @Override
    public void onRespawn(Player player) {
        equipBase(player);
    }

    // ---- shop --------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openShop(event.getPlayer());
        }
    }

    private record TeamUpgrade(String name, Material icon, int diamondCost, Set<Integer> owners) {}

    private List<TeamUpgrade> teamUpgrades() {
        return List.of(
                new TeamUpgrade("Sharpened Swords (team)", Material.DIAMOND_SWORD, 8, sharpnessTeams),
                new TeamUpgrade("Reinforced Armor (team)", Material.DIAMOND_CHESTPLATE, 8, protectionTeams),
                new TeamUpgrade("Maniac Miner (team haste)", Material.GOLDEN_PICKAXE, 4, hasteTeams));
    }

    private void openShop(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Item Shop"));
        holder.inventory = inv;
        for (int i = 0; i < SHOP.size(); i++) {
            ShopItem entry = SHOP.get(i);
            ItemStack display = entry.product().clone();
            display.editMeta(meta -> meta.lore(List.of(
                    (Component) Text.mm("<gray>Cost: <yellow>" + entry.cost() + " "
                            + entry.currency().name().toLowerCase().replace('_', ' ')))));
            inv.setItem(i, display);
        }
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        List<TeamUpgrade> upgrades = teamUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            TeamUpgrade upgrade = upgrades.get(i);
            boolean owned = upgrade.owners().contains(teamIndex);
            ItemStack display = new ItemStack(owned ? Material.LIME_DYE : upgrade.icon());
            display.editMeta(meta -> {
                meta.displayName(Text.mm((owned ? "<green>" : "<aqua>") + upgrade.name()));
                meta.lore(List.of((Component) Text.mm(owned ? "<green>Purchased!"
                        : "<gray>Cost: <aqua>" + upgrade.diamondCost() + " diamonds")));
            });
            inv.setItem(18 + i, display);
        }
        player.openInventory(inv);
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0) return;
        if (slot >= 18 && slot < 18 + teamUpgrades().size()) {
            buyTeamUpgrade(player, teamUpgrades().get(slot - 18));
            return;
        }
        if (slot >= SHOP.size()) return;
        ShopItem entry = SHOP.get(slot);
        if (!player.getInventory().containsAtLeast(new ItemStack(entry.currency()), entry.cost())) {
            player.sendMessage(Text.msg("<red>You can't afford that."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        player.getInventory().removeItem(new ItemStack(entry.currency(), entry.cost()));
        ItemStack product = entry.product().clone();
        if (product.getType() == Material.WHITE_WOOL) {
            GameTeam team = game.teamOf(player);
            if (team != null) product = new ItemStack(team.palette().wool(), product.getAmount());
        }
        player.getInventory().addItem(product);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void buyTeamUpgrade(Player player, TeamUpgrade upgrade) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        if (upgrade.owners().contains(teamIndex)) {
            player.sendMessage(Text.msg("<red>Your team already has that upgrade."));
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), upgrade.diamondCost())) {
            player.sendMessage(Text.msg("<red>You need <aqua>" + upgrade.diamondCost() + " diamonds</aqua> for that."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, upgrade.diamondCost()));
        upgrade.owners().add(teamIndex);
        // Apply to every online teammate immediately (without resetting their gear).
        if (team != null) {
            for (java.util.UUID member : team.members()) {
                Player teammate = Bukkit.getPlayer(member);
                if (teammate != null && game.isAlive(teammate)) applyUpgrades(teammate, teamIndex);
            }
        }
        game.broadcast(team != null
                ? team.coloredName() + "</gray> unlocked <aqua>" + upgrade.name() + "</aqua>!"
                : "<aqua>" + upgrade.name() + "</aqua> unlocked!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
        // Re-opening from inside InventoryClickEvent leaves the client with a ghost window.
        game.runLater(1L, () -> {
            if (player.isOnline() && game.isAlive(player)) openShop(player);
        });
    }

    // ---- rules ----------------------------------------------------------------------

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        for (GameTeam team : game.teams()) {
            boolean alive = team.members().stream().map(Bukkit::getPlayer)
                    .anyMatch(p -> p != null && game.isAlive(p));
            String bed = bedAlive.getOrDefault(team.index(), false) ? "<green>✔" : (alive ? "<red>✘" : "<dark_gray>✖");
            lines.add(team.coloredName() + "<gray>: " + bed);
        }
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) { return true; }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 1800);
    }
}
