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

    private final Map<Integer, Boolean> bedAlive = new HashMap<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    private List<Cuboid> bedRegions = List.of();

    public BedwarsGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        bedRegions = game.arena().regionsByPrefix("bed");
        for (GameTeam team : game.teams()) bedAlive.put(team.index(), true);
        game.alivePlayers().forEach(this::equipBase);
        startGenerators();
        game.broadcast("Protect your bed! If it's destroyed you can't respawn.");
    }

    private void equipBase(Player player) {
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Item Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(8, shop);
    }

    private void startGenerators() {
        game.runRepeating(40L, 40L, () -> dropAtTeamGenerators(Material.IRON_INGOT));
        game.runRepeating(160L, 160L, () -> dropAtTeamGenerators(Material.GOLD_INGOT));
        game.runRepeating(600L, 600L, () -> {
            for (String raw : game.arena().settings().getStringList("diamond-generators")) {
                drop(uk.co.playerready.pulsegames.core.util.LocUtil.parse(raw, game.world()), Material.DIAMOND);
            }
        });
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

    private void destroyBed(int teamIndex, Player breaker) {
        if (!bedAlive.getOrDefault(teamIndex, false)) return;
        bedAlive.put(teamIndex, false);
        GameTeam victimTeam = game.teams().stream().filter(t -> t.index() == teamIndex).findFirst().orElse(null);
        String teamName = victimTeam != null ? victimTeam.coloredName() : "A team";
        game.broadcast("<red><b>BED DESTROYED!</b></red> " + teamName + "'s</gray> bed was broken by <yellow>"
                + breaker.getName() + "</yellow>!");
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
        }
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
        player.openInventory(inv);
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0 || slot >= SHOP.size()) return;
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
