package dev.majek.simplehomes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tchristofferson.configupdater.ConfigUpdater;

import dev.majek.simplehomes.api.SimpleHomesAPI;
import dev.majek.simplehomes.command.CommandDelHome;
import dev.majek.simplehomes.command.CommandHome;
import dev.majek.simplehomes.command.CommandHomes;
import dev.majek.simplehomes.command.CommandSetHome;
import dev.majek.simplehomes.command.CommandSimpleHomes;
import dev.majek.simplehomes.data.JSONConfig;
import dev.majek.simplehomes.data.PAPI;
import dev.majek.simplehomes.data.YAMLConfig;
import dev.majek.simplehomes.data.struct.HomesPlayer;
import dev.majek.simplehomes.mechanic.PlayerJoin;
import dev.majek.simplehomes.mechanic.PlayerMove;
import dev.majek.simplehomes.mechanic.PlayerRespawn;

/**
 * Main plugin class
 */
public final class SimpleHomes extends JavaPlugin {

    private static SimpleHomes core;
    private static SimpleHomesAPI api;
    private final Map<UUID, HomesPlayer> userMap;
    private FileConfiguration lang;
    public boolean hasPapi = false;

    public SimpleHomes() {
        core = this;
        api = new SimpleHomesAPI();
        this.userMap = new HashMap<>();
    }

    @Override
    public void onEnable() {

        // Update config.yml and lang.yml
        reload();

        // Load player data
        File folder = new File(getDataFolder() + File.separator + "playerdata");
        if (!folder.exists())
            if (folder.mkdirs())
                getLogger().info("Player data folder created.");
            else
                getLogger().severe("Unable to create player data folder.");
        if (folder.listFiles() != null) {
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                JSONConfig dataStorage = new JSONConfig(folder, file.getName());
                JsonObject fileContents;
                try {
                    fileContents = dataStorage.toJsonObject();
                } catch (IOException | JsonParseException e) {
                    SimpleHomes.core().getLogger().severe("Critical error loading player data from "
                            + dataStorage.getFile().getName());
                    e.printStackTrace();
                    continue;
                }
                addToUserMap(new HomesPlayer(dataStorage, fileContents));
            }
        }

        // Hook into PAPI if it's enabled
        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") &&
                this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("Hooking into PlaceholderAPI...");
            new PAPI(this).register();
            hasPapi = true;
        }

        // Metrics
        new Metrics(this, 11490);

        // Set command executors and tab completers
        registerCommands();

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerJoin(), this);
        getServer().getPluginManager().registerEvents(new PlayerMove(), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawn(), this);
    }

    @SuppressWarnings("ConstantConditions")
    private void registerCommands() {
        getCommand("home").setExecutor(new CommandHome());
        getCommand("home").setTabCompleter(new CommandHome());
        getCommand("homes").setExecutor(new CommandHomes());
        getCommand("homes").setTabCompleter(new CommandHomes());
        getCommand("sethome").setExecutor(new CommandSetHome());
        getCommand("sethome").setTabCompleter(new CommandSetHome());
        getCommand("delhome").setExecutor(new CommandDelHome());
        getCommand("delhome").setTabCompleter(new CommandDelHome());
        getCommand("simplehomes").setExecutor(new CommandSimpleHomes());
        getCommand("simplehomes").setTabCompleter(new CommandSimpleHomes());
    }

    /**
     * Reload the plugin's configuration files.
     */
    public void reload() {
        // Initialize main config
        saveDefaultConfig();
        File configFile = new File(core.getDataFolder(), "config.yml");
        try {
            ConfigUpdater.update(core, "config.yml", configFile, Collections.emptyList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        reloadConfig();

        // Initialize lang config
        YAMLConfig langConfig = new YAMLConfig(core, null, "lang.yml");
        File langFile = new File(core.getDataFolder(), "lang.yml");
        langConfig.saveDefaultConfig();
        try {
            ConfigUpdater.update(core, "lang.yml", langFile, Collections.emptyList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        langConfig.reloadConfig();
        lang = langConfig.getConfig();
    }

    /**
     * Get SimpleHomes core. Returns an instance of the main class.
     * @return SimpleHomes.
     */
    public static SimpleHomes core() {
        return core;
    }

    /**
     * Get the SimpleHomes API. Has some useful methods.
     * @return SimpleHomesAPI.
     */
    public static SimpleHomesAPI api() {
        return api;
    }

    /**
     * Get the language file for messages.
     * @return Lang file.
     */
    public FileConfiguration getLang() {
        return core.lang;
    }

    /**
     * Get a {@link HomesPlayer} via a {@link Player} or {@link org.bukkit.OfflinePlayer}'s unique id.
     * @param uuid The unique id.
     * @return HomesPlayer if it exists.
     */
    public HomesPlayer getHomesPlayer(UUID uuid) {
        return this.userMap.get(uuid);
    }

    /**
     * Get a {@link HomesPlayer} from a username. May return null.
     * @param name The username.
     * @return HomesPlayer if it exists.
     */
    @Nullable
    public HomesPlayer getHomesPlayer(String name) {
        for (HomesPlayer homesPlayer : userMap.values()) {
            if (homesPlayer.getLastSeenName().equalsIgnoreCase(name))
                return homesPlayer;
        }
        return null;
    }

    /**
     * Add a new {@link HomesPlayer} to the user map. Used internally.
     * @param homesPlayer New user.
     */
    public void addToUserMap(HomesPlayer homesPlayer) {
        this.userMap.put(homesPlayer.getUuid(), homesPlayer);
    }

    /**
     * Get the user map. Pairs unique ids to {@link HomesPlayer} objects.
     * @return User map.
     */
    public Map<UUID, HomesPlayer> getUserMap() {
        return this.userMap;
    }

    /**
     * Safely teleport a player.
     * @param player The player.
     * @param location Teleport destination.
     */
    public void safeTeleportPlayer(final Player player, final Location location) {
        player.teleport(location);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
    }
}
