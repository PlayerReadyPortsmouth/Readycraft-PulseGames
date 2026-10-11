package uk.co.playerready.pulsegames.games;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.games.partygames.PartyGamesGame;

import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PartyGamesCombatTest {
    @Test void eliminatedSumoPlayerCannotAttackOrBeAttacked() throws Exception {
        GameInstance instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        Player a = player(), b = player(), eliminated = player();
        when(instance.alivePlayers()).thenReturn(List.of(a, b, eliminated));
        PartyGamesGame logic = new PartyGamesGame(instance);
        // Seed a running round without Paper's sound registry/server bootstrap.
        var phase = PartyGamesGame.class.getDeclaredField("current");
        phase.setAccessible(true);
        for (Object value : phase.getType().getEnumConstants()) {
            if (value.toString().equals("SUMO")) phase.set(logic, value);
        }
        var roster = PartyGamesGame.class.getDeclaredField("roundAlive");
        roster.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<UUID> active = (java.util.Set<UUID>) roster.get(logic);
        active.addAll(List.of(a.getUniqueId(), b.getUniqueId(), eliminated.getUniqueId()));
        logic.onDeath(eliminated, a);
        EntityDamageByEntityEvent illegalAttack = mock(EntityDamageByEntityEvent.class);
        logic.onDamageByPlayer(a, eliminated, illegalAttack);
        verify(illegalAttack).setCancelled(true);
        EntityDamageByEntityEvent illegalVictim = mock(EntityDamageByEntityEvent.class);
        logic.onDamageByPlayer(eliminated, a, illegalVictim);
        verify(illegalVictim).setCancelled(true);
        EntityDamageByEntityEvent valid = mock(EntityDamageByEntityEvent.class);
        logic.onDamageByPlayer(a, b, valid);
        verify(valid).setDamage(0);
        verify(valid, never()).setCancelled(true);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
