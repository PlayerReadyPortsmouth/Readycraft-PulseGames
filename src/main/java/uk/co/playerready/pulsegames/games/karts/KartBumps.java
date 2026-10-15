package uk.co.playerready.pulsegames.games.karts;

/** Directional, swept rear bumper detection. Side contact and head-on traffic are non-blocking. */
public final class KartBumps {
    public static final double MIN_CLOSING_SPEED = 2;
    private KartBumps() {}

    /**
     * A is the attacker and B the car ahead. Both snapshots must represent the same tick.
     * Cooldowns and style-dependent consequences are owned by the match, not this geometry.
     */
    public static boolean rearImpact(KartPhysics.State previousA, KartPhysics.State nextA,
                                     KartPhysics.State previousB, KartPhysics.State nextB) {
        double radians=Math.toRadians(previousB.yaw());
        double fx=-Math.sin(radians),fz=Math.cos(radians),rx=fz,rz=-fx;
        double alignment=Math.cos(Math.toRadians(previousA.yaw()-previousB.yaw()));
        if(alignment<Math.cos(Math.toRadians(35)))return false;
        // Reverse driving into another car is not a rear-end attack.
        if(previousA.speed()<=0 || previousB.speed()<-.2)return false;
        double relativeForward=previousA.speed()*alignment-previousB.speed();
        if(relativeForward<MIN_CLOSING_SPEED)return false;
        double x0=previousA.x()-previousB.x(),z0=previousA.z()-previousB.z();
        double x1=nextA.x()-nextB.x(),z1=nextA.z()-nextB.z();
        double forward0=x0*fx+z0*fz,forward1=x1*fx+z1*fz;
        // The attacker's front must cross the victim's rear plane from outside.
        // Projection accounts for a modest angle between their headings.
        double attackerHalfLength=(KartPhysics.BODY_LENGTH*alignment
                +KartPhysics.BODY_WIDTH*Math.abs(Math.sin(Math.toRadians(previousA.yaw()-previousB.yaw()))))/2;
        double contact=-(KartPhysics.BODY_LENGTH/2+attackerHalfLength);
        if(forward0>=contact || forward1<contact || forward1<=forward0)return false;
        double fraction=(contact-forward0)/(forward1-forward0);
        double lateral=(x0+(x1-x0)*fraction)*rx+(z0+(z1-z0)*fraction)*rz;
        // Require bumper-centre overlap. Merely clipping a side while overtaking does not count.
        if(Math.abs(lateral)>KartPhysics.BODY_WIDTH*.45)return false;
        double ay=previousA.y()+(nextA.y()-previousA.y())*fraction;
        double by=previousB.y()+(nextB.y()-previousB.y())*fraction;
        return Math.abs(ay-by)<.75;
    }
}
