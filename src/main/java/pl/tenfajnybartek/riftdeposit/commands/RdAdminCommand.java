package pl.tenfajnybartek.riftdeposit.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.config.MessagesManager;
import pl.tenfajnybartek.riftdeposit.deposit.DepositData;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.deposit.ItemLimit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin command: {@code /rdadmin <subcommand> [args...]}.
 *
 * <pre>
 * /rdadmin reload                     — reloads config + messages
 * /rdadmin inspect <player>           — shows all stored items for player
 * /rdadmin clear <player> [item]      — clears deposit (all or one item)
 * /rdadmin give <player> <item> <n>   — adds N of item to player's deposit
 * /rdadmin help                       — lists subcommands
 * </pre>
 */
public final class RdAdminCommand implements CommandExecutor, TabCompleter {

    private final DepositManager depositManager;
    private final ConfigManager config;
    private final MessagesManager messages;

    // Provided by the plugin so we can call reload on both managers
    private final Runnable reloadCallback;

    public RdAdminCommand(
            DepositManager depositManager,
            ConfigManager config,
            MessagesManager messages,
            Runnable reloadCallback
    ) {
        this.depositManager = depositManager;
        this.config = config;
        this.messages = messages;
        this.reloadCallback = reloadCallback;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("riftdeposit.admin")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "clear"   -> handleClear(sender, args);
            case "give"    -> handleGive(sender, args);
            default        -> messages.send(sender, "errors.unknown-subcommand");
        }
        return true;
    }

    // ── Subcommand handlers ────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        try {
            reloadCallback.run();
            messages.send(sender, "admin.reload-success");
        } catch (Exception e) {
            messages.send(sender, "admin.reload-fail");
            Bukkit.getLogger().severe("[RiftDeposit] Reload error: " + e.getMessage());
        }
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("/rdadmin inspect <player>"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", MessagesManager.player(args[1]));
            return;
        }

        if (!depositManager.isLoaded(target.getUniqueId())) {
            messages.send(sender, "errors.data-not-loaded");
            return;
        }

        DepositData data = depositManager.getData(target.getUniqueId());
        sender.sendMessage(messages.format("admin.inspect-header", MessagesManager.player(target.getName())));

        if (data.isEmpty()) {
            sender.sendMessage(messages.format("admin.inspect-empty"));
        } else {
            for (Map.Entry<String, Integer> entry : data.getAll().entrySet()) {
                sender.sendMessage(messages.format("admin.inspect-line",
                        MessagesManager.key(entry.getKey()),
                        MessagesManager.amount(entry.getValue())));
            }
        }
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("/rdadmin clear <player> [item]"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", MessagesManager.player(args[1]));
            return;
        }

        if (args.length >= 3) {
            // Clear specific item
            String itemKey = args[2];
            depositManager.adminClearItem(target.getUniqueId(), itemKey);
            messages.send(sender, "admin.clear-item-success",
                    MessagesManager.player(target.getName()),
                    MessagesManager.key(itemKey));
        } else {
            // Clear all
            depositManager.adminClear(target.getUniqueId());
            messages.send(sender, "admin.clear-success", MessagesManager.player(target.getName()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage("/rdadmin give <player> <item> <amount>"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", MessagesManager.player(args[1]));
            return;
        }

        String itemKey = args[2];
        Optional<ItemLimit> limitOpt = config.getItem(itemKey);
        if (limitOpt.isEmpty()) {
            sender.sendMessage("§cUnknown item key: " + itemKey);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a positive integer.");
            return;
        }

        if (!depositManager.isLoaded(target.getUniqueId())) {
            messages.send(sender, "errors.data-not-loaded");
            return;
        }

        depositManager.adminGive(target.getUniqueId(), itemKey, amount);

        ItemLimit limit = limitOpt.get();
        int maxForPlayer = limit.getMaxForPlayer(target, config.getGroups());
        int totalStored  = depositManager.getData(target.getUniqueId()).getStored(itemKey);

        messages.send(sender, "admin.give-success",
                MessagesManager.player(target.getName()),
                MessagesManager.amount(amount),
                MessagesManager.key(itemKey));

        if (totalStored > maxForPlayer) {
            messages.send(sender, "admin.give-over-limit",
                    MessagesManager.player(target.getName()),
                    MessagesManager.key(itemKey),
                    MessagesManager.max(maxForPlayer));
        }
    }

    // ── Help ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lRiftDeposit Admin Commands:");
        sender.sendMessage("§7/rdadmin reload §8— §freload config & messages");
        sender.sendMessage("§7/rdadmin inspect <player> §8— §fview player deposit");
        sender.sendMessage("§7/rdadmin clear <player> [item] §8— §fclear deposit");
        sender.sendMessage("§7/rdadmin give <player> <item> <amount> §8— §fadd to deposit");
    }

    // ── Tab Completion ─────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("riftdeposit.admin")) return List.of();

        if (args.length == 1) {
            return filterStart(args[0], List.of("reload", "inspect", "clear", "give", "help"));
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2 && (sub.equals("inspect") || sub.equals("clear") || sub.equals("give"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (sub.equals("clear") || sub.equals("give"))) {
            return config.getItems().stream()
                    .map(ItemLimit::getKey)
                    .filter(k -> k.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private List<String> filterStart(String input, List<String> options) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}