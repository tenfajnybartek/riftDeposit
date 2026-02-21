package pl.tenfajnybartek.riftdeposit.base;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tenfajnybartek.riftdeposit.commands.DepositCommand;
import pl.tenfajnybartek.riftdeposit.commands.RdAdminCommand;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.config.MessagesManager;
import pl.tenfajnybartek.riftdeposit.data.MySQLStorageProvider;
import pl.tenfajnybartek.riftdeposit.data.StorageProvider;
import pl.tenfajnybartek.riftdeposit.data.YamlStorageProvider;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.gui.GuiListener;
import pl.tenfajnybartek.riftdeposit.listener.InventoryCheckListener;

import java.util.Objects;

public final class DepositPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private StorageProvider storageProvider;
    private DepositManager depositManager;


    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        configManager  = new ConfigManager(this);
        messagesManager = new MessagesManager(this);
        configManager.reload();
        messagesManager.reload();

        storageProvider = buildStorage();
        try {
            storageProvider.init();
        } catch (Exception e) {
            getLogger().severe("Failed to initialise storage backend: " + e.getMessage());
            getLogger().severe("RiftDeposit will be DISABLED.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        depositManager = new DepositManager(this, configManager, messagesManager, storageProvider);

        for (Player player : Bukkit.getOnlinePlayers()) {
            depositManager.loadPlayer(player.getUniqueId());
        }

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new InventoryCheckListener(this, depositManager), this);
        pm.registerEvents(new GuiListener(depositManager, configManager), this);

        long interval = configManager.getCheckIntervalTicks();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (depositManager.isLoaded(player.getUniqueId())) {
                    depositManager.checkAndDeposit(player);
                }
            }
        }, interval, interval);

        DepositCommand depositCmd = new DepositCommand(depositManager, configManager, messagesManager);
        RdAdminCommand adminCmd   = new RdAdminCommand(
                depositManager, configManager, messagesManager,
                this::performReload
        );

        Objects.requireNonNull(getCommand("schowek")).setExecutor(depositCmd);
        Objects.requireNonNull(getCommand("rdadmin")).setExecutor(adminCmd);
        Objects.requireNonNull(getCommand("rdadmin")).setTabCompleter(adminCmd);

        getLogger().info("RiftDeposit v" + getDescription().getVersion() + " enabled. "
                + "Storage: " + configManager.getStorageType());
    }


    @Override
    public void onDisable() {
        if (depositManager != null) {
            depositManager.saveAll();
        }
        if (storageProvider != null) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            storageProvider.close();
        }
        getLogger().info("RiftDeposit disabled. All data saved.");
    }


    private StorageProvider buildStorage() {
        String type = configManager.getStorageType();
        if ("MYSQL".equalsIgnoreCase(type)) {
            return new MySQLStorageProvider(configManager, getLogger());
        }
        return new YamlStorageProvider(getDataFolder(), getLogger());
    }

    private void performReload() {
        configManager.reload();
        messagesManager.reload();
        getLogger().info("Config and messages reloaded.");
    }


    public ConfigManager getConfigManager()       { return configManager; }
    public MessagesManager getMessagesManager()   { return messagesManager; }
    public DepositManager getDepositManager()     { return depositManager; }
}