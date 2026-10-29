package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * One bot's human-like melee habits. Pure decisions: randomness and the tick clock are injected, and
 * block checks arrive as predicates, so nothing here touches the server beyond building {@link Location}s.
 * Movement is expressed as short destinations (Citizens cannot press A/D), as in QuakeGame.combatDestination.
 */
final class BotMelee {
    private static final double[] EDGE_RADII = {1.5, 3.0};
    private static final int[][] EDGE_DIRS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private final Random random;
    private final LongSupplier clock;
    private final double aggression, strafeTrait, jumpiness;
    private int side;
    private boolean strafing = true;
    private long flipAt = -1, aimAt = -1, sprintRollAt = -1, releaseUntil, jumpAfter, retreatEnds;
    private double aimYaw, aimPitch;
    private boolean sprintOn, retreating;

    BotMelee(UUID id, Random random, LongSupplier clock) {
        this.random = random;
        this.clock = clock;
        this.aggression = BotPersonality.aggression(id);
        this.strafeTrait = BotPersonality.strafeBias(id);
        this.jumpiness = BotPersonality.jumpiness(id);
        this.side = BotPersonality.strafeBias(id) < 0.5 ? 1 : -1;
    }

    double aggression() { return aggression; }

    // ---- strafing -------------------------------------------------------------------------------

    /**
     * A lateral destination 2-3 blocks away (side flips every 20-40 ticks), nudged to keep spacing.
     * {@code ranged} strafes without closing or backing off (shooting). Never returns an unsafe tile;
     * returns null when holding position is the best option.
     */
    Location strafeGoal(Location bot, Location target, BotDifficulty.Profile profile, Predicate<Location> safe, boolean ranged) {
        long now = clock.getAsLong();
        if (flipAt < 0 || now >= flipAt) {
            if (flipAt >= 0) side = -side;
            flipAt = now + 20 + random.nextInt(21);
            double chance = clamp(profile.strafeBias() * (0.6 + 0.8 * strafeTrait));
            strafing = ranged || random.nextDouble() < chance;
        }
        Vector toward = target.toVector().subtract(bot.toVector()).setY(0);
        double distance = toward.length();
        if (distance < 0.01) return null;
        toward.multiply(1 / distance);
        Vector lateral = new Vector(-toward.getZ(), 0, toward.getX());
        double forward = ranged ? 0 : distance < 1.4 ? -1.0 : 0.4;
        double sideways = strafing ? 2 + random.nextDouble() : 0;
        double[] attempts = strafing ? new double[]{side * sideways, -side * sideways} : new double[]{0};
        for (int i = 0; i < attempts.length; i++) {
            Vector offset = toward.clone().multiply(forward).add(lateral.clone().multiply(attempts[i]));
            if (offset.lengthSquared() < 0.25) continue;
            Location destination = bot.clone().add(offset);
            if (safe.test(destination) && safe.test(bot.clone().add(offset.clone().multiply(0.5)))) {
                if (i == 1) side = -side; // the first side was blocked: keep circling the way that works
                return destination;
            }
        }
        return null;
    }

    // ---- aim ------------------------------------------------------------------------------------

