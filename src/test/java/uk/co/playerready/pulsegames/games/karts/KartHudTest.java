package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartHudTest {
 @Test void lineShowsLapPlaceItemAndBoostStateBriefly() {
  assertEquals("<yellow>L2/3  <white>P4/8  <aqua>Banana  <gold>BOOST",KartHud.line(2,3,4,8,"Banana",true,0));
  assertEquals("<white>P1/1",KartHud.line(1,1,1,1,null,false,0),"single-lap races omit the lap");
  assertEquals("<yellow>L3/3  <white>P2/6",KartHud.line(9,3,2,6,null,false,0),"lap is clamped to the race length");
 }
 @Test void aChargedDriftNamesItsTierInWordsNotJustColour() {
  assertTrue(KartHud.line(1,3,1,2,null,true,1).endsWith("<blue>MINI"));
  assertTrue(KartHud.line(1,3,1,2,null,true,2).endsWith("<gold>SUPER"));
  assertTrue(KartHud.line(1,3,1,2,null,true,3).endsWith("<light_purple>ULTRA"));
  assertFalse(KartHud.line(1,3,1,2,null,true,2).contains("BOOST"),"the tier replaces the generic word");
 }
 @Test void everyHudLineFitsABedrockActionBar() {
  var worst=KartHud.line(3,3,12,12,"Triple shell x3",true,3).replaceAll("<[^>]+>","");
  assertTrue(worst.length()<=45,worst.length()+": "+worst);
 }
 @Test void finalLapIsAnnouncedOnceOnEnteringTheLastLapOfAMultiLapRace() {
  assertTrue(KartHud.enteredFinalLap(2,3,3));assertFalse(KartHud.enteredFinalLap(3,3,3));assertFalse(KartHud.enteredFinalLap(1,2,3));
  assertFalse(KartHud.enteredFinalLap(0,1,1),"a one-lap sprint has no final lap");
 }
}
