package uk.co.playerready.pulsegames.core.seasonal;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SeasonCalendarTest {
    private SeasonCalendar.Window window(String id,String a,String b) {return new SeasonCalendar.Window(id,MonthDay.parse("--"+a),MonthDay.parse("--"+b),Set.of("presentpanic"),Set.of(),true);}
    @Test void easterMovesWithTheYear() {
        assertEquals(LocalDate.of(2026,4,5),SeasonCalendar.easterSunday(2026));
        assertEquals(LocalDate.of(2027,3,28),SeasonCalendar.easterSunday(2027));
        var easter=new SeasonCalendar.Window("easter",null,null,Set.of(),Set.of(),true,true,7,1);
        assertTrue(easter.contains(LocalDate.of(2027,3,21)));assertFalse(easter.contains(LocalDate.of(2027,4,5)));
    }
    @Test void inclusiveWindowsAndYearBoundary() {
        var x=window("new-year","12-31","01-07");
        assertTrue(x.contains(LocalDate.of(2026,12,31)));assertTrue(x.contains(LocalDate.of(2027,1,7)));
        assertFalse(x.contains(LocalDate.of(2027,1,8)));assertFalse(x.contains(LocalDate.of(2026,12,30)));
    }
    @Test void previewOverrideDoesNotChangeWindowCalendar() {
        var x=window("christmas","12-01","12-30");var date=LocalDate.of(2026,11,16);
        assertTrue(SeasonCalendar.active(List.of(x),date,null).isEmpty());
        assertEquals("christmas",SeasonCalendar.active(List.of(x),date,"christmas").orElseThrow().id());
        assertTrue(SeasonCalendar.active(List.of(x),date,null).isEmpty());
    }
}
