package mcbesser.operator;

import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class VanillaWorldEditManager implements Listener {

    public static final String MENU_TITLE = ChatColor.DARK_AQUA + "VanillaWorldEdit";
    private static final int MATERIAL_SLOT = 13;
    private static final int MAX_REGION_BLOCKS = 250_000;
    private static final double MAX_PREVIEW_DISTANCE = 96.0;

    private final OperatorPlugin plugin;
    private final OperatorMenu operatorMenu;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, Material> materialSelections = new HashMap<>();
    private int previewTaskId = -1;

    public VanillaWorldEditManager(OperatorPlugin plugin, OperatorMenu operatorMenu) {
        this.plugin = plugin;
        this.operatorMenu = operatorMenu;
        this.wandKey = new NamespacedKey(plugin, "selection_stick");
        startPreviewTask();
    }

    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 45, MENU_TITLE);
        fillInventory(inventory);
        inventory.setItem(10, createItem(Material.BREEZE_ROD, ChatColor.AQUA + "Selection Stick",
            "Linksklick setzt Punkt 1.",
            "Rechtsklick setzt Punkt 2.",
            "Mittelklick mit dem Stick oeffnet dieses Menue."));
        inventory.setItem(11, createStatusItem(Material.REDSTONE_TORCH, ChatColor.RED + "Punkt 1", pos1.get(player.getUniqueId())));
        inventory.setItem(12, createStatusItem(Material.SOUL_TORCH, ChatColor.AQUA + "Punkt 2", pos2.get(player.getUniqueId())));
        inventory.setItem(MATERIAL_SLOT, createMaterialItem(materialSelections.get(player.getUniqueId())));
        inventory.setItem(20, createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Fill", "Fuellt die gesamte Auswahl."));
        inventory.setItem(21, createItem(Material.BARRIER, ChatColor.RED + "Clear", "Setzt die gesamte Auswahl auf Air."));
        inventory.setItem(22, createItem(Material.IRON_BARS, ChatColor.YELLOW + "Walls", "Erstellt nur die Aussenwaende."));
        inventory.setItem(23, createItem(Material.OAK_PLANKS, ChatColor.GOLD + "Floor", "Fuellt nur die unterste Ebene."));
        inventory.setItem(24, createItem(Material.GLASS, ChatColor.AQUA + "Hollow", "Aussenhuelle mit leerem Inneren."));
        inventory.setItem(31, createItem(Material.SLIME_BLOCK, ChatColor.GREEN + "Expand Vertical", "Zieht die Auswahl von Min- bis Max-Hoehe."));
        inventory.setItem(40, createItem(Material.BARRIER, ChatColor.RED + "Zurueck", "Geht zurueck ins Operator-Menue."));
        player.openInventory(inventory);
    }

    public void giveWand(Player player) {
        ItemStack wand = createWand();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isWand(inventory.getItem(slot))) {
                inventory.setItem(slot, wand);
                player.sendMessage(ChatColor.GREEN + "Selection Stick aktualisiert.");
                return;
            }
        }
        if (!inventory.addItem(wand).isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), wand);
        }
        player.sendMessage(ChatColor.GREEN + "Selection Stick erhalten.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().startsWith(MENU_TITLE)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);

        int slot = event.getSlot();
        if (slot == 10) {
            giveWand(player);
            if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.CREATIVE) {
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
            }
            return;
        }
        if (slot == 11) {
            pos1.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Punkt 1 entfernt.");
            openMenu(player);
            return;
        }
        if (slot == 12) {
            pos2.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Punkt 2 entfernt.");
            openMenu(player);
            return;
        }
        if (slot == MATERIAL_SLOT) {
            updateStoredMaterial(player, event.getCursor());
            openMenu(player);
            return;
        }
        if (slot == 20) {
            apply(player, RegionMode.FILL);
            return;
        }
        if (slot == 21) {
            apply(player, RegionMode.CLEAR);
            return;
        }
        if (slot == 22) {
            apply(player, RegionMode.WALLS);
            return;
        }
        if (slot == 23) {
            apply(player, RegionMode.FLOOR);
            return;
        }
        if (slot == 24) {
            apply(player, RegionMode.HOLLOW);
            return;
        }
        if (slot == 31) {
            expandVertical(player);
            openMenu(player);
            return;
        }
        if (slot == 40) {
            operatorMenu.openMainMenu(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(MENU_TITLE)
            && event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandMiddleClick(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("operator.use")) {
            return;
        }
        if (!isWand(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        openMenu(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().isOp() && !event.getPlayer().hasPermission("operator.use")) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR) {
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            placeAirSupportAndSetPoint(event.getPlayer());
            return;
        }
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            pos1.put(event.getPlayer().getUniqueId(), block.getLocation());
            sendPointMessage(event.getPlayer(), 1, block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f);
            return;
        }

        pos2.put(event.getPlayer().getUniqueId(), block.getLocation());
        sendPointMessage(event.getPlayer(), 2, block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.2f);
    }

    private void placeAirSupportAndSetPoint(Player player) {
        Block supportBlock = player.getLocation().clone().subtract(0.0, 1.0, 0.0).getBlock();
        if (!supportBlock.getType().isAir() && supportBlock.getType() != Material.CAVE_AIR && supportBlock.getType() != Material.VOID_AIR) {
            pos2.put(player.getUniqueId(), supportBlock.getLocation());
            player.sendMessage(ChatColor.YELLOW + "Unter dir ist bereits ein Block. Punkt 2 wurde darauf gesetzt.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.2f);
            return;
        }

        supportBlock.setType(Material.GLASS, false);
        pos2.put(player.getUniqueId(), supportBlock.getLocation());
        player.sendMessage(ChatColor.GREEN + "Glasblock unter dir erstellt und Punkt 2 gesetzt.");
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0f, 1.1f);
    }

    private void updateStoredMaterial(Player player, ItemStack cursor) {
        if (cursor != null && !cursor.getType().isAir()) {
            materialSelections.put(player.getUniqueId(), cursor.getType());
            player.sendMessage(ChatColor.GREEN + "Material gesetzt auf " + formatMaterial(cursor.getType()) + ".");
            return;
        }
        if (materialSelections.remove(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.YELLOW + "Material entfernt. Fill nutzt jetzt Air.");
            return;
        }
        player.sendMessage(ChatColor.RED + "Nimm ein Item auf den Cursor und klicke dann auf den Slot.");
    }

    private void sendPointMessage(Player player, int point, Location location, Sound sound, float pitch) {
        player.sendMessage(ChatColor.GREEN + "Punkt " + point + " gesetzt auf "
            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
            + " in " + location.getWorld().getName() + ".");
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.AQUA + "VanillaWorldEdit Stick");
        meta.setLore(List.of(
            ChatColor.GRAY + "Linksklick Block: Punkt 1",
            ChatColor.GRAY + "Rechtsklick Block: Punkt 2",
            ChatColor.AQUA + "Mittelklick: VanillaWorldEdit-Menue"
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BREEZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(wandKey, PersistentDataType.BYTE);
    }

    private ItemStack createStatusItem(Material material, String name, Location location) {
        if (location == null) {
            return createItem(material, name, "Noch nicht gesetzt.");
        }
        return createItem(material, name,
            "Welt: " + location.getWorld().getName(),
            "X: " + location.getBlockX(),
            "Y: " + location.getBlockY(),
            "Z: " + location.getBlockZ(),
            "Klick entfernt den Punkt.");
    }

    private ItemStack createMaterialItem(Material material) {
        if (material == null) {
            return createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + "Fill-Material",
                "Lege ein Item auf den Cursor und klicke hier.",
                "Leer gelassen = Air.");
        }
        return createItem(material, ChatColor.GREEN + "Fill-Material: " + formatMaterial(material),
            "Klicke mit leerem Cursor, um es zu entfernen.");
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void fillInventory(Inventory inventory) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void expandVertical(Player player) {
        Region region = getRegion(player, false);
        if (region == null) {
            return;
        }
        pos1.put(player.getUniqueId(), new Location(region.world, region.minX, region.world.getMinHeight(), region.minZ));
        pos2.put(player.getUniqueId(), new Location(region.world, region.maxX, region.world.getMaxHeight() - 1, region.maxZ));
        player.sendMessage(ChatColor.GREEN + "Auswahl vertikal auf Welthoehe erweitert.");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
    }

    private void apply(Player player, RegionMode mode) {
        Region region = getRegion(player, true);
        if (region == null) {
            return;
        }
        Material selected = mode == RegionMode.CLEAR ? Material.AIR : materialSelections.getOrDefault(player.getUniqueId(), Material.AIR);
        int changed = 0;
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Material target = mode.materialFor(region, x, y, z, selected);
                    if (target == null) {
                        continue;
                    }
                    Block block = region.world.getBlockAt(x, y, z);
                    if (block.getType() == target) {
                        continue;
                    }
                    block.setType(target, false);
                    changed++;
                }
            }
        }
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + mode.message(selected, changed));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.0f, 1.0f);
    }

    private Region getRegion(Player player, boolean checkVolume) {
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            player.sendMessage(ChatColor.RED + "Setze zuerst Punkt 1 und Punkt 2 mit dem Stick.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return null;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            player.sendMessage(ChatColor.RED + "Beide Punkte muessen in derselben Welt liegen.");
            return null;
        }
        Region region = new Region(
            first.getWorld(),
            Math.min(first.getBlockX(), second.getBlockX()),
            Math.max(first.getBlockX(), second.getBlockX()),
            Math.min(first.getBlockY(), second.getBlockY()),
            Math.max(first.getBlockY(), second.getBlockY()),
            Math.min(first.getBlockZ(), second.getBlockZ()),
            Math.max(first.getBlockZ(), second.getBlockZ())
        );
        if (checkVolume && region.volume() > MAX_REGION_BLOCKS) {
            player.sendMessage(ChatColor.RED + "Auswahl zu gross. Maximal " + MAX_REGION_BLOCKS + " Bloecke.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return null;
        }
        return region;
    }

    private record Region(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private enum RegionMode {
        FILL, CLEAR, WALLS, FLOOR, HOLLOW;

        private Material materialFor(Region region, int x, int y, int z, Material selected) {
            return switch (this) {
                case FILL, CLEAR -> selected;
                case WALLS -> x == region.minX || x == region.maxX || z == region.minZ || z == region.maxZ ? selected : null;
                case FLOOR -> y == region.minY ? selected : null;
                case HOLLOW -> isEdge(region, x, y, z) ? selected : Material.AIR;
            };
        }

        private boolean isEdge(Region region, int x, int y, int z) {
            return x == region.minX || x == region.maxX
                || y == region.minY || y == region.maxY
                || z == region.minZ || z == region.maxZ;
        }

        private String message(Material material, int changed) {
            return name().toLowerCase() + " mit " + material.name().toLowerCase().replace('_', ' ')
                + " auf " + changed + " Bloecke angewendet.";
        }
    }

    public void stopTask() {
        if (previewTaskId != -1) {
            Bukkit.getScheduler().cancelTask(previewTaskId);
            previewTaskId = -1;
        }
    }

    private void startPreviewTask() {
        previewTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.isOnline() || !isWand(player.getInventory().getItemInMainHand())) {
                    continue;
                }
                Location first = pos1.get(player.getUniqueId());
                Location second = pos2.get(player.getUniqueId());
                if (first != null && first.getWorld() == player.getWorld()) {
                    spawnPointParticles(player, first, true);
                }
                if (second != null && second.getWorld() == player.getWorld()) {
                    spawnPointParticles(player, second, false);
                }
                if (first == null || second == null || first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
                    continue;
                }
                if (!isNear(player.getLocation(), first, second)) {
                    continue;
                }
                drawCuboidEdges(player, first, second);
            }
        }, 5L, 8L);
    }

    private boolean isNear(Location playerLocation, Location first, Location second) {
        double centerX = (first.getBlockX() + second.getBlockX()) / 2.0;
        double centerY = (first.getBlockY() + second.getBlockY()) / 2.0;
        double centerZ = (first.getBlockZ() + second.getBlockZ()) / 2.0;
        Location center = new Location(playerLocation.getWorld(), centerX, centerY, centerZ);
        return playerLocation.distanceSquared(center) <= MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE;
    }

    private void drawCuboidEdges(Player player, Location first, Location second) {
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            spawnEdge(player, x, minY, minZ);
            spawnEdge(player, x, minY, maxZ);
            spawnEdge(player, x, maxY, minZ);
            spawnEdge(player, x, maxY, maxZ);
        }
        for (int y = minY; y <= maxY; y++) {
            spawnEdge(player, minX, y, minZ);
            spawnEdge(player, minX, y, maxZ);
            spawnEdge(player, maxX, y, minZ);
            spawnEdge(player, maxX, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            spawnEdge(player, minX, minY, z);
            spawnEdge(player, maxX, minY, z);
            spawnEdge(player, minX, maxY, z);
            spawnEdge(player, maxX, maxY, z);
        }
    }

    private void spawnEdge(Player player, int x, int y, int z) {
        player.spawnParticle(Particle.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void spawnPointParticles(Player player, Location location, boolean firstPoint) {
        player.spawnParticle(Particle.PORTAL, location.clone().add(0.5, 0.4, 0.5), 6, 0.14, 0.06, 0.14, 0.02);
        player.spawnParticle(Particle.END_ROD, location.clone().add(0.5, firstPoint ? 1.0 : 1.15, 0.5), 2, 0.08, 0.10, 0.08, 0.0);
    }
}
