package pl.tenfajnybartek.riftdeposit.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import pl.tenfajnybartek.riftdeposit.deposit.ItemLimit;
import pl.tenfajnybartek.riftdeposit.deposit.LimitGroup;
import pl.tenfajnybartek.riftdeposit.deposit.PotionSpec;

import java.util.*;
import java.util.logging.Logger;

/**
 * Parses {@code config.yml} and exposes strongly-typed accessors.
 * Call {@link #reload()} to re-parse at runtime.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger log;

    private List<LimitGroup> groups = new ArrayList<>();
    private List<ItemLimit>  items  = new ArrayList<>();

    // Storage
    private String storageType;
    private String mysqlHost;
    private int    mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int    mysqlPoolSize;
    private long   mysqlConnectionTimeout;

    // Deposit
    private long checkIntervalTicks;

    // GUI
    private String   guiTitle;
    private int      guiRows;
    private boolean  guiFillEmpty;
    private Material guiFillMaterial;
    private String   guiFillName;
    private int      takeAllSlot;
    private Material takeAllMaterial;
    private String   takeAllName;
    private String   takeAllLore;
    private int      closeSlot;
    private Material closeMaterial;
    private String   closeName;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        parseStorage(cfg);
        parseGroups(cfg);
        parseItems(cfg);
        parseGui(cfg);

        checkIntervalTicks = Math.max(5, cfg.getLong("deposit.check-interval-ticks", 20));
        log.info("Loaded " + groups.size() + " limit groups, " + items.size() + " item limits.");
    }

    // ── Parsers ────────────────────────────────────────────────────────────

    private void parseStorage(FileConfiguration cfg) {
        storageType            = cfg.getString("storage.type", "YAML").toUpperCase();
        mysqlHost              = cfg.getString("storage.mysql.host", "localhost");
        mysqlPort              = cfg.getInt("storage.mysql.port", 3306);
        mysqlDatabase          = cfg.getString("storage.mysql.database", "riftdeposit");
        mysqlUsername          = cfg.getString("storage.mysql.username", "root");
        mysqlPassword          = cfg.getString("storage.mysql.password", "password");
        mysqlPoolSize          = cfg.getInt("storage.mysql.pool-size", 10);
        mysqlConnectionTimeout = cfg.getLong("storage.mysql.connection-timeout", 5000);
    }

    private void parseGroups(FileConfiguration cfg) {
        groups = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("groups");
        if (sec == null) {
            log.warning("No 'groups' section in config.yml — using built-in default.");
            groups.add(new LimitGroup("default", null, 0));
            return;
        }
        for (String key : sec.getKeys(false)) {
            String permission = sec.getString(key + ".permission");
            if ("null".equalsIgnoreCase(permission)) permission = null;
            int priority = sec.getInt(key + ".priority", 0);
            groups.add(new LimitGroup(key, permission, priority));
        }
        groups.sort(Comparator.comparingInt(LimitGroup::getPriority).reversed());
    }

    private void parseItems(FileConfiguration cfg) {
        items = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("items");
        if (sec == null) {
            log.warning("No 'items' section in config.yml — no items will be tracked.");
            return;
        }

        for (String key : sec.getKeys(false)) {
            String matName = sec.getString(key + ".material");
            if (matName == null) {
                log.warning("Item '" + key + "': missing 'material' — skipped.");
                continue;
            }
            Material material = Material.matchMaterial(matName);
            if (material == null) {
                log.warning("Item '" + key + "': unknown material '" + matName + "' — skipped.");
                continue;
            }

            // Potion spec (optional — only for POTION / SPLASH_POTION / LINGERING_POTION)
            PotionSpec potionSpec = parsePotionSpec(sec, key);

            String displayName = sec.getString(key + ".display-name", "<white>" + key);
            int    slot        = sec.getInt(key + ".slot", 0);

            // Per-group limits
            ConfigurationSection limitsSec = sec.getConfigurationSection(key + ".limits");
            Map<String, Integer> groupLimits = new HashMap<>();
            if (limitsSec != null) {
                for (String g : limitsSec.getKeys(false)) {
                    groupLimits.put(g, limitsSec.getInt(g));
                }
            } else {
                log.warning("Item '" + key + "': no limits defined — defaulting to 0.");
                groupLimits.put("default", 0);
            }

            items.add(new ItemLimit(key, material, potionSpec, displayName, slot, groupLimits));
        }
    }

    /**
     * Parses the optional {@code potion} sub-section for an item.
     *
     * <pre>
     * items:
     *   strength_ii:
     *     material: POTION
     *     potion:
     *       effect-type: STRENGTH      # required
     *       amplifier: 1               # optional; 0 = level I, 1 = level II
     *       min-duration-ticks: 1800   # optional lower bound (inclusive)
     *       max-duration-ticks: 1800   # optional upper bound (inclusive)
     * </pre>
     *
     * Returns {@code null} if no {@code potion:} section is present
     * (i.e. item is not a potion or doesn't need effect-level matching).
     */
    private PotionSpec parsePotionSpec(ConfigurationSection parent, String itemKey) {
        ConfigurationSection potSec = parent.getConfigurationSection(itemKey + ".potion");
        if (potSec == null) return null;

        String effectName = potSec.getString("effect-type");
        if (effectName == null) {
            log.warning("Item '" + itemKey + ".potion': missing 'effect-type' — potion spec ignored.");
            return null;
        }

        // Paper 1.21: PotionEffectType.getByName() is deprecated; use Registry.EFFECT
        // However, getByName is still the simplest cross-version approach here.
        // For 1.21 compatibility we use the namespaced key lookup.
        PotionEffectType effectType = resolveEffectType(effectName, itemKey);
        if (effectType == null) return null;

        Integer amplifier        = potSec.isSet("amplifier")         ? potSec.getInt("amplifier")         : null;
        Integer minDurationTicks = potSec.isSet("min-duration-ticks") ? potSec.getInt("min-duration-ticks") : null;
        Integer maxDurationTicks = potSec.isSet("max-duration-ticks") ? potSec.getInt("max-duration-ticks") : null;

        // Convenience: if only `duration-ticks` is set, treat it as exact (min == max)
        if (potSec.isSet("duration-ticks") && minDurationTicks == null && maxDurationTicks == null) {
            int exact = potSec.getInt("duration-ticks");
            minDurationTicks = exact;
            maxDurationTicks = exact;
        }

        return new PotionSpec(effectType, amplifier, minDurationTicks, maxDurationTicks);
    }

    private PotionEffectType resolveEffectType(String name, String itemKey) {
        // Paper 1.21+: use Registry.EFFECT (no deprecation warnings)
        // Normalise: accept both "STRENGTH" and "strength" and "minecraft:strength"
        String normalized = name.toLowerCase().replace(" ", "_");
        if (normalized.contains(":")) {
            // already namespaced — strip namespace prefix
            normalized = normalized.substring(normalized.indexOf(':') + 1);
        }

        try {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(normalized));
            if (type != null) return type;
        } catch (Exception ignored) { /* fall through */ }

        // Legacy fallback for servers that might still expose getByName
        //noinspection deprecation
        PotionEffectType legacy = PotionEffectType.getByName(name.toUpperCase());
        if (legacy != null) return legacy;

        log.warning("Item '" + itemKey + ".potion': unknown effect-type '" + name + "' — potion spec ignored.");
        log.warning("  Hint: use lowercase minecraft names, e.g. 'strength', 'instant_health', 'regeneration'");
        return null;
    }

    private void parseGui(FileConfiguration cfg) {
        guiTitle        = cfg.getString("gui.title", "<gold>Schowek");
        guiRows         = Math.max(1, Math.min(6, cfg.getInt("gui.rows", 6)));
        guiFillEmpty    = cfg.getBoolean("gui.fill-empty", true);
        guiFillName     = cfg.getString("gui.fill-name", " ");
        guiFillMaterial = parseMaterial(cfg.getString("gui.fill-material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE, "gui.fill-material");
        takeAllSlot     = cfg.getInt("gui.take-all-slot", 49);
        takeAllName     = cfg.getString("gui.take-all-name", "<green>Dobierz wszystkie");
        takeAllLore     = cfg.getString("gui.take-all-lore", "");
        takeAllMaterial = parseMaterial(cfg.getString("gui.take-all-material", "CHEST"), Material.CHEST, "gui.take-all-material");
        closeSlot       = cfg.getInt("gui.close-slot", 45);
        closeName       = cfg.getString("gui.close-name", "<red>Zamknij");
        closeMaterial   = parseMaterial(cfg.getString("gui.close-material", "BARRIER"), Material.BARRIER, "gui.close-material");
    }

    private Material parseMaterial(String name, Material fallback, String ctx) {
        Material mat = Material.matchMaterial(name == null ? "" : name);
        if (mat == null) { log.warning("Unknown material at '" + ctx + "' — using " + fallback); return fallback; }
        return mat;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public List<LimitGroup> getGroups()            { return Collections.unmodifiableList(groups); }
    public List<ItemLimit>  getItems()             { return Collections.unmodifiableList(items); }
    public Optional<ItemLimit> getItem(String key) { return items.stream().filter(i -> i.getKey().equals(key)).findFirst(); }

    public String getStorageType()                 { return storageType; }
    public String getMysqlHost()                   { return mysqlHost; }
    public int    getMysqlPort()                   { return mysqlPort; }
    public String getMysqlDatabase()               { return mysqlDatabase; }
    public String getMysqlUsername()               { return mysqlUsername; }
    public String getMysqlPassword()               { return mysqlPassword; }
    public int    getMysqlPoolSize()               { return mysqlPoolSize; }
    public long   getMysqlConnectionTimeout()      { return mysqlConnectionTimeout; }
    public long   getCheckIntervalTicks()          { return checkIntervalTicks; }

    public String   getGuiTitle()                  { return guiTitle; }
    public int      getGuiRows()                   { return guiRows; }
    public boolean  isGuiFillEmpty()               { return guiFillEmpty; }
    public Material getGuiFillMaterial()           { return guiFillMaterial; }
    public String   getGuiFillName()               { return guiFillName; }
    public int      getTakeAllSlot()               { return takeAllSlot; }
    public Material getTakeAllMaterial()           { return takeAllMaterial; }
    public String   getTakeAllName()               { return takeAllName; }
    public String   getTakeAllLore()               { return takeAllLore; }
    public int      getCloseSlot()                 { return closeSlot; }
    public Material getCloseMaterial()             { return closeMaterial; }
    public String   getCloseName()                 { return closeName; }
}