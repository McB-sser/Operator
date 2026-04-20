package mcbesser.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class OperatorMenu implements Listener {

    private static final String MAIN_TITLE = ChatColor.DARK_BLUE + "Operator Menu";
    private static final String PLAYER_TITLE = ChatColor.DARK_GREEN + "Teleport Menu";
    private static final String TPHERE_TITLE = ChatColor.GOLD + "Bring Player Menu";
    private static final String PLUGIN_TITLE = ChatColor.DARK_PURPLE + "Plugin Menu";
    private static final String PLUGIN_RESTART_TITLE = ChatColor.DARK_RED + "Plugin Restart";
    private static final String PERFORMANCE_TITLE = ChatColor.DARK_AQUA + "Performance Center";
    private static final String PERFORMANCE_OBJECTIVE_ID = "operator_perf";
    private static final int PAGE_SIZE = 45;
    private static final int SCOREBOARD_UPDATE_TICKS = 20;

    private final OperatorPlugin plugin;
    private VanillaWorldEditManager vanillaWorldEditManager;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, Integer> tpHerePages = new HashMap<>();
    private final Map<UUID, Integer> pluginPages = new HashMap<>();
    private final Map<UUID, String> selectedPlugins = new HashMap<>();
    private final Set<UUID> performanceScoreboardEnabled = new HashSet<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private BukkitTask performanceTask;

    public OperatorMenu(OperatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void setVanillaWorldEditManager(VanillaWorldEditManager vanillaWorldEditManager) {
        this.vanillaWorldEditManager = vanillaWorldEditManager;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        fillInventory(inventory);
        inventory.setItem(10, createItem(Material.ENDER_PEARL, ChatColor.AQUA + "Teleport To Player",
            ChatColor.GRAY + "Open a list of online players."));
        inventory.setItem(13, createItem(Material.LEAD, ChatColor.GOLD + "Teleport Player Here",
            ChatColor.GRAY + "Bring a player to your location."));
        inventory.setItem(15, createItem(Material.BREEZE_ROD, ChatColor.AQUA + "VanillaWorldEdit",
            ChatColor.GRAY + "Open region tools and Selection Stick.",
            ChatColor.YELLOW + "Includes fill, hollow, walls and more."));
        inventory.setItem(16, createItem(Material.COMPARATOR, ChatColor.LIGHT_PURPLE + "Manage Plugins",
            ChatColor.GRAY + "Show loaded plugins.",
            ChatColor.YELLOW + "Restart plugins from the GUI."));
        inventory.setItem(12, createItem(Material.NETHERITE_SWORD, ChatColor.DARK_RED + "Kill Nearby Entities",
            ChatColor.GRAY + "Removes nearby non-player entities.",
            ChatColor.YELLOW + "/kill @e[distance=..3, type=!player]"));
        inventory.setItem(22, createItem(Material.OBSERVER, ChatColor.RED + "Server Problems",
            ChatColor.GRAY + "Show common lag indicators.",
            getScoreboardStateLine(player),
            ChatColor.YELLOW + "Open live performance details."));
        player.openInventory(inventory);
    }

    private void openPlayerMenu(Player player, int page) {
        openPlayerSelectionMenu(player, page, PLAYER_TITLE, playerPages,
            ChatColor.GRAY + "Click to teleport.");
    }

    private void openTpHereMenu(Player player, int page) {
        openPlayerSelectionMenu(player, page, TPHERE_TITLE, tpHerePages,
            ChatColor.GRAY + "Click to bring this player to you.");
    }

    private void openPlayerSelectionMenu(Player player, int page, String title, Map<UUID, Integer> pageMap, String lore) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.removeIf(target -> target.getUniqueId().equals(player.getUniqueId()));
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int maxPage = Math.max(0, (onlinePlayers.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        pageMap.put(player.getUniqueId(), safePage);

        Inventory inventory = Bukkit.createInventory(null, 54, title + ChatColor.GRAY + " #" + (safePage + 1));
        fillInventory(inventory);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, onlinePlayers.size());
        for (int i = start; i < end; i++) {
            Player target = onlinePlayers.get(i);
            inventory.setItem(i - start, createItem(Material.PLAYER_HEAD, ChatColor.GREEN + target.getName(),
                lore));
        }

        setNavigationItems(inventory);

        if (onlinePlayers.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "No Players Online",
                ChatColor.GRAY + "No other players are available."));
        }

        player.openInventory(inventory);
    }

    public void openPluginMenu(Player player, int page) {
        List<Plugin> plugins = getSortedPlugins();
        int maxPage = Math.max(0, (plugins.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        pluginPages.put(player.getUniqueId(), safePage);

        Inventory inventory = Bukkit.createInventory(null, 54, PLUGIN_TITLE + ChatColor.GRAY + " #" + (safePage + 1));
        fillInventory(inventory);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, plugins.size());
        for (int i = start; i < end; i++) {
            Plugin targetPlugin = plugins.get(i);
            boolean enabled = targetPlugin.isEnabled();
            Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
            ChatColor nameColor = enabled ? ChatColor.GREEN : ChatColor.RED;
            ChatColor stateColor = enabled ? ChatColor.GREEN : ChatColor.RED;
            inventory.setItem(i - start, createItem(material, nameColor + targetPlugin.getName(),
                ChatColor.GRAY + "Version: " + targetPlugin.getDescription().getVersion(),
                stateColor + "State: " + (enabled ? "enabled" : "disabled"),
                ChatColor.YELLOW + "Click to restart this plugin."));
        }

        setNavigationItems(inventory);
        player.openInventory(inventory);
    }

    private void openPluginRestartMenu(Player player, String pluginName) {
        selectedPlugins.put(player.getUniqueId(), pluginName);

        Inventory inventory = Bukkit.createInventory(null, 27, PLUGIN_RESTART_TITLE);
        fillInventory(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Restart " + pluginName,
            ChatColor.YELLOW + "Disable and enable the plugin again."));
        inventory.setItem(15, createItem(Material.BARRIER, ChatColor.RED + "Back",
            ChatColor.GRAY + "Return to the plugin list."));
        player.openInventory(inventory);
    }

    private void openPerformanceMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, PERFORMANCE_TITLE);
        fillInventory(inventory);

        PerformanceSnapshot snapshot = capturePerformanceSnapshot();
        boolean enabled = performanceScoreboardEnabled.contains(player.getUniqueId());
        Material scoreboardMaterial = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ChatColor scoreboardColor = enabled ? ChatColor.GREEN : ChatColor.RED;

        inventory.setItem(11, createItem(scoreboardMaterial, scoreboardColor + "Performance Scoreboard",
            ChatColor.GRAY + "Toggle the live sidebar on or off.",
            ChatColor.YELLOW + "Current: " + (enabled ? "enabled" : "disabled")));
        inventory.setItem(13, createItem(Material.BOOK, ChatColor.AQUA + "Current Resource Usage",
            ChatColor.GRAY + "Memory used: " + ChatColor.WHITE + snapshot.usedMemoryMb() + " MB",
            ChatColor.GRAY + "Chunks loaded: " + ChatColor.WHITE + snapshot.loadedChunks(),
            ChatColor.GRAY + "Entities loaded: " + ChatColor.WHITE + snapshot.loadedEntities(),
            ChatColor.GRAY + "Tile entities: " + ChatColor.WHITE + snapshot.tileEntities()));
        inventory.setItem(15, createItem(Material.REDSTONE, ChatColor.GOLD + "Likely Lag Sources",
            ChatColor.GRAY + snapshot.primaryLoadLabel(),
            ChatColor.GRAY + snapshot.secondaryLoadLabel(),
            ChatColor.YELLOW + snapshot.recommendation()));
        inventory.setItem(26, createItem(Material.BARRIER, ChatColor.RED + "Back",
            ChatColor.GRAY + "Return to the main menu."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!isOperatorMenu(title)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (title.startsWith(MAIN_TITLE)) {
            handleMainMenuClick(player, clicked.getType());
            return;
        }

        if (title.startsWith(PLAYER_TITLE)) {
            handlePlayerMenuClick(player, event.getSlot(), clicked);
            return;
        }

        if (title.startsWith(TPHERE_TITLE)) {
            handleTpHereMenuClick(player, event.getSlot(), clicked);
            return;
        }

        if (title.startsWith(PLUGIN_TITLE)) {
            handlePluginMenuClick(player, event.getSlot(), clicked);
            return;
        }

        if (title.startsWith(PLUGIN_RESTART_TITLE)) {
            handlePluginRestartClick(player, clicked.getType());
            return;
        }

        if (title.startsWith(PERFORMANCE_TITLE)) {
            handlePerformanceMenuClick(player, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        UUID playerId = event.getPlayer().getUniqueId();

        if (title.startsWith(PLAYER_TITLE)) {
            playerPages.remove(playerId);
        }

        if (title.startsWith(TPHERE_TITLE)) {
            tpHerePages.remove(playerId);
        }

        if (title.startsWith(PLUGIN_TITLE)) {
            pluginPages.remove(playerId);
        }

        if (!title.startsWith(PLUGIN_RESTART_TITLE)) {
            selectedPlugins.remove(playerId);
        }
    }

    private void handleMainMenuClick(Player player, Material type) {
        if (type == Material.ENDER_PEARL) {
            openPlayerMenu(player, 0);
            return;
        }

        if (type == Material.LEAD) {
            openTpHereMenu(player, 0);
            return;
        }

        if (type == Material.NETHERITE_SWORD) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[distance=..3, type=!player]");
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Executed: /kill @e[distance=..3, type=!player]");
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            return;
        }

        if (type == Material.BREEZE_ROD && vanillaWorldEditManager != null) {
            vanillaWorldEditManager.openMenu(player);
            return;
        }

        if (type == Material.COMPARATOR) {
            openPluginMenu(player, 0);
            return;
        }

        if (type == Material.OBSERVER) {
            openPerformanceMenu(player);
        }
    }

    private void handlePlayerMenuClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            openPlayerMenu(player, playerPages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            openPlayerMenu(player, playerPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        String name = getItemName(clicked);
        if (name == null || name.isBlank()) {
            return;
        }

        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "That player is no longer online.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openPlayerMenu(player, playerPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        player.teleport(target.getLocation());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handleTpHereMenuClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            openTpHereMenu(player, tpHerePages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            openTpHereMenu(player, tpHerePages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        String name = getItemName(clicked);
        if (name == null || name.isBlank()) {
            return;
        }

        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "That player is no longer online.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openTpHereMenu(player, tpHerePages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        target.teleport(player.getLocation());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Brought " + target.getName() + " to your location.");
        target.sendMessage(ChatColor.YELLOW + "You were teleported to " + player.getName() + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handlePluginMenuClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        String pluginName = getItemName(clicked);
        if (pluginName == null || pluginName.isBlank()) {
            return;
        }

        openPluginRestartMenu(player, pluginName);
    }

    private void handlePluginRestartClick(Player player, Material type) {
        if (type == Material.BARRIER) {
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (type != Material.LIME_CONCRETE) {
            return;
        }

        String pluginName = selectedPlugins.get(player.getUniqueId());
        if (pluginName == null || pluginName.isBlank()) {
            player.sendMessage(ChatColor.RED + "No plugin selected.");
            openPluginMenu(player, 0);
            return;
        }

        if (plugin.getName().equalsIgnoreCase(pluginName)) {
            player.sendMessage(ChatColor.RED + "Operator cannot restart itself from its own GUI.");
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            player.sendMessage(ChatColor.RED + "The selected plugin is no longer loaded.");
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        player.closeInventory();
        boolean restarted = plugin.restartPlugin(targetPlugin);
        if (restarted) {
            player.sendMessage(ChatColor.GREEN + "Plugin " + targetPlugin.getName() + " was restarted.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        } else {
            player.sendMessage(ChatColor.RED + "Plugin " + targetPlugin.getName() + " could not be restarted. Check console.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handlePerformanceMenuClick(Player player, int slot) {
        if (slot == 11) {
            togglePerformanceScoreboard(player);
            openPerformanceMenu(player);
            return;
        }

        if (slot == 26) {
            openMainMenu(player);
        }
    }

    private List<Plugin> getSortedPlugins() {
        List<Plugin> plugins = new ArrayList<>(List.of(Bukkit.getPluginManager().getPlugins()));
        plugins.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
        return plugins;
    }

    private boolean isOperatorMenu(String title) {
        return title.startsWith(MAIN_TITLE)
            || title.startsWith(PLAYER_TITLE)
            || title.startsWith(TPHERE_TITLE)
            || title.startsWith(PLUGIN_TITLE)
            || title.startsWith(PLUGIN_RESTART_TITLE)
            || title.startsWith(PERFORMANCE_TITLE);
    }

    private String getItemName(ItemStack clicked) {
        if (clicked.getItemMeta() == null || clicked.getItemMeta().getDisplayName() == null) {
            return null;
        }
        return ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        if (loreLines.length > 0) {
            meta.setLore(List.of(loreLines));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void setNavigationItems(Inventory inventory) {
        inventory.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "Previous Page",
            ChatColor.GRAY + "Go to the previous page."));
        inventory.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "Back",
            ChatColor.GRAY + "Return to the main menu."));
        inventory.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "Next Page",
            ChatColor.GRAY + "Go to the next page."));
    }

    private void fillInventory(Inventory inventory) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    public void shutdown() {
        if (performanceTask != null) {
            performanceTask.cancel();
            performanceTask = null;
        }

        for (UUID playerId : new HashSet<>(performanceScoreboardEnabled)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePreviousScoreboard(player);
            }
        }

        performanceScoreboardEnabled.clear();
        previousScoreboards.clear();
    }

    private void togglePerformanceScoreboard(Player player) {
        UUID playerId = player.getUniqueId();
        if (performanceScoreboardEnabled.contains(playerId)) {
            performanceScoreboardEnabled.remove(playerId);
            restorePreviousScoreboard(player);
            player.sendMessage(ChatColor.YELLOW + "Performance scoreboard disabled.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            stopPerformanceTaskIfUnused();
            return;
        }

        previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
        performanceScoreboardEnabled.add(playerId);
        updatePlayerPerformanceScoreboard(player, capturePerformanceSnapshot());
        startPerformanceTaskIfNeeded();
        player.sendMessage(ChatColor.GREEN + "Performance scoreboard enabled.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private void startPerformanceTaskIfNeeded() {
        if (performanceTask != null) {
            return;
        }

        performanceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (performanceScoreboardEnabled.isEmpty()) {
                stopPerformanceTaskIfUnused();
                return;
            }

            PerformanceSnapshot snapshot = capturePerformanceSnapshot();
            for (UUID playerId : new HashSet<>(performanceScoreboardEnabled)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    performanceScoreboardEnabled.remove(playerId);
                    previousScoreboards.remove(playerId);
                    continue;
                }

                updatePlayerPerformanceScoreboard(player, snapshot);
            }

            stopPerformanceTaskIfUnused();
        }, 0L, SCOREBOARD_UPDATE_TICKS);
    }

    private void stopPerformanceTaskIfUnused() {
        if (!performanceScoreboardEnabled.isEmpty() || performanceTask == null) {
            return;
        }

        performanceTask.cancel();
        performanceTask = null;
    }

    private void restorePreviousScoreboard(Player player) {
        Scoreboard previous = previousScoreboards.remove(player.getUniqueId());
        if (previous != null) {
            player.setScoreboard(previous);
            return;
        }

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updatePlayerPerformanceScoreboard(Player player, PerformanceSnapshot snapshot) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(PERFORMANCE_OBJECTIVE_ID, Criteria.DUMMY,
            ChatColor.RED + "Server Problems");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        setScore(objective, ChatColor.RED + "Lag Risk", 8);
        setScore(objective, snapshot.riskLine(), 7);
        setScore(objective, ChatColor.GOLD + "Memory", 6);
        setScore(objective, snapshot.memoryLine(), 5);
        setScore(objective, ChatColor.YELLOW + "Entities", 4);
        setScore(objective, snapshot.entityLine(), 3);
        setScore(objective, ChatColor.AQUA + "Chunks", 2);
        setScore(objective, snapshot.chunkLine(), 1);
        setScore(objective, snapshot.tipLine(), 0);

        player.setScoreboard(scoreboard);
    }

    private void setScore(Objective objective, String line, int score) {
        objective.getScore(line).setScore(score);
    }

    private PerformanceSnapshot capturePerformanceSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);

        int loadedChunks = 0;
        int loadedEntities = 0;
        int tileEntities = 0;
        for (World world : Bukkit.getWorlds()) {
            var chunks = world.getLoadedChunks();
            loadedChunks += chunks.length;
            loadedEntities += world.getEntities().size();
            for (var chunk : chunks) {
                tileEntities += chunk.getTileEntities().length;
            }
        }

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int enabledPlugins = 0;
        for (Plugin loadedPlugin : Bukkit.getPluginManager().getPlugins()) {
            if (loadedPlugin.isEnabled()) {
                enabledPlugins++;
            }
        }

        String primaryLoadLabel = "Highest load: " + determinePrimaryLoad(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks);
        String secondaryLoadLabel = "Pressure: " + determinePressureLabel(onlinePlayers, enabledPlugins, loadedChunks);
        String recommendation = determineRecommendation(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks);
        String riskLine = determineRiskLine(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks);

        return new PerformanceSnapshot(usedMemoryMb, maxMemoryMb, loadedChunks, loadedEntities, tileEntities,
            primaryLoadLabel, secondaryLoadLabel, recommendation, riskLine);
    }

    private String determinePrimaryLoad(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks) {
        double memoryRatio = maxMemoryMb <= 0 ? 0.0d : (double) usedMemoryMb / maxMemoryMb;
        if (loadedEntities >= 450) {
            return "too many entities/mobs";
        }
        if (tileEntities >= 180) {
            return "many hoppers/chests/furnaces";
        }
        if (loadedChunks >= 650) {
            return "many loaded chunks";
        }
        if (memoryRatio >= 0.75d) {
            return "high RAM usage";
        }
        return "no critical hotspot detected";
    }

    private String determinePressureLabel(int onlinePlayers, int enabledPlugins, int loadedChunks) {
        if (onlinePlayers >= 12) {
            return "many players online";
        }
        if (enabledPlugins >= 18) {
            return "many active plugins";
        }
        if (loadedChunks >= 400) {
            return "chunk activity rising";
        }
        return "currently moderate";
    }

    private String determineRecommendation(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks) {
        double memoryRatio = maxMemoryMb <= 0 ? 0.0d : (double) usedMemoryMb / maxMemoryMb;
        if (loadedEntities >= 450) {
            return "Check mob farms and dropped items first.";
        }
        if (tileEntities >= 180) {
            return "Inspect hopper lines and auto-sorters.";
        }
        if (loadedChunks >= 650) {
            return "Reduce chunk loaders and farm areas.";
        }
        if (memoryRatio >= 0.75d) {
            return "Watch RAM and heavy plugin tasks.";
        }
        return "No obvious lag source at the moment.";
    }

    private String determineRiskLine(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks) {
        double memoryRatio = maxMemoryMb <= 0 ? 0.0d : (double) usedMemoryMb / maxMemoryMb;
        int score = 0;
        if (memoryRatio >= 0.75d) {
            score += 2;
        } else if (memoryRatio >= 0.6d) {
            score += 1;
        }
        if (loadedEntities >= 450) {
            score += 2;
        } else if (loadedEntities >= 250) {
            score += 1;
        }
        if (tileEntities >= 180) {
            score += 2;
        } else if (tileEntities >= 100) {
            score += 1;
        }
        if (loadedChunks >= 650) {
            score += 2;
        } else if (loadedChunks >= 400) {
            score += 1;
        }

        if (score >= 5) {
            return ChatColor.RED + "High";
        }
        if (score >= 3) {
            return ChatColor.GOLD + "Medium";
        }
        return ChatColor.GREEN + "Low";
    }

    private String getScoreboardStateLine(Player player) {
        boolean enabled = performanceScoreboardEnabled.contains(player.getUniqueId());
        return ChatColor.YELLOW + "Scoreboard: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF");
    }

    private record PerformanceSnapshot(
        long usedMemoryMb,
        long maxMemoryMb,
        int loadedChunks,
        int loadedEntities,
        int tileEntities,
        String primaryLoadLabel,
        String secondaryLoadLabel,
        String recommendation,
        String riskLine
    ) {
        private String memoryLine() {
            return ChatColor.WHITE + "" + usedMemoryMb + "/" + maxMemoryMb + " MB";
        }

        private String entityLine() {
            return ChatColor.WHITE + "" + loadedEntities + " loaded";
        }

        private String chunkLine() {
            return ChatColor.WHITE + "" + loadedChunks + " loaded";
        }

        private String tipLine() {
            if (loadedEntities >= 450) {
                return ChatColor.YELLOW + "Check farms";
            }
            if (tileEntities >= 180) {
                return ChatColor.YELLOW + "Check hoppers";
            }
            if (loadedChunks >= 650) {
                return ChatColor.YELLOW + "Check chunks";
            }
            return ChatColor.GREEN + "Server looks stable";
        }
    }
}