    /** Where to look: the target with a slowly re-sampled yaw/pitch error. Easy bots aim at the torso. */
    Location aim(Location eye, Location targetEye, BotDifficulty.Profile profile, boolean torso) {
        long now = clock.getAsLong();
        if (aimAt < 0 || now >= aimAt) {
            aimAt = now + 30 + random.nextInt(31);
            aimYaw = (random.nextDouble() * 2 - 1) * profile.aimErrorDegrees();
            aimPitch = (random.nextDouble() * 2 - 1) * profile.aimErrorDegrees() * 0.5;
        }
        Vector delta = targetEye.toVector().subtract(eye.toVector());
        if (torso) delta.setY(delta.getY() - 0.5);
        double length = delta.length();
        if (length < 0.01) return targetEye;
        double yaw = Math.atan2(delta.getZ(), delta.getX()) + Math.toRadians(aimYaw);
        double pitch = Math.atan2(delta.getY(), Math.hypot(delta.getX(), delta.getZ())) + Math.toRadians(aimPitch);
        pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch));
        Vector aimed = new Vector(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw)).multiply(length);
        return eye.clone().add(aimed);
    }

    /** A miss still swings: look a little way to one side of the target first. */
    Location missPoint(Location eye, Location targetEye) {
        Vector toward = targetEye.toVector().subtract(eye.toVector()).setY(0);
        if (toward.lengthSquared() < 0.0001) return targetEye;
        toward.normalize();
        double offset = (random.nextBoolean() ? 1 : -1) * (0.9 + random.nextDouble() * 0.6);
        return targetEye.clone().add(new Vector(-toward.getZ(), 0, toward.getX()).multiply(offset));
    }

    // ---- sprint, jump -----------------------------------------------------------------------------

    /** Sprint while closing and on the first hit; {@link #afterHit()} releases it for one think (a w-tap). */
    boolean sprint(double distance, boolean hitting, BotDifficulty.Profile profile) {
        long now = clock.getAsLong();
        if (now >= sprintRollAt) {
            sprintRollAt = now + 40 + random.nextInt(41);
            sprintOn = random.nextDouble() < clamp(profile.sprintChance() * (0.6 + 0.8 * aggression));
        }
        return sprintOn && now >= releaseUntil && (distance > 2.5 || hitting);
    }

    void afterHit() { releaseUntil = clock.getAsLong() + 5; }

    /** Jump-crit (cooldown full, target 2-3.2 blocks away) or jump-reset (just hurt). Easy never jumps. */
    boolean jump(double distance, boolean onGround, boolean cooldownReady, boolean hurt, BotDifficulty.Profile profile) {
        long now = clock.getAsLong();
        if (!onGround || now < jumpAfter || profile.jumpCritChance() <= 0) return false;
        double chance = clamp(profile.jumpCritChance() * (0.5 + jumpiness));
        boolean want = hurt ? random.nextDouble() < chance * 0.8
                : cooldownReady && distance >= 2.0 && distance <= 3.2 && random.nextDouble() < chance;
        if (want) jumpAfter = now + 20 + random.nextInt(21);
        return want;
    }

    // ---- retreat ----------------------------------------------------------------------------------

    /** Back off below the profile threshold only while there is something to heal with; give up after 6 s. */
    boolean retreating(double healthFraction, boolean canHeal, BotDifficulty.Profile profile) {
        long now = clock.getAsLong();
        if (!retreating) {
            if (canHeal && healthFraction <= profile.retreatHealth()) { retreating = true; retreatEnds = now + 120; }
        } else if (!canHeal || healthFraction >= profile.retreatHealth() + 0.25 || now >= retreatEnds) {
            retreating = false;
        }
        return retreating;
    }

    /** Four blocks away from the target, trying the sides if directly back is blocked; null when boxed in. */
    Location retreatGoal(Location bot, Location target, Predicate<Location> safe) {
        Vector away = bot.toVector().subtract(target.toVector()).setY(0);
        if (away.lengthSquared() < 0.0001) away = new Vector(1, 0, 0);
        away.normalize();
        Vector lateral = new Vector(-away.getZ(), 0, away.getX());
        for (Vector direction : new Vector[]{away, away.clone().add(lateral).normalize(), away.clone().subtract(lateral).normalize()}) {
            Location destination = bot.clone().add(direction.clone().multiply(4));
            if (safe.test(destination) && safe.test(bot.clone().add(direction.clone().multiply(2)))) return destination;
        }
        return null;
    }

    // ---- sumo edge --------------------------------------------------------------------------------

    /** Unit vector from {@code feet} towards the nearest missing ground within 3 blocks, or null if none. */
    static Vector edgeDirection(Location feet, Predicate<Location> ground) {
        for (double radius : EDGE_RADII) {
            Vector sum = new Vector();
            boolean found = false;
            for (int[] d : EDGE_DIRS) {
                Vector unit = new Vector(d[0], 0, d[1]).normalize();
                if (!ground.test(feet.clone().add(unit.clone().multiply(radius)))) { sum.add(unit); found = true; }
            }
            if (found && sum.lengthSquared() > 0.0001) return sum.normalize();
            if (found) return new Vector(1, 0, 0);
        }
        return null;
    }

    /**
     * Sumo: step away from the void when it is within 3 blocks; otherwise, when the target is near an edge,
     * go round to the side of the target away from that edge so the target is between the bot and the void.
     * Null means no edge decision (use the normal strafe).
     */
    Location sumoGoal(Location bot, Location target, Predicate<Location> ground) {
        Vector own = edgeDirection(bot, ground);
        if (own != null) {
            Location inward = bot.clone().add(own.clone().multiply(-3));
            return ground.test(inward) ? inward : null;
        }
        Vector theirs = edgeDirection(target, ground);
        if (theirs == null) return null;
        Location behind = target.clone().add(theirs.multiply(-1.8));
        if (!ground.test(behind) || behind.toVector().distanceSquared(bot.toVector()) < 1) return null;
        return behind;
    }

    // ---- KOTH -------------------------------------------------------------------------------------

    /** A stable spot inside the hill for this bot so a squad spreads out instead of stacking one block. */
    static Location spreadPoint(Cuboid hill, World world, UUID id) {
        double fx = 0.15 + 0.7 * BotPersonality.aggression(id), fz = 0.15 + 0.7 * BotPersonality.jumpiness(id);
        return new Location(world, hill.minX() + fx * (hill.maxX() - hill.minX() + 1),
                hill.minY(), hill.minZ() + fz * (hill.maxZ() - hill.minZ() + 1));
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
