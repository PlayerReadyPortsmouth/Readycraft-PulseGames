package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NickRulesTest {
    static final List<String> ONLINE = List.of("Steve", "BlueFox");
    static final List<String> STAFF = List.of("Aurora", "MattPalmer");

    boolean ok(String nick) { return NickRules.check(nick, ONLINE, STAFF, NickRules.words("badword,Ev1l")).ok(); }

    @ParameterizedTest @CsvSource({"abc,true", "ab,false", "SixteenCharsLong_,false", "Sixteen_Chars_Ok,true", "a b c,false", "ab c,false", "Zed!,false",
            "§cRed,false", "12345,false", "a1234,false", "1ab34,true", "__ab__,true", "Ünï,false", "reset,false", "RESET,false", "Max_Fun,true"})
    void shapeTable(String nick, boolean expected) { assertEquals(expected, ok(nick), nick); }

    @ParameterizedTest @CsvSource({"Steve,false", "steve,false", "Steven,false", "Stev,false", "Steve1,false", "Stexe,false", "Stevxx,true", "BlueFoxx,false",
            "Aurora,false", "Aurorb,false", "Auroraa,false", "MattPalme,false", "Aurorbb,true", "Totally_New,true"})
    void impersonationTable(String nick, boolean expected) { assertEquals(expected, ok(nick), nick); }

    @ParameterizedTest @CsvSource({"Fuck_You,false", "sh1tty,false", "xXbadwordXx,false", "Ev1l_One,false", "3v1l,false", "Sunny_Day,true", "Hitler99,false", "N1gg4,false"})
    void blockedWordsTable(String nick, boolean expected) { assertEquals(expected, ok(nick), nick); }

    @Test void refusalsDoNotRepeatTheWord() {
        var r = NickRules.check("fuck_off", ONLINE, STAFF, Set.of());
        assertFalse(r.ok());
        assertFalse(r.message().toLowerCase().contains("fuck"));
    }
    @Test void editDistanceIsExactlyOne() {
        assertTrue(NickRules.withinOne("abc", "abc")); assertTrue(NickRules.withinOne("abc", "abd")); assertTrue(NickRules.withinOne("abc", "ab"));
        assertTrue(NickRules.withinOne("abc", "xabc")); assertFalse(NickRules.withinOne("abc", "axd")); assertFalse(NickRules.withinOne("abc", "a"));
        assertFalse(NickRules.withinOne("abcd", "badc"));
    }
    @Test void storedNicknamesAreRevalidatedOnRead() {
        assertEquals("Ann_B", IdentityResolver.nickname(" Ann_B "));
        assertNull(IdentityResolver.nickname("<red>x")); assertNull(IdentityResolver.nickname("a b")); assertNull(IdentityResolver.nickname(null));
    }
}
