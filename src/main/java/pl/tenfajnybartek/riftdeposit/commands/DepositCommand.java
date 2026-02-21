package pl.tenfajnybartek.riftdeposit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.config.MessagesManager;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.gui.DepositGui;

/**
 * Executor for {@code /schowek}, {@code /depozyt}, {@code /deposit}, {@code /vault}.
 * All aliases share this executor — register each in plugin.yml and point at this class.
 */
public final class DepositCommand implements CommandExecutor {

    private final DepositManager depositManager;
    private final ConfigManager config;
    private final MessagesManager messages;

    public DepositCommand(DepositManager depositManager, ConfigManager config, MessagesManager messages) {
        this.depositManager = depositManager;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }

        if (!player.hasPermission("riftdeposit.use")) {
            messages.send(player, "errors.no-permission");
            return true;
        }

        if (!depositManager.isLoaded(player.getUniqueId())) {
            messages.send(player, "errors.data-not-loaded");
            return true;
        }

        DepositGui gui = new DepositGui(player, depositManager, config, messages);
        player.openInventory(gui.getInventory());
        return true;
    }
}