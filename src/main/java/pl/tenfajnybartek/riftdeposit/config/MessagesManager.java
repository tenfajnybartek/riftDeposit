package pl.tenfajnybartek.riftdeposit.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * Loads {@code messages.yml} and provides typed, MiniMessage-formatted
 * component factories with named placeholder support.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * messages.send(player, "deposit.item-taken",
 *     Placeholder.unparsed("amount", String.valueOf(3)),
 *     Placeholder.component("item", Component.text("Refil")));
 * }</pre>
 */
public final class MessagesManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Logger log;
    private FileConfiguration messages;

    public MessagesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    // ── Core API ───────────────────────────────────────────────────────────

    /**
     * Retrieves the raw string from messages.yml, injects prefix and
     * resolves MiniMessage tags + all provided placeholders.
     */
    public Component format(String path, TagResolver... resolvers) {
        String raw = messages.getString(path);
        if (raw == null) {
            log.warning("Missing message key: '" + path + "'");
            raw = "<red>[MISSING: " + path + "]";
        }

        // Inject prefix placeholder so messages can use <prefix>
        String prefix = messages.getString("prefix", "");
        TagResolver prefixResolver = Placeholder.component("prefix", MM.deserialize(prefix));

        TagResolver combined = TagResolver.resolver(
                TagResolver.resolver(resolvers),
                prefixResolver
        );

        return MM.deserialize(raw, combined);
    }

    /**
     * Formats and sends a message to the given sender.
     */
    public void send(CommandSender sender, String path, TagResolver... resolvers) {
        sender.sendMessage(format(path, resolvers));
    }

    /**
     * Returns the raw (unparsed) string value from messages.yml.
     * Useful when you need the MiniMessage source for lore building.
     */
    public String getRaw(String path) {
        String raw = messages.getString(path, "");
        // Replace <prefix> manually in raw context
        String prefix = messages.getString("prefix", "");
        return raw.replace("<prefix>", prefix);
    }

    // ── Convenience helpers ────────────────────────────────────────────────

    public static TagResolver amount(int n) {
        return Placeholder.unparsed("amount", String.valueOf(n));
    }

    public static TagResolver stored(int n) {
        return Placeholder.unparsed("stored", String.valueOf(n));
    }

    public static TagResolver max(int n) {
        return Placeholder.unparsed("max", String.valueOf(n));
    }

    public static TagResolver inInventory(int n) {
        return Placeholder.unparsed("in_inv", String.valueOf(n));
    }

    public static TagResolver canTake(int n) {
        return Placeholder.unparsed("can_take", String.valueOf(n));
    }

    public static TagResolver item(String displayName) {
        return Placeholder.component("item", MM.deserialize(displayName));
    }

    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }

    public static TagResolver key(String k) {
        return Placeholder.unparsed("key", k);
    }
}