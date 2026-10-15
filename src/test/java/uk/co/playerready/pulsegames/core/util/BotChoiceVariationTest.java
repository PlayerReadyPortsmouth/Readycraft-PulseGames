package uk.co.playerready.pulsegames.core.util;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BotChoiceVariationTest {
    @Test void preferenceIsStableBoundedAndVariesBetweenBotsAndRounds() {
        UUID a=new UUID(0,1),b=new UUID(0,2);
        assertEquals(BotChoiceVariation.preference(a,2,3,4),BotChoiceVariation.preference(a,2,3,4));
        assertNotEquals(BotChoiceVariation.preference(a,2,3,4),BotChoiceVariation.preference(b,2,3,4));
        assertNotEquals(BotChoiceVariation.preference(a,2,3,4),BotChoiceVariation.preference(a,2,3,5));
        for(int x=-20;x<20;x++)for(int z=-20;z<20;z++) {
            double score=BotChoiceVariation.preference(a,x,z,4);assertTrue(score>=0 && score<.25);
        }
    }
}
