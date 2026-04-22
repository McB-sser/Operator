package mcbesser.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
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

    private static final String MAIN_TITLE = ChatColor.DARK_BLUE + "Operator Menue";
    private static final String PLAYER_TITLE = ChatColor.DARK_GREEN + "Teleport-Menue";
    private static final String TPHERE_TITLE = ChatColor.GOLD + "Spieler-herholen-Menue";
    private static final String ENTITY_TELEPORT_TITLE = ChatColor.DARK_AQUA + "Entity-Teleports";
    private static final String ENTITY_TYPE_MENU_TITLE = ChatColor.DARK_GREEN + "Entity-Typ-Suche";
    private static final String PLUGIN_TITLE = ChatColor.DARK_PURPLE + "Plugin-Menue";
    private static final String PLUGIN_RESTART_TITLE = ChatColor.DARK_RED + "Plugin-Neustart";
    private static final String PERFORMANCE_TITLE = ChatColor.DARK_AQUA + "Performance-Zentrum";
    private static final String PERFORMANCE_OBJECTIVE_ID = "operator_perf";
    private static final String ENTITY_TYPE_OBJECTIVE_ID = "operator_types";
    private static final int PAGE_SIZE = 45;
    private static final int SCOREBOARD_UPDATE_TICKS = 20;
    private static final int PLAYER_CHUNK_RADIUS = 4;
    private static final double ENTITY_PLAYER_NEARBY_RADIUS = 64.0d;
    private static final ChatColor[] ENTITY_TYPE_COLORS = {
        ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE,
        ChatColor.LIGHT_PURPLE, ChatColor.WHITE, ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA
    };

    private final OperatorPlugin plugin;
    private VanillaWorldEditManager vanillaWorldEditManager;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, Integer> tpHerePages = new HashMap<>();
    private final Map<UUID, Integer> entityTeleportPages = new HashMap<>();
    private final Map<UUID, Integer> entityTypePages = new HashMap<>();
    private final Map<UUID, String> selectedEntityTypeFilters = new HashMap<>();
    private final Map<UUID, List<UUID>> visibleEntityTeleports = new HashMap<>();
    private final Map<UUID, List<EntityTeleportTarget>> visibleEntityTeleportTargets = new HashMap<>();
    private final Map<UUID, List<String>> visibleEntityTypes = new HashMap<>();
    private final Map<UUID, Integer> pluginPages = new HashMap<>();
    private final Map<UUID, String> selectedPlugins = new HashMap<>();
    private final Map<UUID, ScoreboardMode> activeScoreboards = new HashMap<>();
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
        inventory.setItem(10, createItem(Material.ENDER_PEARL, ChatColor.AQUA + "Zu Spieler teleportieren",
            ChatColor.GRAY + "Oeffnet eine Liste aller Online-Spieler."));
        inventory.setItem(13, createItem(Material.LEAD, ChatColor.GOLD + "Spieler zu dir holen",
            ChatColor.GRAY + "Teleportiert einen Spieler zu deiner Position."));
        inventory.setItem(14, createItem(Material.ENDER_EYE, ChatColor.DARK_AQUA + "Entity-Teleports",
            ChatColor.GRAY + "Listet alle geladenen Entities.",
            ChatColor.GRAY + "Verlassene Ziele stehen zuerst.",
            ChatColor.YELLOW + "Klicken, um Teleport-Ziele zu oeffnen."));
        inventory.setItem(15, createItem(Material.BREEZE_ROD, ChatColor.AQUA + "VanillaWorldEdit",
            ChatColor.GRAY + "Oeffnet Region-Tools und den Selection Stick.",
            ChatColor.YELLOW + "Enthaelt Fill, Hollow, Waende und mehr."));
        inventory.setItem(16, createItem(Material.COMPARATOR, ChatColor.LIGHT_PURPLE + "Plugins verwalten",
            ChatColor.GRAY + "Zeigt geladene Plugins an.",
            ChatColor.YELLOW + "Neustart direkt ueber das GUI."));
        inventory.setItem(12, createItem(Material.NETHERITE_SWORD, ChatColor.DARK_RED + "Nahe Entities loeschen",
            ChatColor.GRAY + "Entfernt nahe Entities ausser Spielern.",
            ChatColor.YELLOW + "/kill @e[distance=..3, type=!player]"));
        inventory.setItem(22, createItem(Material.OBSERVER, ChatColor.RED + "Server-Probleme",
            ChatColor.GRAY + "Zeigt haeufige Lag-Indikatoren an.",
            getScoreboardStateLine(player),
            ChatColor.YELLOW + "Oeffnet Live-Performance-Details."));
        player.openInventory(inventory);
    }

    private void openEntityTeleportMenu(Player player, int page) {
        String typeFilter = selectedEntityTypeFilters.get(player.getUniqueId());
        List<EntityTeleportTarget> targets = getEntityTeleportTargets(typeFilter);
        EntityTeleportDebugCounts debugCounts = countEntityTeleportDebugData();
        int maxPage = Math.max(0, (targets.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        entityTeleportPages.put(player.getUniqueId(), safePage);

        String filterLabel = typeFilter == null ? "" : ChatColor.GRAY + " [" + formatEntityTypeName(typeFilter) + "]";
        Inventory inventory = Bukkit.createInventory(null, 54, ENTITY_TELEPORT_TITLE + filterLabel + ChatColor.GRAY + " #" + (safePage + 1));
        fillInventory(inventory);

        List<UUID> visibleTargets = new ArrayList<>();
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, targets.size());
        for (int i = start; i < end; i++) {
            EntityTeleportTarget target = targets.get(i);
            visibleTargets.add(target.entityId());
            ChatColor titleColor = target.hasPlayerNearby() ? ChatColor.YELLOW : ChatColor.GREEN;
            inventory.setItem(i - start, createItem(getEntityIcon(target.entity()), titleColor + target.title(),
                ChatColor.GRAY + "Typ: " + ChatColor.WHITE + target.typeName(),
                target.validLine(),
                ChatColor.GRAY + "Welt: " + ChatColor.WHITE + target.worldName(),
                ChatColor.GRAY + "Position: " + ChatColor.WHITE + formatBlockLocation(target.location()),
                ChatColor.GRAY + "Chunk: " + ChatColor.WHITE + target.chunkX() + ", " + target.chunkZ(),
                target.playerNearbyLine(),
                target.nearestPlayerLine(),
                ChatColor.YELLOW + "Klicken zum Teleportieren."));
        }
        visibleEntityTeleports.put(player.getUniqueId(), visibleTargets);
        visibleEntityTeleportTargets.put(player.getUniqueId(), new ArrayList<>(targets.subList(start, end)));

        setNavigationItems(inventory);
        inventory.setItem(48, createItem(Material.SPYGLASS, ChatColor.AQUA + "Entity-Typen suchen",
            ChatColor.YELLOW + "Bukkit total: " + debugCounts.bukkitEntities(),
            ChatColor.YELLOW + "Chunk total: " + debugCounts.chunkEntities(),
            ChatColor.YELLOW + "Spieler: " + debugCounts.players(),
            ChatColor.YELLOW + "Tile-Entities: " + debugCounts.tileEntities(),
            debugCounts.topTypeLine(),
            ChatColor.GREEN + "Klicken fuer Typ-Auswahl."));

        if (targets.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Keine Entities gefunden",
                ChatColor.GRAY + "Es sind keine geladenen Nicht-Spieler-Entities",
                ChatColor.GRAY + "in Bukkit sichtbar.",
                ChatColor.YELLOW + "Bukkit total: " + debugCounts.bukkitEntities(),
                ChatColor.YELLOW + "Chunk total: " + debugCounts.chunkEntities(),
                ChatColor.YELLOW + "Spieler: " + debugCounts.players(),
                ChatColor.YELLOW + "Tile-Entities: " + debugCounts.tileEntities(),
                debugCounts.topTypeLine()));
        }

        player.openInventory(inventory);
    }

    private void openEntityTypeMenu(Player player, int page) {
        List<EntityTypeOption> types = getEntityTypeOptions();
        int maxPage = Math.max(0, (types.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        entityTypePages.put(player.getUniqueId(), safePage);

        Inventory inventory = Bukkit.createInventory(null, 54, ENTITY_TYPE_MENU_TITLE + ChatColor.GRAY + " #" + (safePage + 1));
        fillInventory(inventory);

        List<String> visibleTypes = new ArrayList<>();
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, types.size());
        for (int i = start; i < end; i++) {
            EntityTypeOption type = types.get(i);
            visibleTypes.add(type.rawName());
            inventory.setItem(i - start, createItem(getEntityTypeIcon(type.rawName()),
                ChatColor.GREEN + type.displayName(),
                ChatColor.GRAY + "Geladen: " + ChatColor.WHITE + type.count(),
                ChatColor.GRAY + "Welt: " + ChatColor.WHITE + type.sampleWorldName(),
                ChatColor.GRAY + "Beispiel: " + ChatColor.WHITE + formatBlockLocation(type.sampleLocation()),
                ChatColor.YELLOW + "Klicken, um nur diesen Typ zu zeigen."));
        }
        visibleEntityTypes.put(player.getUniqueId(), visibleTypes);

        setNavigationItems(inventory);
        inventory.setItem(48, createItem(Material.ENDER_EYE, ChatColor.AQUA + "Alle Entity-Typen",
            ChatColor.GRAY + "Filter entfernen.",
            ChatColor.YELLOW + "Klicken fuer alle Teleportziele."));
        player.openInventory(inventory);
    }

    private void openPlayerMenu(Player player, int page) {
        openPlayerSelectionMenu(player, page, PLAYER_TITLE, playerPages,
            ChatColor.GRAY + "Klicken zum Teleportieren.");
    }

    private void openTpHereMenu(Player player, int page) {
        openPlayerSelectionMenu(player, page, TPHERE_TITLE, tpHerePages,
            ChatColor.GRAY + "Klicken, um diesen Spieler zu dir zu holen.");
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
            inventory.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Keine Spieler online",
                ChatColor.GRAY + "Es sind keine anderen Spieler verfuegbar."));
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
                stateColor + "Status: " + (enabled ? "aktiviert" : "deaktiviert"),
                ChatColor.YELLOW + "Klicken fuer einen Plugin-Neustart."));
        }

        setNavigationItems(inventory);
        player.openInventory(inventory);
    }

    private void openPluginRestartMenu(Player player, String pluginName) {
        selectedPlugins.put(player.getUniqueId(), pluginName);

        Inventory inventory = Bukkit.createInventory(null, 27, PLUGIN_RESTART_TITLE);
        fillInventory(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, ChatColor.GREEN + pluginName + " neu starten",
            ChatColor.YELLOW + "Deaktiviert und aktiviert das Plugin erneut."));
        inventory.setItem(15, createItem(Material.BARRIER, ChatColor.RED + "Zurueck",
            ChatColor.GRAY + "Zur Plugin-Liste zurueckkehren."));
        player.openInventory(inventory);
    }

    private void openPerformanceMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, PERFORMANCE_TITLE);
        fillInventory(inventory);

        PerformanceSnapshot snapshot = capturePerformanceSnapshot(player);
        ScoreboardMode activeMode = activeScoreboards.getOrDefault(player.getUniqueId(), ScoreboardMode.NONE);
        boolean performanceEnabled = activeMode == ScoreboardMode.PERFORMANCE;
        boolean entityTypesEnabled = activeMode == ScoreboardMode.ENTITY_TYPES;
        Material performanceMaterial = performanceEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ChatColor performanceColor = performanceEnabled ? ChatColor.GREEN : ChatColor.RED;
        Material entityTypesMaterial = entityTypesEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ChatColor entityTypesColor = entityTypesEnabled ? ChatColor.GREEN : ChatColor.RED;

        inventory.setItem(10, createItem(performanceMaterial, performanceColor + "Performance-Scoreboard",
            ChatColor.GRAY + "Schaltet die Live-Seitenleiste ein oder aus.",
            ChatColor.YELLOW + "Aktuell: " + (performanceEnabled ? "aktiviert" : "deaktiviert"),
            ChatColor.GRAY + "Wenn aktiv, ist das andere Board aus."));
        inventory.setItem(11, createItem(entityTypesMaterial, entityTypesColor + "Entity-Typen-Scoreboard",
            ChatColor.GRAY + "Zeigt globale Entity-Typen live an.",
            ChatColor.YELLOW + "In Klammern: Anzahl in Radius 16.",
            ChatColor.GRAY + "Wenn aktiv, ist das andere Board aus."));
        inventory.setItem(13, createItem(Material.BOOK, ChatColor.AQUA + "Aktuelle Ressourcennutzung",
            ChatColor.GRAY + "RAM belegt: " + ChatColor.WHITE + snapshot.usedMemoryMb() + " MB",
            ChatColor.GRAY + "Chunks geladen: " + ChatColor.WHITE + snapshot.loadedChunks(),
            ChatColor.GRAY + "Entities geladen: " + ChatColor.WHITE + snapshot.loadedEntities(),
            ChatColor.GRAY + "Tile-Entities: " + ChatColor.WHITE + snapshot.tileEntities()));
        inventory.setItem(15, createItem(Material.REDSTONE, ChatColor.GOLD + "Wahrscheinliche Lag-Quellen",
            ChatColor.GRAY + snapshot.primaryLoadLabel(),
            ChatColor.GRAY + snapshot.secondaryLoadLabel(),
            ChatColor.YELLOW + snapshot.recommendation()));
        inventory.setItem(17, createItem(Material.SPYGLASS, ChatColor.GREEN + "Entity-Dichte in deiner Naehe",
            ChatColor.GRAY + "Innerhalb 64 Bloecke: " + ChatColor.WHITE + snapshot.nearbyEntities64(),
            ChatColor.GRAY + "Innerhalb 16 Bloecke: " + ChatColor.WHITE + snapshot.nearbyEntities16(),
            ChatColor.GRAY + "Innerhalb 3 Bloecke: " + ChatColor.WHITE + snapshot.nearbyEntities3(),
            ChatColor.YELLOW + "Geladene Chunks nahe dir: " + snapshot.playerAreaLoadedChunks()));
        inventory.setItem(26, createItem(Material.BARRIER, ChatColor.RED + "Zurueck",
            ChatColor.GRAY + "Zum Hauptmenue zurueckkehren."));
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

        if (title.startsWith(ENTITY_TELEPORT_TITLE)) {
            handleEntityTeleportMenuClick(player, event.getSlot());
            return;
        }

        if (title.startsWith(ENTITY_TYPE_MENU_TITLE)) {
            handleEntityTypeMenuClick(player, event.getSlot());
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

        if (title.startsWith(ENTITY_TELEPORT_TITLE)) {
            entityTeleportPages.remove(playerId);
            visibleEntityTeleports.remove(playerId);
            visibleEntityTeleportTargets.remove(playerId);
        }

        if (title.startsWith(ENTITY_TYPE_MENU_TITLE)) {
            entityTypePages.remove(playerId);
            visibleEntityTypes.remove(playerId);
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

        if (type == Material.ENDER_EYE) {
            selectedEntityTypeFilters.remove(player.getUniqueId());
            openEntityTeleportMenu(player, 0);
            return;
        }

        if (type == Material.NETHERITE_SWORD) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[distance=..3, type=!player]");
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Ausgefuehrt: /kill @e[distance=..3, type=!player]");
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
            player.sendMessage(ChatColor.RED + "Dieser Spieler ist nicht mehr online.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openPlayerMenu(player, playerPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        player.teleport(target.getLocation());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Zu " + target.getName() + " teleportiert.");
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
            player.sendMessage(ChatColor.RED + "Dieser Spieler ist nicht mehr online.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openTpHereMenu(player, tpHerePages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        target.teleport(player.getLocation());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + target.getName() + " wurde zu dir teleportiert.");
        target.sendMessage(ChatColor.YELLOW + "Du wurdest zu " + player.getName() + " teleportiert.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handleEntityTeleportMenuClick(Player player, int slot) {
        if (slot == 45) {
            openEntityTeleportMenu(player, entityTeleportPages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            openEntityTeleportMenu(player, entityTeleportPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (slot == 48) {
            openEntityTypeMenu(player, 0);
            return;
        }

        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        List<UUID> targets = visibleEntityTeleports.getOrDefault(player.getUniqueId(), List.of());
        if (slot >= targets.size()) {
            return;
        }

        EntityTeleportTarget snapshot = null;
        List<EntityTeleportTarget> snapshots = visibleEntityTeleportTargets.getOrDefault(player.getUniqueId(), List.of());
        if (slot < snapshots.size()) {
            snapshot = snapshots.get(slot);
        }

        Entity target = findEntity(targets.get(slot));
        if (target == null || !target.isValid()) {
            if (snapshot == null) {
                player.sendMessage(ChatColor.RED + "Diese Entity ist nicht mehr vorhanden.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                openEntityTeleportMenu(player, entityTeleportPages.getOrDefault(player.getUniqueId(), 0));
                return;
            }

            player.teleport(snapshot.location());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Entity-Objekt war nicht mehr stabil. Zur gespeicherten Position teleportiert: "
                + snapshot.typeName() + " bei " + formatBlockLocation(snapshot.location()) + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            return;
        }

        player.teleport(target.getLocation());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Zu Entity " + describeEntity(target) + " teleportiert.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handleEntityTypeMenuClick(Player player, int slot) {
        if (slot == 45) {
            openEntityTypeMenu(player, entityTypePages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }

        if (slot == 49) {
            openEntityTeleportMenu(player, entityTeleportPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (slot == 53) {
            openEntityTypeMenu(player, entityTypePages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (slot == 48) {
            selectedEntityTypeFilters.remove(player.getUniqueId());
            openEntityTeleportMenu(player, 0);
            return;
        }

        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        List<String> types = visibleEntityTypes.getOrDefault(player.getUniqueId(), List.of());
        if (slot >= types.size()) {
            return;
        }

        selectedEntityTypeFilters.put(player.getUniqueId(), types.get(slot));
        openEntityTeleportMenu(player, 0);
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
            player.sendMessage(ChatColor.RED + "Kein Plugin ausgewaehlt.");
            openPluginMenu(player, 0);
            return;
        }

        if (plugin.getName().equalsIgnoreCase(pluginName)) {
            player.sendMessage(ChatColor.RED + "Operator kann sich nicht selbst ueber das eigene GUI neu starten.");
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            player.sendMessage(ChatColor.RED + "Das ausgewaehlte Plugin ist nicht mehr geladen.");
            openPluginMenu(player, pluginPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        player.closeInventory();
        boolean restarted = plugin.restartPlugin(targetPlugin);
        if (restarted) {
            player.sendMessage(ChatColor.GREEN + "Plugin " + targetPlugin.getName() + " wurde neu gestartet.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        } else {
            player.sendMessage(ChatColor.RED + "Plugin " + targetPlugin.getName() + " konnte nicht neu gestartet werden. Pruefe die Konsole.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handlePerformanceMenuClick(Player player, int slot) {
        if (slot == 10) {
            togglePerformanceScoreboard(player);
            openPerformanceMenu(player);
            return;
        }

        if (slot == 11) {
            toggleEntityTypeScoreboard(player);
            openPerformanceMenu(player);
            return;
        }

        if (slot == 26) {
            openMainMenu(player);
        }
    }

    private List<EntityTeleportTarget> getEntityTeleportTargets(String typeFilter) {
        List<EntityTeleportTarget> targets = new ArrayList<>();
        Set<UUID> seenEntities = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                addEntityTeleportTarget(targets, seenEntities, entity, typeFilter);
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    addEntityTeleportTarget(targets, seenEntities, entity, typeFilter);
                }
            }
        }

        targets.sort(Comparator.comparing(EntityTeleportTarget::hasPlayerNearby)
            .thenComparing(EntityTeleportTarget::worldName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(EntityTeleportTarget::typeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(target -> target.location().getBlockX())
            .thenComparingInt(target -> target.location().getBlockZ())
            .thenComparingInt(target -> target.location().getBlockY()));
        return targets;
    }

    private void addEntityTeleportTarget(List<EntityTeleportTarget> targets, Set<UUID> seenEntities, Entity entity, String typeFilter) {
        if (entity instanceof Player || !matchesEntityType(entity, typeFilter) || !seenEntities.add(entity.getUniqueId())) {
            return;
        }

        Location location = entity.getLocation().clone();
        double nearestPlayerDistance = nearestPlayerDistance(entity);
        boolean hasPlayerNearby = nearestPlayerDistance <= ENTITY_PLAYER_NEARBY_RADIUS;
        targets.add(new EntityTeleportTarget(entity, entity.getUniqueId(), describeEntity(entity),
            formatEntityTypeName(entity.getType().name()), entity.getWorld().getName(), location,
            location.getBlockX() >> 4, location.getBlockZ() >> 4, nearestPlayerDistance, hasPlayerNearby, entity.isValid()));
    }

    private boolean matchesEntityType(Entity entity, String typeFilter) {
        return typeFilter == null || entity.getType().name().equals(typeFilter);
    }

    private List<EntityTypeOption> getEntityTypeOptions() {
        Map<String, EntityTypeOption> typeOptions = new HashMap<>();
        Set<UUID> seenEntities = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                addEntityTypeOption(typeOptions, seenEntities, entity);
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    addEntityTypeOption(typeOptions, seenEntities, entity);
                }
            }
        }

        return typeOptions.values().stream()
            .sorted(Comparator.comparingInt(EntityTypeOption::count).reversed()
                .thenComparing(EntityTypeOption::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private void addEntityTypeOption(Map<String, EntityTypeOption> typeOptions, Set<UUID> seenEntities, Entity entity) {
        if (entity instanceof Player || !seenEntities.add(entity.getUniqueId())) {
            return;
        }

        String rawName = entity.getType().name();
        EntityTypeOption existing = typeOptions.get(rawName);
        if (existing == null) {
            typeOptions.put(rawName, new EntityTypeOption(rawName, formatEntityTypeName(rawName), 1,
                entity.getWorld().getName(), entity.getLocation().clone()));
            return;
        }

        typeOptions.put(rawName, existing.withIncrementedCount());
    }

    private EntityTeleportDebugCounts countEntityTeleportDebugData() {
        int bukkitEntities = 0;
        int chunkEntities = 0;
        int players = 0;
        int tileEntities = 0;
        Map<String, Integer> typeCounts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                bukkitEntities++;
                typeCounts.merge(formatEntityTypeName(entity.getType().name()), 1, Integer::sum);
                if (entity instanceof Player) {
                    players++;
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                tileEntities += chunk.getTileEntities().length;
                for (Entity ignored : chunk.getEntities()) {
                    chunkEntities++;
                }
            }
        }

        String topTypeLine = typeCounts.entrySet().stream()
            .filter(entry -> !entry.getKey().equalsIgnoreCase("Player"))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(3)
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(", "));
        if (topTypeLine.isBlank()) {
            topTypeLine = "keine Nicht-Spieler-Typen";
        }

        return new EntityTeleportDebugCounts(bukkitEntities, chunkEntities, players, tileEntities,
            ChatColor.YELLOW + "Top-Typen: " + topTypeLine);
    }

    private double nearestPlayerDistance(Entity entity) {
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        Location entityLocation = entity.getLocation();
        for (Player player : entity.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(entityLocation);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
            }
        }

        return nearestDistanceSquared == Double.POSITIVE_INFINITY
            ? Double.POSITIVE_INFINITY
            : Math.sqrt(nearestDistanceSquared);
    }

    private Entity findEntity(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            return entity;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity worldEntity : world.getEntities()) {
                if (worldEntity.getUniqueId().equals(entityId)) {
                    return worldEntity;
                }
            }
        }
        return null;
    }

    private String describeEntity(Entity entity) {
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            return customName;
        }

        String visibleName = entity.getName();
        if (visibleName != null && !visibleName.isBlank()) {
            return visibleName;
        }

        return formatEntityTypeName(entity.getType().name());
    }

    private Material getEntityIcon(Entity entity) {
        String typeName = entity.getType().name();
        return getEntityTypeIcon(typeName, entity instanceof LivingEntity);
    }

    private Material getEntityTypeIcon(String typeName) {
        return getEntityTypeIcon(typeName, false);
    }

    private Material getEntityTypeIcon(String typeName, boolean livingEntity) {
        if (typeName.equals("ITEM_DISPLAY")) {
            return Material.ITEM_FRAME;
        }
        if (typeName.equals("ITEM_FRAME") || typeName.equals("GLOW_ITEM_FRAME")) {
            return Material.ITEM_FRAME;
        }
        if (livingEntity) {
            return Material.ZOMBIE_HEAD;
        }
        if (typeName.equals("ITEM")) {
            return Material.CHEST;
        }
        if (typeName.equals("EXPERIENCE_ORB")) {
            return Material.EXPERIENCE_BOTTLE;
        }
        if (typeName.contains("MINECART")) {
            return Material.MINECART;
        }
        if (typeName.equals("BOAT") || typeName.endsWith("_BOAT")) {
            return Material.OAK_BOAT;
        }
        if (typeName.contains("PROJECTILE") || typeName.equals("ARROW") || typeName.equals("TRIDENT")) {
            return Material.ARROW;
        }
        return Material.ENDER_EYE;
    }

    private String formatBlockLocation(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
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
            || title.startsWith(ENTITY_TELEPORT_TITLE)
            || title.startsWith(ENTITY_TYPE_MENU_TITLE)
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
        inventory.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "Vorherige Seite",
            ChatColor.GRAY + "Zur vorherigen Seite wechseln."));
        inventory.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "Zurueck",
            ChatColor.GRAY + "Zum Hauptmenue zurueckkehren."));
        inventory.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "Naechste Seite",
            ChatColor.GRAY + "Zur naechsten Seite wechseln."));
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

        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePreviousScoreboard(player);
            }
        }

        activeScoreboards.clear();
        previousScoreboards.clear();
    }

    private void togglePerformanceScoreboard(Player player) {
        toggleScoreboardMode(player, ScoreboardMode.PERFORMANCE);
    }

    private void toggleEntityTypeScoreboard(Player player) {
        toggleScoreboardMode(player, ScoreboardMode.ENTITY_TYPES);
    }

    private void toggleScoreboardMode(Player player, ScoreboardMode newMode) {
        UUID playerId = player.getUniqueId();
        ScoreboardMode currentMode = activeScoreboards.getOrDefault(playerId, ScoreboardMode.NONE);
        if (currentMode == newMode) {
            activeScoreboards.remove(playerId);
            restorePreviousScoreboard(player);
            player.sendMessage(ChatColor.YELLOW + newMode.disabledMessage());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            stopPerformanceTaskIfUnused();
            return;
        }

        previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
        activeScoreboards.put(playerId, newMode);
        updateActiveScoreboard(player);
        startPerformanceTaskIfNeeded();
        player.sendMessage(ChatColor.GREEN + newMode.enabledMessage());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private void startPerformanceTaskIfNeeded() {
        if (performanceTask != null) {
            return;
        }

        performanceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeScoreboards.isEmpty()) {
                stopPerformanceTaskIfUnused();
                return;
            }

            for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    activeScoreboards.remove(playerId);
                    previousScoreboards.remove(playerId);
                    continue;
                }

                updateActiveScoreboard(player);
            }

            stopPerformanceTaskIfUnused();
        }, 19L, SCOREBOARD_UPDATE_TICKS);
    }

    private void stopPerformanceTaskIfUnused() {
        if (!activeScoreboards.isEmpty() || performanceTask == null) {
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

    private void updateActiveScoreboard(Player player) {
        ScoreboardMode mode = activeScoreboards.getOrDefault(player.getUniqueId(), ScoreboardMode.NONE);
        if (mode == ScoreboardMode.PERFORMANCE) {
            updatePlayerPerformanceScoreboard(player, capturePerformanceSnapshot(player));
            return;
        }
        if (mode == ScoreboardMode.ENTITY_TYPES) {
            updateEntityTypeScoreboard(player, captureEntityTypeSnapshot(player));
        }
    }

    private void updatePlayerPerformanceScoreboard(Player player, PerformanceSnapshot snapshot) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(PERFORMANCE_OBJECTIVE_ID, Criteria.DUMMY,
            ChatColor.RED + "Server-Probleme");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        setScore(objective, ChatColor.RED + "Lag-Risiko", 14);
        setScore(objective, snapshot.riskLine(), 13);
        setScore(objective, ChatColor.GOLD + "Arbeitsspeicher", 12);
        setScore(objective, snapshot.memoryLine(), 11);
        setScore(objective, ChatColor.YELLOW + "Globale Ents", 10);
        setScore(objective, snapshot.entityLine(), 9);
        setScore(objective, ChatColor.AQUA + "Globale Chunks", 8);
        setScore(objective, snapshot.chunkLine(), 7);
        setScore(objective, ChatColor.GREEN + "R64: " + ChatColor.WHITE + snapshot.nearbyEntities64(), 6);
        setScore(objective, ChatColor.GREEN + "R16: " + ChatColor.WHITE + snapshot.nearbyEntities16(), 5);
        setScore(objective, ChatColor.GREEN + "R3: " + ChatColor.WHITE + snapshot.nearbyEntities3(), 4);
        setScore(objective, ChatColor.DARK_AQUA + "Bereich-Chunks", 3);
        setScore(objective, snapshot.playerChunkLine(), 2);
        setScore(objective, snapshot.playerChunkEntitiesLine(), 1);
        setScore(objective, snapshot.tipLine(), 0);

        player.setScoreboard(scoreboard);
    }

    private void updateEntityTypeScoreboard(Player player, EntityTypeSnapshot snapshot) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(ENTITY_TYPE_OBJECTIVE_ID, Criteria.DUMMY,
            ChatColor.DARK_GREEN + "Entity-Typen");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        setScore(objective, ChatColor.YELLOW + "Global (R16)", 13);
        int score = 12;
        for (String line : snapshot.lines()) {
            setScore(objective, line, score--);
        }
        setScore(objective, ChatColor.GRAY + "Nahe: " + ChatColor.WHITE + snapshot.totalNearby16(), 1);
        setScore(objective, ChatColor.GRAY + "Global: " + ChatColor.WHITE + snapshot.totalGlobal(), 0);

        player.setScoreboard(scoreboard);
    }

    private void setScore(Objective objective, String line, int score) {
        objective.getScore(line).setScore(score);
    }

    private PerformanceSnapshot capturePerformanceSnapshot(Player player) {
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

        int nearbyEntities64 = countNearbyNonPlayerEntities(player, 64.0d);
        int nearbyEntities16 = countNearbyNonPlayerEntities(player, 16.0d);
        int nearbyEntities3 = countNearbyNonPlayerEntities(player, 3.0d);
        int playerAreaLoadedChunks = 0;
        int playerAreaChunkEntities = 0;
        Chunk centerChunk = player.getLocation().getChunk();
        World playerWorld = player.getWorld();
        for (int chunkX = centerChunk.getX() - PLAYER_CHUNK_RADIUS; chunkX <= centerChunk.getX() + PLAYER_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = centerChunk.getZ() - PLAYER_CHUNK_RADIUS; chunkZ <= centerChunk.getZ() + PLAYER_CHUNK_RADIUS; chunkZ++) {
                if (!playerWorld.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                playerAreaLoadedChunks++;
                Chunk chunk = playerWorld.getChunkAt(chunkX, chunkZ);
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Player)) {
                        playerAreaChunkEntities++;
                    }
                }
            }
        }

        String primaryLoadLabel = "Hoechste Last: " + determinePrimaryLoad(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks);
        String secondaryLoadLabel = "Druck: " + determinePressureLabel(onlinePlayers, enabledPlugins, loadedChunks);
        String recommendation = determineRecommendation(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks,
            nearbyEntities64, nearbyEntities16, nearbyEntities3, playerAreaChunkEntities);
        String riskLine = determineRiskLine(usedMemoryMb, maxMemoryMb, loadedEntities, tileEntities, loadedChunks,
            nearbyEntities64, nearbyEntities16, nearbyEntities3, playerAreaChunkEntities);

        return new PerformanceSnapshot(usedMemoryMb, maxMemoryMb, loadedChunks, loadedEntities, tileEntities,
            primaryLoadLabel, secondaryLoadLabel, recommendation, riskLine,
            nearbyEntities64, nearbyEntities16, nearbyEntities3, playerAreaLoadedChunks, playerAreaChunkEntities);
    }

    private int countNearbyNonPlayerEntities(Player player, double radius) {
        int count = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player)) {
                count++;
            }
        }
        return count;
    }

    private EntityTypeSnapshot captureEntityTypeSnapshot(Player player) {
        Map<String, Integer> globalCounts = new HashMap<>();
        Map<String, Integer> nearby16Counts = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }

                String typeName = formatEntityTypeName(entity.getType().name());
                globalCounts.merge(typeName, 1, Integer::sum);
            }
        }

        for (Entity entity : player.getNearbyEntities(16.0d, 16.0d, 16.0d)) {
            if (entity instanceof Player) {
                continue;
            }

            String typeName = formatEntityTypeName(entity.getType().name());
            nearby16Counts.merge(typeName, 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> topTypes = globalCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(11)
            .toList();

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < topTypes.size(); i++) {
            Map.Entry<String, Integer> entry = topTypes.get(i);
            int nearbyCount = nearby16Counts.getOrDefault(entry.getKey(), 0);
            lines.add(ENTITY_TYPE_COLORS[i % ENTITY_TYPE_COLORS.length] + compactEntityLine(entry.getKey(), entry.getValue(), nearbyCount));
        }

        while (lines.size() < 11) {
            lines.add(ENTITY_TYPE_COLORS[lines.size() % ENTITY_TYPE_COLORS.length] + "-");
        }

        int totalGlobal = globalCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalNearby16 = nearby16Counts.values().stream().mapToInt(Integer::intValue).sum();
        return new EntityTypeSnapshot(lines, totalGlobal, totalNearby16);
    }

    private String formatEntityTypeName(String rawName) {
        return List.of(rawName.split("_")).stream()
            .map(part -> part.isEmpty() ? part : part.substring(0, 1) + part.substring(1).toLowerCase())
            .collect(Collectors.joining(""));
    }

    private String compactEntityLine(String typeName, int globalCount, int nearbyCount) {
        String compact = typeName.length() > 10 ? typeName.substring(0, 10) : typeName;
        return compact + " " + globalCount + " (" + nearbyCount + ")";
    }

    private String determinePrimaryLoad(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks) {
        double memoryRatio = maxMemoryMb <= 0 ? 0.0d : (double) usedMemoryMb / maxMemoryMb;
        if (loadedEntities >= 450) {
            return "zu viele Entities/Mobs";
        }
        if (tileEntities >= 180) {
            return "viele Hopper/Kisten/Oefen";
        }
        if (loadedChunks >= 650) {
            return "sehr viele geladene Chunks";
        }
        if (memoryRatio >= 0.75d) {
            return "hohe RAM-Auslastung";
        }
        return "kein kritischer Hotspot erkannt";
    }

    private String determinePressureLabel(int onlinePlayers, int enabledPlugins, int loadedChunks) {
        if (onlinePlayers >= 12) {
            return "viele Spieler online";
        }
        if (enabledPlugins >= 18) {
            return "viele aktive Plugins";
        }
        if (loadedChunks >= 400) {
            return "Chunk-Aktivitaet steigt";
        }
        return "derzeit moderat";
    }

    private String determineRecommendation(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks,
                                          int nearbyEntities64, int nearbyEntities16, int nearbyEntities3, int playerAreaChunkEntities) {
        double memoryRatio = maxMemoryMb <= 0 ? 0.0d : (double) usedMemoryMb / maxMemoryMb;
        if (nearbyEntities3 >= 8) {
            return "Sehr dichte Entity-Ansammlung an deiner Position.";
        }
        if (nearbyEntities16 >= 40 || nearbyEntities64 >= 140) {
            return "Hohe Mob-/Item-Dichte in deiner Naehe.";
        }
        if (playerAreaChunkEntities >= 220) {
            return "Viele Entities in deinem geladenen Chunk-Bereich.";
        }
        if (loadedEntities >= 450) {
            return "Pruefe zuerst Mobfarmen und gedroppte Items.";
        }
        if (tileEntities >= 180) {
            return "Pruefe Hopper-Leitungen und Auto-Sorter.";
        }
        if (loadedChunks >= 650) {
            return "Reduziere Chunkloader und Farmbereiche.";
        }
        if (memoryRatio >= 0.75d) {
            return "Behalte RAM und schwere Plugin-Tasks im Blick.";
        }
        return "Aktuell keine eindeutige Lag-Quelle erkannt.";
    }

    private String determineRiskLine(long usedMemoryMb, long maxMemoryMb, int loadedEntities, int tileEntities, int loadedChunks,
                                     int nearbyEntities64, int nearbyEntities16, int nearbyEntities3, int playerAreaChunkEntities) {
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
        if (nearbyEntities64 >= 140) {
            score += 2;
        } else if (nearbyEntities64 >= 80) {
            score += 1;
        }
        if (nearbyEntities16 >= 40) {
            score += 2;
        } else if (nearbyEntities16 >= 20) {
            score += 1;
        }
        if (nearbyEntities3 >= 8) {
            score += 2;
        } else if (nearbyEntities3 >= 4) {
            score += 1;
        }
        if (playerAreaChunkEntities >= 220) {
            score += 2;
        } else if (playerAreaChunkEntities >= 120) {
            score += 1;
        }

        if (score >= 5) {
            return ChatColor.RED + "Hoch";
        }
        if (score >= 3) {
            return ChatColor.GOLD + "Mittel";
        }
        return ChatColor.GREEN + "Niedrig";
    }

    private String getScoreboardStateLine(Player player) {
        ScoreboardMode mode = activeScoreboards.getOrDefault(player.getUniqueId(), ScoreboardMode.NONE);
        return ChatColor.YELLOW + "Scoreboard: " + mode.menuLabel();
    }

    private enum ScoreboardMode {
        NONE("AUS", "", ""),
        PERFORMANCE("Performance", "Performance-Scoreboard aktiviert.", "Performance-Scoreboard deaktiviert."),
        ENTITY_TYPES("Entity-Typen", "Entity-Typen-Scoreboard aktiviert.", "Entity-Typen-Scoreboard deaktiviert.");

        private final String menuLabel;
        private final String enabledMessage;
        private final String disabledMessage;

        ScoreboardMode(String menuLabel, String enabledMessage, String disabledMessage) {
            this.menuLabel = menuLabel;
            this.enabledMessage = enabledMessage;
            this.disabledMessage = disabledMessage;
        }

        private String menuLabel() {
            return this == NONE ? ChatColor.RED + menuLabel : ChatColor.GREEN + menuLabel;
        }

        private String enabledMessage() {
            return enabledMessage;
        }

        private String disabledMessage() {
            return disabledMessage;
        }
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
        String riskLine,
        int nearbyEntities64,
        int nearbyEntities16,
        int nearbyEntities3,
        int playerAreaLoadedChunks,
        int playerAreaChunkEntities
    ) {
        private String memoryLine() {
            return ChatColor.WHITE + "" + usedMemoryMb + "/" + maxMemoryMb + " MB";
        }

        private String entityLine() {
            return ChatColor.WHITE + "" + loadedEntities + " geladen";
        }

        private String chunkLine() {
            return ChatColor.WHITE + "" + loadedChunks + " geladen";
        }

        private String playerChunkLine() {
            return ChatColor.WHITE + "" + playerAreaLoadedChunks + " geladen";
        }

        private String playerChunkEntitiesLine() {
            return ChatColor.WHITE + "" + playerAreaChunkEntities + " ents";
        }

        private String tipLine() {
            if (nearbyEntities3 >= 8) {
                return ChatColor.RED + "Zu dicht in der Naehe";
            }
            if (nearbyEntities16 >= 40 || nearbyEntities64 >= 140) {
                return ChatColor.YELLOW + "Nahe Entities hoch";
            }
            if (playerAreaChunkEntities >= 220) {
                return ChatColor.YELLOW + "Chunk-Entities hoch";
            }
            if (loadedEntities >= 450) {
                return ChatColor.YELLOW + "Farmen pruefen";
            }
            if (tileEntities >= 180) {
                return ChatColor.YELLOW + "Hopper pruefen";
            }
            if (loadedChunks >= 650) {
                return ChatColor.YELLOW + "Chunks pruefen";
            }
            return ChatColor.GREEN + "Server wirkt stabil";
        }
    }

    private record EntityTeleportTarget(
        Entity entity,
        UUID entityId,
        String title,
        String typeName,
        String worldName,
        Location location,
        int chunkX,
        int chunkZ,
        double nearestPlayerDistance,
        boolean hasPlayerNearby,
        boolean valid
    ) {
        private String validLine() {
            return valid
                ? ChatColor.GREEN + "Status: teleportierbar"
                : ChatColor.RED + "Status: nicht mehr gueltig";
        }

        private String playerNearbyLine() {
            if (hasPlayerNearby) {
                return ChatColor.GOLD + "Spieler-Naehe: innerhalb "
                    + (int) ENTITY_PLAYER_NEARBY_RADIUS + " Bloecke";
            }

            return ChatColor.GREEN + "Spieler-Naehe: frei";
        }

        private String nearestPlayerLine() {
            if (nearestPlayerDistance == Double.POSITIVE_INFINITY) {
                return ChatColor.GRAY + "Naechster Spieler: " + ChatColor.WHITE + "keiner in dieser Welt";
            }

            return ChatColor.GRAY + "Naechster Spieler: " + ChatColor.WHITE
                + (int) Math.round(nearestPlayerDistance) + " Bloecke entfernt";
        }
    }

    private record EntityTeleportDebugCounts(
        int bukkitEntities,
        int chunkEntities,
        int players,
        int tileEntities,
        String topTypeLine
    ) {
    }

    private record EntityTypeOption(
        String rawName,
        String displayName,
        int count,
        String sampleWorldName,
        Location sampleLocation
    ) {
        private EntityTypeOption withIncrementedCount() {
            return new EntityTypeOption(rawName, displayName, count + 1, sampleWorldName, sampleLocation);
        }
    }

    private record EntityTypeSnapshot(
        List<String> lines,
        int totalGlobal,
        int totalNearby16
    ) {
    }
}
