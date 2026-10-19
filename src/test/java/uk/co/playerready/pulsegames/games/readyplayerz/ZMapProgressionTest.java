package uk.co.playerready.pulsegames.games.readyplayerz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;

class ZMapProgressionTest {
    private Map<String,Object> settings() {
        return new HashMap<>(Map.of("z-door1-anchor","1,64,1","z-door1-cost",100,"z-wall1-anchor","2,64,1","z-wall1-cost",50,"z-wall1-type","arrows","mobspawn2-door",1));
    }
    private Map<String,Cuboid> regions() {
        return new HashMap<>(Map.of("bounds",new Cuboid(0,60,0,20,80,20),"z-door1",new Cuboid(3,64,3,3,64,3),"mobspawn1",new Cuboid(5,64,5,5,64,5),"mobspawn2",new Cuboid(10,64,10,10,64,10)));
    }
    @Test void legacyMapsNeedNoProgressionConfiguration() {assertTrue(ZMapProgression.parse(Map.of(),Map.of()).doors().isEmpty());}
    @Test void validDefinitionIncludesGatedSpawnAndOfferings() {
        var d=ZMapProgression.parse(settings(),regions());assertEquals(1,d.doors().size());assertEquals(ZMapProgression.Offering.ARROWS,d.walls().getFirst().type());assertEquals(1,d.gates().get(2));
    }
    @Test void unsafeConfigurationsFailClosed() {
        for(var change:List.<Map.Entry<String,Object>>of(Map.entry("z-door1-cost",-1),Map.entry("z-wall1-cost",0),Map.entry("z-wall1-type","diamond_blocks"),Map.entry("z-door1-anchor","NaN,64,1"),Map.entry("z-wall1-anchor","1,64,1"),Map.entry("mobspawn2-door",2),Map.entry("z-door1-round",0),Map.entry("z-door01-cost",1))) {
            var s=settings();s.put(change.getKey(),change.getValue());assertThrows(IllegalArgumentException.class,()->ZMapProgression.parse(s,regions()),change.toString());
        }
    }
    @Test void emptyBoundsMissingDoorsOversizeAndAllGatedRefuse() {
        var r=regions();r.remove("bounds");assertThrows(IllegalArgumentException.class,()->ZMapProgression.parse(settings(),r));
        r.put("bounds",new Cuboid(0,60,0,20,80,20));r.put("z-door1",new Cuboid(0,60,0,20,80,20));assertThrows(IllegalArgumentException.class,()->ZMapProgression.parse(settings(),r));
        var s=settings();s.put("mobspawn1-door",1);assertThrows(IllegalArgumentException.class,()->ZMapProgression.parse(s,regions()));
    }
    private static class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        final Player player=mock(Player.class); final World world=mock(World.class);
        final PlayerInventory inventory=mock(PlayerInventory.class); final Block anchor=mock(Block.class), door=mock(Block.class);
        final BlockData original=mock(BlockData.class); final PlayerInteractEvent event=mock(PlayerInteractEvent.class);
        final ZMapProgression helper; org.bukkit.entity.TextDisplay display;
        Fixture(Map<String,Object> settings,Map<String,Cuboid> regions) {
            MemoryConfiguration config=new MemoryConfiguration();settings.forEach(config::set);
            Arena arena=new Arena("map","readyplayerz","Map","template",List.of(),1,4,"1,64,1","1,64,1",List.of("1,64,1"),regions,config);
            when(game.arena()).thenReturn(arena);when(game.world()).thenReturn(world);when(game.id()).thenReturn("run");when(world.getName()).thenReturn("pulse_run");
            when(game.plugin().getConfig().getString("instances.world-prefix","pulse_")).thenReturn("pulse_");
            when(game.state()).thenReturn(GameState.RUNNING);when(game.isActive(player)).thenReturn(true);when(game.score(player)).thenReturn(1000);
            when(player.getWorld()).thenReturn(world);when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);when(player.getLocation()).thenReturn(new Location(world,1,64,1));when(player.getInventory()).thenReturn(inventory);
            when(event.getPlayer()).thenReturn(player);when(event.getHand()).thenReturn(EquipmentSlot.HAND);when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);when(event.getClickedBlock()).thenReturn(anchor);
            when(anchor.getX()).thenReturn(1);when(anchor.getY()).thenReturn(64);when(anchor.getZ()).thenReturn(1);when(anchor.getWorld()).thenReturn(world);when(anchor.getLocation()).thenAnswer(i->new Location(world,1,64,1));
            when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(world.getBlockAt(3,64,3)).thenReturn(door);when(door.getWorld()).thenReturn(world);when(door.getX()).thenReturn(3);when(door.getY()).thenReturn(64);when(door.getZ()).thenReturn(3);when(door.getLocation()).thenReturn(new Location(world,3,64,3));
            org.bukkit.WorldBorder border=mock(org.bukkit.WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
            BlockState state=mock(BlockState.class);when(door.getState()).thenReturn(state);when(door.getType()).thenReturn(Material.STONE);when(door.getBlockData()).thenReturn(original);when(original.clone()).thenReturn(original);
            helper=new ZMapProgression(game);helper.setRound(1);
        }
    }
    @Test void teamDoorChargesOnceAndRestoresOnlyInstanceBlocks() {
        var f=new Fixture(settings(),regions());assertFalse(f.helper.spawnUnlocked(2));
        assertTrue(f.helper.onInteract(f.event));assertTrue(f.helper.spawnUnlocked(2));verify(f.game).addScore(f.player,-100);verify(f.door).setType(Material.AIR,false);
        f.helper.onInteract(f.event);verify(f.game,times(1)).addScore(f.player,-100);
        when(f.door.isEmpty()).thenReturn(true);f.helper.cleanup();verify(f.door).setBlockData(f.original,false);assertFalse(f.helper.spawnUnlocked(2));
    }
    @Test void foreignWorldOffhandInactiveAndFarClicksNeverCharge() {
        var f=new Fixture(settings(),regions());when(f.world.getName()).thenReturn("editing_world");f.helper.onInteract(f.event);verify(f.door,never()).setType(any(),anyBoolean());
        when(f.world.getName()).thenReturn("pulse_run");when(f.event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);f.helper.onInteract(f.event);
        when(f.event.getHand()).thenReturn(EquipmentSlot.HAND);when(f.game.isActive(f.player)).thenReturn(false);f.helper.onInteract(f.event);
        when(f.game.isActive(f.player)).thenReturn(true);when(f.player.getLocation()).thenReturn(new Location(f.world,20,64,20));f.helper.onInteract(f.event);
        verify(f.game,never()).addScore(any(),anyInt());
    }
    @Test void unloadedChunksContainersEmptyDoorAndRoundGatesNeverCharge() {
        var f=new Fixture(settings(),regions());f.helper.setRound(0);f.helper.onInteract(f.event);f.helper.setRound(1);
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);f.helper.onInteract(f.event);when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        TileState tile=mock(TileState.class);when(f.door.getState()).thenReturn(tile);f.helper.onInteract(f.event);when(f.door.getState()).thenReturn(mock(BlockState.class));
        when(f.door.isEmpty()).thenReturn(true);f.helper.onInteract(f.event);
        verify(f.game,never()).addScore(any(),anyInt());assertFalse(f.helper.spawnUnlocked(2));
    }
    @Test void fullInventoryWallBuyDoesNotChargeOrReplaceItems() {
        var f=new Fixture(settings(),regions());when(f.anchor.getX()).thenReturn(2);when(f.inventory.firstEmpty()).thenReturn(-1);
        f.helper.onInteract(f.event);verify(f.game,never()).addScore(any(),anyInt());verify(f.inventory,never()).setItem(anyInt(),any());
    }
    @Test void inertBarrierPolicyRejectsToggleGravityAndBurnableBlocks() {
        for(Material material:List.of(Material.OAK_DOOR,Material.IRON_DOOR,Material.OAK_FENCE_GATE,Material.LEVER,
                Material.PISTON,Material.SAND,Material.WHITE_CONCRETE_POWDER,Material.OAK_PLANKS,Material.CHEST))
            assertFalse(ZMapProgression.inert(material), material.name());
        assertTrue(ZMapProgression.inert(Material.STONE_BRICKS));
        assertTrue(ZMapProgression.inert(Material.WHITE_CONCRETE));
    }
    @Test void normalDoorMaterialFailsBeforeChargingAndAtStart() {
        var f=new Fixture(settings(),regions());when(f.door.getType()).thenReturn(Material.OAK_DOOR);
        assertNotNull(f.helper.startProblem()); f.helper.onInteract(f.event);
        verify(f.game,never()).addScore(any(),anyInt());verify(f.door,never()).setType(any(),anyBoolean());
    }
    @Test void lockedDoorRegionInteractionIsConsumedWithoutBuying() {
        var f=new Fixture(settings(),regions());when(f.event.getClickedBlock()).thenReturn(f.door);
        assertTrue(f.helper.onInteract(f.event));verify(f.event).setCancelled(true);
        verify(f.game,never()).addScore(any(),anyInt());
    }
    @Test void pistonCannotMoveLockedBarrierEvenFromAdjacentRedstone() {
        var f=new Fixture(settings(),regions());
        var piston=mock(Block.class);when(piston.getWorld()).thenReturn(f.world);
        var event=mock(org.bukkit.event.block.BlockPistonExtendEvent.class);
        when(event.getBlock()).thenReturn(piston);when(event.getBlocks()).thenReturn(List.of(f.door));
        when(event.getDirection()).thenReturn(org.bukkit.block.BlockFace.NORTH);
        f.helper.onPistonExtend(event);verify(event).setCancelled(true);
    }

    private Map<String,Object> settingsNoAnchor(){var s=settings();s.remove("z-door1-anchor");return s;}
    private Fixture sneakFixture() {
        var s=settings();s.remove("z-door1-anchor");s.put("z-door1-name","Cellar");
        var f=new Fixture(s,regions());
        f.display=mock(org.bukkit.entity.TextDisplay.class);
        when(f.world.spawn(any(Location.class),eq(org.bukkit.entity.TextDisplay.class),any(java.util.function.Consumer.class))).thenReturn(f.display);
        when(f.game.alivePlayers()).thenReturn(List.of(f.player));
        return f;
    }
    private org.bukkit.event.player.PlayerToggleSneakEvent sneak(Fixture f,boolean down) {
        var e=mock(org.bukkit.event.player.PlayerToggleSneakEvent.class);when(e.getPlayer()).thenReturn(f.player);when(e.isSneaking()).thenReturn(down);return e;
    }
    @Test void anchorIsOptionalAndSneakingNearTheDoorBuysItOnce() {
        var f=sneakFixture();assertNull(ZMapProgression.parse(settingsNoAnchor(),regions()).doors().getFirst().anchor());
        f.helper.onSneak(sneak(f,false));verify(f.game,never()).addScore(any(),anyInt());   // standing up does nothing
        f.helper.onSneak(sneak(f,true));verify(f.game).addScore(f.player,-100);verify(f.door).setType(Material.AIR,false);
        f.helper.onSneak(sneak(f,true));verify(f.game,times(1)).addScore(any(),anyInt());   // already open
    }
    @Test void sneakFarAwayLockedOrPoorNeverCharges() {
        var f=sneakFixture();
        when(f.player.getLocation()).thenReturn(new Location(f.world,15,64,15));f.helper.onSneak(sneak(f,true));
        when(f.player.getLocation()).thenReturn(new Location(f.world,1,64,1));
        f.helper.setRound(0);f.helper.onSneak(sneak(f,true));f.helper.setRound(1);
        when(f.game.score(f.player)).thenReturn(99);f.helper.onSneak(sneak(f,true));
        verify(f.game,never()).addScore(any(),anyInt());assertFalse(f.helper.spawnUnlocked(2));
        assertTrue(f.helper.nearDoorPrompt(f.player));
        assertFalse(f.helper.buyDoor(f.player,f.helper.cheapestAvailable()));
    }
    @Test void hologramShowsPriceThenIsRemovedOnOpenAndOnCleanup() {
        var f=sneakFixture();f.helper.spawnHolograms();
        verify(f.world).spawn(any(Location.class),eq(org.bukkit.entity.TextDisplay.class),any(java.util.function.Consumer.class));
        f.helper.tick();verify(f.display,atLeastOnce()).text(any(net.kyori.adventure.text.Component.class));
        verify(f.player).sendActionBar(any(net.kyori.adventure.text.Component.class));
        f.helper.onSneak(sneak(f,true));verify(f.display).remove();
        var g=sneakFixture();g.helper.spawnHolograms();g.helper.cleanup();verify(g.display).remove();
    }
}
