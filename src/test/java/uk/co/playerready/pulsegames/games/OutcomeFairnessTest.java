package uk.co.playerready.pulsegames.games;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.koth.KothGame;
import uk.co.playerready.pulsegames.games.parkour.ParkourGame;
import uk.co.playerready.pulsegames.games.buildbattle.BuildBattleGame;
import static org.mockito.Mockito.*;

class OutcomeFairnessTest {
    private Player player(World world,double x) {
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getLocation()).thenReturn(new Location(world,x,65,.5));when(p.getName()).thenReturn("Player");return p;
    }
    private void field(Object target,Class<?> owner,String name,Object value) throws Exception {
        var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @Test void kothEqualSoloScoresDrawAndSoleLeaderWins() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var a=player(null,0);var b=player(null,0);
        when(game.alivePlayers()).thenReturn(List.of(a,b));when(game.score(a)).thenReturn(5);when(game.score(b)).thenReturn(5);
        new KothGame(game).onTimeUp();verify(game).end(List.of(),"time-draw");
        when(game.score(b)).thenReturn(6);new KothGame(game).onTimeUp();verify(game).end(List.of(b),"time");
    }
    @Test void kothTiedTeamsDrawIncludingZeroScoreSides() throws Exception {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var a=player(null,0);var b=player(null,0);
        var red=new uk.co.playerready.pulsegames.core.team.GameTeam(0);red.add(a);
        var blue=new uk.co.playerready.pulsegames.core.team.GameTeam(1);blue.add(b);
        when(game.mode().isTeams()).thenReturn(true);when(game.teams()).thenReturn(List.of(red,blue));
        when(game.alivePlayers()).thenReturn(List.of(a,b));var logic=new KothGame(game);
        logic.onTimeUp();verify(game).end(List.of(),"time-draw");
        field(logic,KothGame.class,"teamScores",new HashMap<>(Map.of(0,7,1,7)));
        logic.onTimeUp();verify(game,times(2)).end(List.of(),"time-draw");
    }
    @Test void buildBattleTiedVoteAverageAwardsBothBuilders() throws Exception {
        var game=mock(GameInstance.class);var a=player(null,0);var b=player(null,0);
        when(game.alivePlayers()).thenReturn(List.of(a,b));var logic=new BuildBattleGame(game);
        field(logic,BuildBattleGame.class,"plotOf",new HashMap<>(Map.of(a.getUniqueId(),0,b.getUniqueId(),1)));
        field(logic,BuildBattleGame.class,"votes",new HashMap<>(Map.of(0,List.of(3,5),1,List.of(4))));
        var finish=BuildBattleGame.class.getDeclaredMethod("finish");finish.setAccessible(true);finish.invoke(logic);
        verify(game).end(List.of(a,b),"votes");
    }
    private ParkourGame race(GameInstance game,World world,List<Player> players) {
        when(game.world()).thenReturn(world);when(game.alivePlayers()).thenReturn(players);
        when(game.arena().region("finish")).thenReturn(new Cuboid(20,64,0,21,67,1));
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of());
        when(game.mode().id()).thenReturn("elimination");when(game.arena().settings().getInt("elim-interval",45)).thenReturn(45);
        var logic=new ParkourGame(game);logic.onStart();return logic;
    }
    @Test void parkourRanksWithinSegmentInsteadOfJoinOrder() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var world=mock(World.class);
        var behind=player(world,1);var ahead=player(world,10);var logic=race(game,world,List.of(behind,ahead));
        logic.onSecond(45);verify(game).eliminate(behind);logic.onTimeUp();verify(game).end(List.of(ahead),"time");
    }
    @Test void parkourNearTiesNeitherEliminateArbitrarilyNorAwardArbitraryWin() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var world=mock(World.class);
        var a=player(world,5);var b=player(world,5.2);var logic=race(game,world,List.of(a,b));
        logic.onSecond(45);verify(game,never()).eliminate(any());logic.onTimeUp();verify(game).end(List.of(),"time-draw");
    }
}
