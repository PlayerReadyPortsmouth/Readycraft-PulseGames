package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.setup.SetupRequirement;

import java.util.List;
import java.util.function.Function;

/** Static definition of a minigame: identity, modes, setup requirements and the logic factory. */
public final class GameType {

    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final List<GameMode> modes;
    private final Function<GameInstance, MiniGame> factory;
    private final List<SetupRequirement> setup;
    private final boolean wip;

    public GameType(String id, String displayName, String description, Material icon,
                    List<GameMode> modes, Function<GameInstance, MiniGame> factory,
                    List<SetupRequirement> setup, boolean wip) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.modes = List.copyOf(modes);
        this.factory = factory;
        this.setup = List.copyOf(setup);
        this.wip = wip;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Material icon() { return icon; }
    public List<GameMode> modes() { return modes; }
    public List<SetupRequirement> setup() { return setup; }
    public boolean wip() { return wip; }

    public GameMode mode(String modeId) {
        return modes.stream().filter(m -> m.id().equalsIgnoreCase(modeId)).findFirst().orElse(null);
    }

    public GameMode defaultMode() {
        return modes.getFirst();
    }

    public MiniGame createLogic(GameInstance instance) {
        return factory.apply(instance);
    }
}
