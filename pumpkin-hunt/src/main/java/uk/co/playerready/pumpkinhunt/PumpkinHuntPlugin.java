package uk.co.playerready.pumpkinhunt;

import org.bukkit.plugin.java.JavaPlugin;

public final class PumpkinHuntPlugin extends JavaPlugin {
    private PumpkinHuntManager hunt;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hunt = new PumpkinHuntManager(this);
        hunt.start();
    }

    @Override
    public void onDisable() {
        if (hunt != null) hunt.stop();
    }
}
