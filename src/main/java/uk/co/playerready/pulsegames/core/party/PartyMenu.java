package uk.co.playerready.pulsegames.core.party;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Party menu: member heads (leader first), Invite, Leave / Disband and the party's queue state.
 * Every action runs the player's own {@code /party ...} command, so the proxy-aware routing in
 * {@code Commands#party} (and {@code networkPartyCommand}) is exactly what a typed command gets.
 */
public final class PartyMenu {
    static final int QUEUE_SLOT = 4;
    static final int INVITE_SLOT = 20;
    static final int INVITES_SLOT = 22;
    static final int LEAVE_SLOT = 24;
    static final int PRIVATE_SLOT = 31;
    static final int PER_PAGE = 28;

    private final PulseGamesPlugin plugin;

    public PartyMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }

    static int memberSlot(int index) { return 9 + index; }
    static int contentSlot(int index) { return 10 + (index / 7) * 9 + index % 7; }

    public void open(Player player) {
        Menu menu = new Menu(plugin, player, Theme.title("Party"), 4);
        menu.renderer(() -> render(menu, player));
        menu.open(player);
    }

    /** Runs the player's own party command next tick, then redraws once any network reply has had time to land. */
    private void run(Player player, Menu menu, String command) {
        player.performCommand("party " + command);
        if (menu != null) plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) menu.refresh();
        }, 4L);
    }

    private void render(Menu menu, Player player) {
        Party party = plugin.parties().partyOf(player);
        boolean leader = party == null || party.isLeader(player);
        menu.set(QUEUE_SLOT, Buttons.item(Material.CLOCK, Theme.ACCENT + "What is the party doing?",
                Lore.of().desc(queue(party)).blank().desc("Your leader picks the game and everyone joins together.")));

        if (party != null) {
            List<PartyView.Person> people = new ArrayList<>();
            Map<UUID, Player> online = new java.util.HashMap<>();
            for (Player member : party.onlineMembers()) {
                people.add(new PartyView.Person(member.getUniqueId(), member.getName()));
                online.put(member.getUniqueId(), member);
            }
            int i = 0;
            for (PartyView.Person person : PartyView.orderMembers(party.leader(), people)) {
                boolean isLeader = person.id().equals(party.leader());
                var head = Buttons.head(online.get(person.id()), (isLeader ? Theme.MONEY + "★ " : Theme.NAME) + person.name(),
                        Lore.of().status(isLeader ? Theme.MONEY + "Party leader" : Theme.TEXT + "Member").build());
                menu.set(memberSlot(i++), isLeader ? Buttons.glow(head) : head);
            }
        } else {
            menu.set(memberSlot(0), Buttons.item(Material.PAPER, Theme.TEXT + "No party yet",
                    Lore.of().desc("Invite a friend to start one. You will be the leader.")));
        }

        if (leader) {
            boolean full = party != null && party.size() >= plugin.parties().maxSize();
            menu.set(INVITE_SLOT, Buttons.item(full ? Material.GRAY_DYE : Material.LIME_DYE,
                            (full ? Theme.TEXT : Theme.GOOD) + "<b>Invite a friend</b>",
                            full ? Lore.of().desc("Your party is full.") : Lore.of().desc("Pick someone who is in the lobby.").blank().hint("Click to choose")),
                    full ? null : p -> new InvitePicker(menu, 0).open(p));
        }
        if (leader && plugin.privateMatches().enabled()) {
            menu.set(PRIVATE_SLOT, Buttons.glow(Buttons.item(Material.ENDER_CHEST, Theme.ACCENT + "<b>Private match</b>",
                    Lore.of().desc("Pick a game and map and play with just your party. Bots can fill in.").blank()
                            .desc("Private matches pay tokens only when most players are people.").blank().hint("Click to set one up"))),
                    p -> plugin.privateMatches().open(p));
        }
        boolean on = plugin.parties().acceptsInvites(player);
        menu.set(INVITES_SLOT, Buttons.item(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE, Theme.NAME + "Party invites",
                Lore.of().status(on ? Theme.GOOD + "On" : Theme.BAD + "Off").desc("Whether friends can invite you.").blank().hint("Click to switch")),
                p -> run(p, menu, "invites " + (plugin.parties().acceptsInvites(p) ? "off" : "on")));
        if (party != null) {
            if (party.isLeader(player))
                menu.set(LEAVE_SLOT, Buttons.item(Material.TNT, Theme.BAD + "<b>Disband the party</b>",
                        Lore.of().desc("Ends the party for everyone.").blank().hint("Click to disband")), p -> run(p, menu, "disband"));
            else
                menu.set(LEAVE_SLOT, Buttons.item(Material.OAK_DOOR, Theme.BAD + "<b>Leave the party</b>",
                        Lore.of().blank().hint("Click to leave")), p -> run(p, menu, "leave"));
        }
        menu.back(p -> plugin.gameMenu().openGames(p));
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private String queue(Party party) {
        if (party == null) return PartyView.queueLine(null, null);
        Player lead = plugin.getServer().getPlayer(party.leader());
        var instance = lead == null ? null : plugin.instances().byPlayer(lead);
        return instance == null ? PartyView.queueLine(null, null)
                : PartyView.queueLine(instance.type().displayName(), instance.state());
    }

    /** Paged list of players in this lobby who can be invited right now. */
    private final class InvitePicker {
        private final Menu parent;
        private final int[] page;

        InvitePicker(Menu parent, int page) { this.parent = parent; this.page = new int[]{page}; }

        void open(Player player) {
            Menu menu = new Menu(plugin, player, Theme.title("Invite a friend"), 6);
            menu.renderer(() -> render(menu, player));
            menu.open(player);
        }

        private List<Player> candidates(Player self) {
            Party own = plugin.parties().partyOf(self);
            List<Player> result = new ArrayList<>();
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (other.equals(self) || other.hasMetadata("NPC")) continue;
                if (!plugin.lobby().isLobbyWorld(other.getWorld())) continue;
                if (plugin.parties().partyOf(other) != null || !plugin.parties().acceptsInvites(other)) continue;
                if (own != null && own.contains(other.getUniqueId())) continue;
                result.add(other);
            }
            result.sort(java.util.Comparator.comparing(p -> p.getName().toLowerCase(java.util.Locale.ROOT)));
            return result;
        }

        private void render(Menu menu, Player player) {
            var all = candidates(player);
            var shown = Paginator.page(all, PER_PAGE, page[0]);
            page[0] = shown.page();
            for (int i = 0; i < shown.items().size(); i++) {
                Player target = shown.items().get(i);
                menu.set(contentSlot(i), Buttons.head(target, Theme.NAME + target.getName(), Lore.of().hint("Click to invite").build()),
                        p -> { run(p, null, "invite " + target.getName()); new PartyMenu(plugin).open(p); });
            }
            if (all.isEmpty())
                menu.set(22, Buttons.item(Material.PAPER, Theme.TEXT + "Nobody to invite right now",
                        Lore.of().desc("Friends who are in the lobby and taking invites show up here.")));
            menu.pager(shown, () -> { page[0]--; menu.refresh(); }, () -> { page[0]++; menu.refresh(); });
            menu.back(p -> new PartyMenu(plugin).open(p));
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        }
    }
}
