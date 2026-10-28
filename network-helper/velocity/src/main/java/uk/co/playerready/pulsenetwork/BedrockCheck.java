package uk.co.playerready.pulsenetwork;

import org.geysermc.floodgate.api.FloodgateApi;
import java.util.UUID;

/** Kept separate so Velocity can load the helper without Floodgate installed (see BedrockMenu). */
final class BedrockCheck {
    private BedrockCheck() {}
    static boolean bedrock(UUID id) { return FloodgateApi.getInstance().isFloodgatePlayer(id); }
}
