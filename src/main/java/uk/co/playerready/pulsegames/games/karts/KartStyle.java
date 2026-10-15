package uk.co.playerready.pulsegames.games.karts;

/** Tradeoffs apply equally to players and bots; no style raises the base top speed. */
public enum KartStyle {
    BALANCED("Balanced", "Steady handling and a balanced mix of items.",1,1,1,1,1),
    LIGHT("Light", "Quicker acceleration and sharper steering; longer bump disruption.",1.25,1.15,1,1,1.25),
    HEAVY("Heavy", "Slower acceleration and steering; stronger bumps and shorter hit effects.",.8,.9,1,1.35,.7),
    TRICKSTER("Trickster", "More turbo and lightning items; a slightly lower top speed.",1,1,.92,1,1);

    private final String displayName,description;
    private final double acceleration,steering,speed,bumpPower,incomingDurationMultiplier;
    KartStyle(String displayName,String description,double acceleration,double steering,double speed,
              double bumpPower,double incomingDurationMultiplier) {
        this.displayName=displayName;this.description=description;this.acceleration=acceleration;
        this.steering=steering;this.speed=speed;this.bumpPower=bumpPower;
        this.incomingDurationMultiplier=incomingDurationMultiplier;
    }
    public String displayName(){return displayName;}
    public String description(){return description;}
    public double acceleration(){return acceleration;}
    public double steering(){return steering;}
    public double speed(){return speed;}
    public double bumpPower(){return bumpPower;}
    /** Multiplies incoming bump effect duration: lower values recover sooner. */
    public double incomingDurationMultiplier(){return incomingDurationMultiplier;}
}
