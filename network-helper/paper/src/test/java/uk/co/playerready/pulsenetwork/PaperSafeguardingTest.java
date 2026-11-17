package uk.co.playerready.pulsenetwork;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaperSafeguardingTest {
    @TempDir Path directory;
    JavaPlugin plugin; Server server; Player player, target, staff; YamlConfiguration yaml; PaperSafeguarding safety;
    @BeforeEach void setup() {
        plugin = mock(JavaPlugin.class); server = mock(Server.class); player = player(); target = player(); staff = player();
        when(plugin.getServer()).thenReturn(server); when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.isPrimaryThread()).thenReturn(true); doReturn(List.of(player, target, staff)).when(server).getOnlinePlayers(); when(staff.hasPermission(PaperSafeguarding.REVIEW)).thenReturn(true);
        yaml = new YamlConfiguration(); yaml.set("safeguarding.environment", "test"); yaml.set("safeguarding.backend", "minigamestest"); yaml.set("safeguarding.test-backends", List.of("minigamestest"));
        yaml.set("safeguarding.text-commands", List.of("me", "say", "party")); when(plugin.getConfig()).thenReturn(yaml); safety = new PaperSafeguarding(plugin);
    }
    Player player() { Player p = mock(Player.class); when(p.getUniqueId()).thenReturn(UUID.randomUUID()); when(p.getServer()).thenReturn(server); return p; }
    @AfterEach void close() { safety.close(); }
    String journal() throws Exception { return Files.readString(directory.resolve("safeguarding").resolve(LocalDate.now(ZoneOffset.UTC) + ".tsv")); }
    Command command(String name) { Command c = mock(Command.class); when(c.getName()).thenReturn(name); return c; }
    @Test void localChatIsBlockedBeforeDeliveryAndChangedFinalTextIsRechecked() throws Exception {
        var event = mock(AsyncChatEvent.class); when(event.getPlayer()).thenReturn(player); when(event.message()).thenReturn(Component.text("hello"));
        safety.chat(event); safety.finalChat(event); verify(event, never()).setCancelled(true);
        when(event.message()).thenReturn(Component.text("test@example.org")); safety.finalChat(event);
        verify(event).setCancelled(true); assertFalse(journal().contains("test@example.org")); assertTrue(journal().contains("withheld"));
    }
    @Test void signBookAnvilAndFormAllUseTheSameGate() throws Exception {
        var sign = mock(SignChangeEvent.class); when(sign.getPlayer()).thenReturn(player); when(sign.getLines()).thenReturn(new String[]{"add me on discord", "", "", ""}); safety.sign(sign); verify(sign).setCancelled(true);
        var book = mock(PlayerEditBookEvent.class); var meta = mock(BookMeta.class); when(book.getPlayer()).thenReturn(player); when(book.getNewBookMeta()).thenReturn(meta); when(meta.getPages()).thenReturn(List.of("send me nudes")); safety.book(book); verify(book).setCancelled(true);
        var anvil = mock(InventoryClickEvent.class); var view = mock(AnvilView.class); when(anvil.getView()).thenReturn(view); when(anvil.getWhoClicked()).thenReturn(player); when(anvil.getRawSlot()).thenReturn(2); when(view.getRenameText()).thenReturn("07700.900.123"); safety.anvil(anvil); verify(anvil).setCancelled(true);
        assertFalse(safety.check(player, "form", "test@example.org")); assertFalse(journal().contains("07700.900.123"));
    }
    @Test void namespacedPrivateMessagesAreClosedAndCredentialsAreNeverRetained() throws Exception {
        var dm = new PlayerCommandPreprocessEvent(player, "/essentials:msg Friend hello"); safety.command(dm); assertTrue(dm.isCancelled());
        var login = new PlayerCommandPreprocessEvent(player, "/authme:login not-a-real-password"); safety.command(login); assertTrue(login.isCancelled());
        assertFalse(journal().contains("not-a-real-password")); assertTrue(journal().contains("arguments withheld"));
        assertTrue(journal().lines().anyMatch(line -> line.contains("dm:unknown") && line.contains("blocked")));
        var unknown = new PlayerCommandPreprocessEvent(player, "/unknownplugin:text something"); safety.command(unknown); assertTrue(unknown.isCancelled());
        assertTrue(journal().lines().anyMatch(line -> line.contains("command:text") && line.contains("blocked")));
    }
    @Test void knownMinorIsDeniedEvenWhenMutualFriendModeIsEnabled() {
        safety.close(); yaml.set("safeguarding.private.enabled", true); yaml.set("safeguarding.private.friend-pairs", List.of(player.getUniqueId() + ":" + target.getUniqueId())); yaml.set("safeguarding.under-18-players", List.of(player.getUniqueId().toString()));
        when(server.getPlayerExact("Friend")).thenReturn(target); safety = new PaperSafeguarding(plugin);
        var dm = new PlayerCommandPreprocessEvent(player, "/msg Friend hello"); safety.command(dm); assertTrue(dm.isCancelled());
    }
    @Test void mutePersistsAndReportsBypassMuteOnlyAfterConfirmation() throws Exception {
        safety.onCommand(staff, command("safetymute"), "safetymute", new String[]{player.getUniqueId().toString(), "0"});
        assertFalse(safety.check(player, "chat", "hello"));
        safety.close(); safety = new PaperSafeguarding(plugin); assertFalse(safety.check(player, "form", "another safe text"));
        safety.onCommand(player, command("safetyreport"), "safetyreport", new String[]{target.getUniqueId().toString(), "keep", "this", "a", "secret"});
        var messages = org.mockito.ArgumentCaptor.forClass(Component.class); verify(player, atLeastOnce()).sendMessage(messages.capture());
        String prompt = messages.getAllValues().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).filter(s -> s.contains("/safetyreport confirm ")).findFirst().orElseThrow();
        String token = prompt.substring(prompt.indexOf("/safetyreport confirm ") + "/safetyreport confirm ".length());
        assertFalse(journal().contains("player-report")); assertTrue(journal().contains("report-draft")); assertTrue(journal().contains("URGENT"));
        safety.onCommand(player, command("safetyreport"), "safetyreport", new String[]{"confirm", token});
        assertTrue(journal().contains("player-report")); assertTrue(journal().contains("URGENT"));
        assertTrue(journal().lines().anyMatch(line -> line.contains("another safe text") && line.contains("blocked")));
    }
    @Test void slowModeRateLimitAndPartyHoldsAreAuditedAsBlocked() throws Exception {
        safety.onCommand(staff, command("safetyslow"), "safetyslow", new String[]{"30"}); assertTrue(safety.check(player, "chat", "first")); assertFalse(safety.check(player, "chat", "second"));
        assertFalse(safety.check(target, "party", "private hello"));
        assertTrue(journal().lines().anyMatch(line -> line.contains("second") && line.contains("blocked")));
        assertTrue(journal().lines().anyMatch(line -> line.contains("private hello") && line.contains("blocked")));
    }
    @Test void permissionAndStorageFailuresFailClosedWhileUrgentAlertsSurvive() throws Exception {
        safety.check(player, "chat", "ordinary text"); clearInvocations(target);
        safety.onCommand(target, command("safetyflagged"), "safetyflagged", new String[0]);
        var output = org.mockito.ArgumentCaptor.forClass(Component.class); verify(target).sendMessage(output.capture()); assertTrue(PlainTextComponentSerializer.plainText().serialize(output.getValue()).contains("authorised staff"));
        Files.delete(directory.resolve("safeguarding").resolve(LocalDate.now(ZoneOffset.UTC) + ".tsv")); Files.createDirectory(directory.resolve("safeguarding").resolve(LocalDate.now(ZoneOffset.UTC) + ".tsv"));
        assertFalse(safety.check(player, "chat", "kill myself"));
        verify(staff, atLeastOnce()).sendMessage(any(Component.class));
        assertFalse(safety.check(target, "chat", "safe text"));
    }
    @Test void unexpectedEventFailureCancelsBeforeAnyLocalDelivery() {
        var event = mock(AsyncChatEvent.class); when(event.message()).thenThrow(new IllegalStateException("synthetic test failure"));
        safety.chat(event); verify(event).setCancelled(true);
    }
    @Test void onlyInventoriedCredentialOwnerMayReceiveWithheldArguments() throws Exception {
        safety.close(); yaml.set("safeguarding.credential-command-owners", List.of("AuthMe"));
        var owner = mock(org.bukkit.plugin.Plugin.class); when(owner.getName()).thenReturn("AuthMe");
        var login = mock(org.bukkit.command.PluginCommand.class); when(login.getPlugin()).thenReturn(owner); when(server.getPluginCommand("authme:login")).thenReturn(login);
        safety = new PaperSafeguarding(plugin);
        var event = new PlayerCommandPreprocessEvent(player, "/authme:login never-real-credential"); safety.command(event); assertFalse(event.isCancelled());
        assertFalse(journal().contains("never-real-credential")); assertTrue(journal().contains("credential arguments withheld"));
    }
    @Test void corruptPersistedControlsHoldTextUntilStaffRepair() throws Exception {
        var controls = directory.resolve("safeguarding/controls.properties");
        for (String corrupt : List.of("slow-ms=invalid", "slow-ms=-1", "slow-ms=300001",
                "mute.not-a-uuid=9223372036854775807", "mute." + player.getUniqueId() + "=invalid",
                "mute." + player.getUniqueId() + "=0", "mute." + player.getUniqueId() + "=-1")) {
            safety.close(); Files.writeString(controls, corrupt); safety = new PaperSafeguarding(plugin);
            assertTrue(safety.active()); assertFalse(safety.check(player, "chat", "otherwise safe text"), corrupt);
        }
    }
    @Test void essentialsAliasesAndExternalMailCannotBypassPrivateControls() throws Exception {
        for (String alias : List.of("emsg", "etell", "ewhisper", "epm", "er", "ereply")) {
            var event = new PlayerCommandPreprocessEvent(player, "/essentials:" + alias + " Friend hello"); safety.command(event); assertTrue(event.isCancelled(), alias);
        }
        var mail = new PlayerCommandPreprocessEvent(player, "/essentials:mail send Friend kill myself"); safety.command(mail); assertTrue(mail.isCancelled());
        var inbox = new PlayerCommandPreprocessEvent(player, "/mail"); safety.command(inbox); assertTrue(inbox.isCancelled());
        var otp = new PlayerCommandPreprocessEvent(player, "/authme:email synthetic-otp-739201"); safety.command(otp); assertTrue(otp.isCancelled());
        assertTrue(journal().contains("welfare")); assertFalse(journal().contains("synthetic-otp-739201")); assertTrue(journal().contains("private mail arguments withheld"));
        safety.close(); yaml.set("safeguarding.private.enabled", true); yaml.set("safeguarding.private.friend-pairs", List.of(player.getUniqueId() + ":" + target.getUniqueId()));
        when(server.getPlayerExact("Friend")).thenReturn(target); safety = new PaperSafeguarding(plugin);
        var reply = new PlayerCommandPreprocessEvent(player, "/ereply Friend hello"); safety.command(reply); assertTrue(reply.isCancelled());
    }
    @Test void productionProfileIsInert() {
        safety.close(); yaml.set("safeguarding.environment", "production"); safety = new PaperSafeguarding(plugin); assertFalse(safety.active()); assertTrue(safety.check(player, "chat", "test@example.org"));
    }
}
