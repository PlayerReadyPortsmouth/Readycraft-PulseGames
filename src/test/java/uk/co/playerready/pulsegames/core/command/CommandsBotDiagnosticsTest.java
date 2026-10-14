package uk.co.playerready.pulsegames.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.bot.BotDiagnostics;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandsBotDiagnosticsTest {
    @Test void inspectionAndTabNamesRequireAdminPermissionEvenForNonPlayerSenders() {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var bots = plugin.bots();
        var commands = new Commands(plugin);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);
        when(command.getName()).thenReturn("pulse");
        commands.onCommand(sender, command, "pulse", new String[]{"bots", "inspect"});
        assertEquals(List.of(), commands.onTabComplete(sender, command, "pulse", new String[]{"bots", "inspect", ""}));
        verify(bots, never()).diagnostics();
    }

    @Test void adminCanInspectAndCompleteLiveNamesAndUuid() {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var bots = plugin.bots();
        UUID id = UUID.randomUUID();
        when(bots.diagnostics()).thenReturn(List.of(new BotDiagnostics.Snapshot(id, "Ready1", "duels:match",
                "Fight", BotDifficulty.NORMAL, null, null, "Waiting", null, 0)));
        var commands = new Commands(plugin);
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("pulsegames.admin")).thenReturn(true);
        var command = mock(Command.class);
        when(command.getName()).thenReturn("pulse");
        assertEquals(List.of("inspect"), commands.onTabComplete(sender, command, "pulse", new String[]{"bots", "in"}));
        assertEquals(List.of("Ready1", id.toString()), commands.onTabComplete(sender, command, "pulse", new String[]{"bots", "inspect", ""}));
        commands.onCommand(sender, command, "pulse", new String[]{"bots", "inspect", "Ready1"});
        verify(sender, times(7)).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
