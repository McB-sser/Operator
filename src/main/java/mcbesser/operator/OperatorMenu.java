package mcbesser.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

public final class OperatorMenu implements Listener {

    private static final String MAIN_TITLE = ChatColor.DARK_BLUE + "Operator Menu";
    private static final String PLAYER_TITLE = ChatColor.DARK_GREEN + "Teleport Menu";
    private static final String TPHERE_TITLE = ChatColor.GOLD + "Bring Player Menu";
    private static final String PLUGIN_TITLE = ChatColor.DARK_PURPLE + "Plugin Menu";
    private static final String PLUGIN_RESTART_TITLE = ChatColor.DARK_RED + "Plugin Restart";
    private static final int PAGE_SIZE = 45;

    private final OperatorPlugin plugin;
    private VanillaWorldEditManager vanillaWorldEditManager;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, Integer> tpHerePages = new HashMap<>();
    private final Map<UUID, Integer> pluginPages = new HashMap<>();
    private final Map<UUID, String> selectedPlugins = new HashMap<>();

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

        if (type == Material.BREEZE_ROD && vanillaWorldEditManager != null) {
            vanillaWorldEditManager.openMenu(player);
            return;
        }

        if (type == Material.COMPARATOR) {
            openPluginMenu(player, 0);
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
            || title.startsWith(PLUGIN_RESTART_TITLE);
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
}
