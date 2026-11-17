package uk.co.playerready.pulsegames.core.seasonal;

import java.time.*;
import java.util.*;

/** Inclusive annual windows, including windows crossing New Year. */
public final class SeasonCalendar {
    public record Window(String id,MonthDay start,MonthDay end,Set<String> games,Set<String> rewards,boolean wearable,boolean easter,int before,int after) {
        public Window(String id,MonthDay start,MonthDay end,Set<String> games,Set<String> rewards,boolean wearable) {
            this(id,start,end,games,rewards,wearable,false,0,0);
        }
        public boolean contains(LocalDate date) {
            if(easter) {LocalDate sunday=easterSunday(date.getYear());return !date.isBefore(sunday.minusDays(before))&&!date.isAfter(sunday.plusDays(after));}
            MonthDay d=MonthDay.from(date);return start.compareTo(end)<=0 ? d.compareTo(start)>=0&&d.compareTo(end)<=0 : d.compareTo(start)>=0||d.compareTo(end)<=0;}
    }
    public static LocalDate easterSunday(int year) {
        int a=year%19,b=year/100,c=year%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3;
        int h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451,n=h+l-7*m+114;
        return LocalDate.of(year,n/31,n%31+1);
    }
    private SeasonCalendar() {}
    public static Optional<Window> active(List<Window> windows,LocalDate date,String override) {
        return windows.stream().filter(w->override==null?w.contains(date):w.id().equals(override)).findFirst();
    }
}
