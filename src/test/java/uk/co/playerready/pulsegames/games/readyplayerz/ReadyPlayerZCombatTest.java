package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadyPlayerZCombatTest {
    GameInstance game;ReadyPlayerZGame logic;World world;Player player;
    @BeforeEach void setup(){
        game=mock(GameInstance.class,RETURNS_DEEP_STUBS);world=mock(World.class);player=mock(Player.class);
        when(game.mode().id()).thenReturn("classic");when(world.getName()).thenReturn("pulse_test");
        when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());when(game.world()).thenReturn(world);when(game.state()).thenReturn(GameState.RUNNING);
        when(game.everyone()).thenReturn(List.of());when(game.alivePlayers()).thenReturn(List.of(player));when(game.isActive(player)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(new Location(world,20,64,0));
        logic=spy(new ReadyPlayerZGame(game));doNothing().when(logic).darken(any(),anyInt());doNothing().when(logic).clearDarkness(any());doReturn(null).when(logic).attackSpeed(any());
    }
    Object field(String name)throws Exception{var f=ReadyPlayerZGame.class.getDeclaredField(name);f.setAccessible(true);return f.get(logic);}
    void field(String name,Object value)throws Exception{var f=ReadyPlayerZGame.class.getDeclaredField(name);f.setAccessible(true);f.set(logic,value);}
    Zombie enemy(ZCombatBalance.Variant variant)throws Exception{
        Zombie mob=mock(Zombie.class);UUID id=UUID.randomUUID();when(mob.getUniqueId()).thenReturn(id);when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(new Location(world,0,64,0));when(world.getEntity(id)).thenReturn(mob);
        ((Set<UUID>)field("zombies")).add(id);((Map<UUID,ZCombatBalance.Variant>)field("variants")).put(id,variant);return mob;
    }
    @Test void aggroAcquiresPlayerThroughOcclusionAndExcludesDownedTargets()throws Exception{
        Zombie mob=enemy(ZCombatBalance.Variant.WALKER);field("roundActive",true);field("pendingSpawns",1);
        when(mob.hasLineOfSight(player)).thenReturn(false);
        logic.onSecond(1);verify(mob).setTarget(player);verify(mob,never()).hasLineOfSight(any(org.bukkit.entity.Entity.class));
        ((Set<UUID>)field("downed")).add(player.getUniqueId());logic.onSecond(2);verify(mob).setTarget(null);
    }
    @Test void nukeLeavesBossAliveAndSuppressesDeathRewardChains()throws Exception{
        Zombie regular=enemy(ZCombatBalance.Variant.WALKER),boss=enemy(ZCombatBalance.Variant.BOSS);when(boss.getHealth()).thenReturn(100d);
        doAnswer(invocation->{logic.onEntityDeath(regular,player);return null;}).when(regular).setHealth(0);
        var method=ReadyPlayerZGame.class.getDeclaredMethod("activatePowerup",Player.class,String.class);method.setAccessible(true);method.invoke(logic,player,"nuke");
        verify(boss).setHealth(50);verify(boss,never()).setHealth(0);verify(regular).setHealth(0);
        verify(game).addScore(player,50);verify(game,never()).addScore(player,10);verify(world,never()).dropItem(any(),any());
        assertEquals(Set.of(boss.getUniqueId()),field("zombies"));
    }
    @Test void repeatedDeathCannotAwardPointsTwice()throws Exception{
        Zombie mob=enemy(ZCombatBalance.Variant.WALKER);field("nextDropTick",Long.MAX_VALUE);
        logic.onEntityDeath(mob,player);logic.onEntityDeath(mob,player);
        verify(game,times(1)).addScore(player,10);assertTrue(((Map<?,?>)field("variants")).isEmpty());
    }
    @Test void cleanupRemovesOwnedEntitiesAndRestoresInstanceNightRules()throws Exception{
        Zombie mob=enemy(ZCombatBalance.Variant.BOSS);
        when(world.getTime()).thenReturn(6000L);when(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)).thenReturn(true);when(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE)).thenReturn(true);
        doNothing().when(logic).equipBase(any());logic.onStart();
        verify(world).setTime(18000);verify(world).setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);verify(world).setGameRule(GameRule.DO_WEATHER_CYCLE,false);
        logic.onCleanup();logic.onCleanup();
        verify(mob,times(1)).remove();verify(world,times(1)).setTime(6000);verify(world,times(1)).setGameRule(GameRule.DO_DAYLIGHT_CYCLE,true);
        verify(world,times(1)).setGameRule(GameRule.DO_WEATHER_CYCLE,true);assertTrue(((Set<?>)field("zombies")).isEmpty());
    }
    @Test void shopDragCannotInsertItemsIntoMenu()throws Exception{
        Class<?> holderType=Class.forName(ReadyPlayerZGame.class.getName()+"$ShopHolder");
        var constructor=holderType.getDeclaredConstructor();constructor.setAccessible(true);
        var holder=(org.bukkit.inventory.InventoryHolder)constructor.newInstance();
        var top=mock(org.bukkit.inventory.Inventory.class);when(top.getHolder()).thenReturn(holder);
        var view=mock(org.bukkit.inventory.InventoryView.class);when(view.getTopInventory()).thenReturn(top);
        var event=mock(org.bukkit.event.inventory.InventoryDragEvent.class);when(event.getView()).thenReturn(view);
        logic.onInventoryDrag(player,event);verify(event).setCancelled(true);
    }
    @Test void downedActivePlayerCannotPurchaseOrOpenShop()throws Exception{
        ((Set<UUID>)field("downed")).add(player.getUniqueId());when(game.score(player)).thenReturn(1000);
        assertFalse(logic.purchase(player,6));verify(game,never()).addScore(any(),anyInt());
        var event=mock(org.bukkit.event.player.PlayerInteractEvent.class);when(event.getPlayer()).thenReturn(player);
        logic.onInteract(event);verify(event).setCancelled(true);verify(player,never()).openInventory(any(org.bukkit.inventory.Inventory.class));
    }

    java.util.Map<Player,org.bukkit.attribute.AttributeInstance> speeds=new java.util.HashMap<>();
    void attackSpeed(Player p,double base){
        var attribute=mock(org.bukkit.attribute.AttributeInstance.class);var value=new double[]{base};
        when(attribute.getBaseValue()).thenAnswer(i->value[0]);
        doAnswer(i->{value[0]=i.getArgument(0);return null;}).when(attribute).setBaseValue(anyDouble());
        doReturn(attribute).when(logic).attackSpeed(p);speeds.put(p,attribute);
    }
    double speed(Player p){return speeds.get(p).getBaseValue();}
    @Test void legacyCombatSetsHighAttackSpeedOnStartAndRestoresOnEveryExitPath()throws Exception{
        doNothing().when(logic).equipBase(any());
        for(String path:List.of("death","quit","eliminated","cleanup","end")){
            Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());attackSpeed(p,4.0);
            when(game.alivePlayers()).thenReturn(List.of(p));when(game.everyone()).thenReturn(List.of(p));
            logic.onStart();
            assertEquals(1024,speed(p),path);
            switch(path){
                case "death"->logic.onDeath(p,null);
                case "quit"->logic.onQuit(p);
                case "eliminated"->logic.onEliminated(p);
                case "cleanup"->logic.onCleanup();
                default->logic.onEnd(List.of());
            }
            assertEquals(4.0,speed(p),path);
            logic.onCleanup();
            assertEquals(4.0,speed(p),path);
        }
    }
    @Test void legacyCombatIsReappliedOnRevivePreservesOriginalAndSkipsBotsAndDisabledArenas()throws Exception{
        attackSpeed(player,3.5);logic.applyLegacyCombat(player);logic.applyLegacyCombat(player);
        assertEquals(1024,speed(player));logic.restoreCombat(player);assertEquals(3.5,speed(player));
        logic.restoreCombat(player);assertEquals(3.5,speed(player));
        when(game.isBot(player)).thenReturn(true);logic.applyLegacyCombat(player);assertEquals(3.5,speed(player));
        when(game.isBot(player)).thenReturn(false);game.arena().settings().set("legacy-combat",false);
        logic.applyLegacyCombat(player);assertEquals(3.5,speed(player));
    }
    @Test void sweepAttacksAreCancelledOnlyWhenLegacyCombatIsOn(){
        var sweep=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(sweep.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        var normal=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(normal.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        logic.onEntityDamage(sweep);logic.onEntityDamage(normal);
        verify(sweep).setCancelled(true);verify(normal,never()).setCancelled(true);
        game.arena().settings().set("legacy-combat",false);
        var off=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(off.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        logic.onEntityDamage(off);verify(off,never()).setCancelled(true);
    }
    @Test void arenaSettingsOverrideHealthCurve(){
        var s=game.arena().settings();
        s.set("zombie-health-base",30);s.set("zombie-health-per-round",0);s.set("boss-health-multiplier",2);
        assertEquals(new ZCombatBalance.Tuning(30,0,60,2),logic.tuning());
    }
}
