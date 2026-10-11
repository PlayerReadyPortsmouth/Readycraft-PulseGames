package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.proxy.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.cumulus.form.SimpleForm;
import java.util.List;
import java.util.function.Consumer;

/** Kept separate so Velocity can load the helper without Floodgate installed. */
final class BedrockMenu {
    static boolean show(Player player, List<QueueDirectory.Queue> queues, Consumer<String> join) {
        FloodgateApi api = FloodgateApi.getInstance();
        if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
        var form = SimpleForm.builder().title("Join a minigame").content("Choose a waiting game. Places are checked when you join.");
        for (var q : queues) form.button(q.title() + "\n" + q.players() + " / " + q.maximum() + " players");
        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < queues.size()) join.accept(queues.get(index).token());
        });
        return api.sendForm(player.getUniqueId(), form);
    }
}
