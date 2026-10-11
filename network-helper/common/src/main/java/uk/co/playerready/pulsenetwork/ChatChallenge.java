package uk.co.playerready.pulsenetwork;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public record ChatChallenge(String question, String answer) {
    private static final List<String> WORDS = List.of("diamond", "pickaxe", "lantern", "village", "compass", "creeper", "redstone", "portal");
    public static ChatChallenge random() {
        var random=ThreadLocalRandom.current();
        if(random.nextBoolean()) {int a=random.nextInt(2,21),b=random.nextInt(2,21);return new ChatChallenge("What is "+a+" + "+b+"?",Integer.toString(a+b));}
        String answer=WORDS.get(random.nextInt(WORDS.size()));var chars=new ArrayList<Character>();
        for(char c:answer.toCharArray())chars.add(c);Collections.shuffle(chars);StringBuilder scrambled=new StringBuilder();chars.forEach(scrambled::append);
        if(scrambled.toString().equals(answer))scrambled.reverse();
        return new ChatChallenge("Unscramble: "+scrambled,answer);
    }
}
