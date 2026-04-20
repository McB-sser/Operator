package mcbesser.operator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class OperatorPlugin extends JavaPlugin implements TabCompleter {

    private OperatorMenu menu;
    private VanillaWorldEditManager vanillaWorldEditManager;

    @Override
    public void onEnable() {
        this.menu = new OperatorMenu(this);
        this.vanillaWorldEditManager = new VanillaWorldEditManager(this, menu);
        this.menu.setVanillaWorldEditManager(vanillaWorldEditManager);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(vanillaWorldEditManager, this);
        if (getCommand("operator") != null) {
            getCommand("operator").setTabCompleter(this);
        }
        getLogger().info("Operator enabled.");
    }

    @Override
    public void onDisable() {
        if (menu != null) {
            menu.shutdown();
        }
        if (vanillaWorldEditManager != null) {
            vanillaWorldEditManager.stopTask();
        }
        getLogger().info("Operator disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl verwenden.");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("operator.use")) {
            player.sendMessage(ChatColor.RED + "Du musst Operator sein, um dieses Menue zu benutzen.");
            return true;
        }

        if (args.length == 0) {
            menu.openMainMenu(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("vwe")) {
            vanillaWorldEditManager.openMenu(player);
            return true;
        }

        if (subcommand.equals("wand")) {
            vanillaWorldEditManager.giveWand(player);
            return true;
        }

        if (subcommand.equals("tp")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Benutzung: /operator tp <spieler>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Dieser Spieler ist nicht online.");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Du bist bereits an deiner eigenen Position.");
                return true;
            }

            player.teleport(target.getLocation());
            player.sendMessage(ChatColor.GREEN + "Zu " + target.getName() + " teleportiert.");
            return true;
        }

        if (subcommand.equals("tphere")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Benutzung: /operator tphere <spieler>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Dieser Spieler ist nicht online.");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst zu dir holen.");
                return true;
            }

            target.teleport(player.getLocation());
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde zu dir teleportiert.");
            target.sendMessage(ChatColor.YELLOW + "Du wurdest zu " + player.getName() + " teleportiert.");
            return true;
        }

        if (subcommand.equals("plugin")) {
            if (args.length == 1) {
                menu.openPluginMenu(player, 0);
                return true;
            }

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Benutzung: /operator plugin <reload|enabled|disabled> <name>");
                return true;
            }

            String action = args[1].toLowerCase(Locale.ROOT);
            Plugin target = Bukkit.getPluginManager().getPlugin(args[2]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Plugin nicht gefunden.");
                return true;
            }

            if (getName().equalsIgnoreCase(target.getName())) {
                player.sendMessage(ChatColor.RED + "Operator kann sich nicht selbst verwalten.");
                return true;
            }

            if (action.equals("reload")) {
                boolean restarted = restartPlugin(target);
                if (restarted) {
                    player.sendMessage(ChatColor.GREEN + "Plugin " + target.getName() + " wurde neu gestartet.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " konnte nicht neu gestartet werden. Pruefe die Konsole.");
                }
                return true;
            }

            if (action.equals("enabled")) {
                boolean enabled = enablePlugin(target);
                if (enabled) {
                    player.sendMessage(ChatColor.GREEN + "Plugin " + target.getName() + " wurde aktiviert.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " konnte nicht aktiviert werden. Pruefe die Konsole.");
                }
                return true;
            }

            if (action.equals("disabled")) {
                boolean disabled = disablePlugin(target);
                if (disabled) {
                    player.sendMessage(ChatColor.YELLOW + "Plugin " + target.getName() + " wurde deaktiviert.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " konnte nicht deaktiviert werden. Pruefe die Konsole.");
                }
                return true;
            }

            player.sendMessage(ChatColor.RED + "Benutzung: /operator plugin <reload|enabled|disabled> <name>");
            return true;
        }

        player.sendMessage(ChatColor.RED + "Benutzung: /operator [vwe|wand|tp <spieler>|tphere <spieler>|plugin <reload|enabled|disabled> <name>]");
        return true;
    }

    public boolean restartPlugin(Plugin target) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        try {
            pluginManager.disablePlugin(target);
            pluginManager.enablePlugin(target);
            return target.isEnabled();
        } catch (Exception exception) {
            getLogger().severe("Failed to restart plugin " + target.getName() + ": " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public boolean enablePlugin(Plugin target) {
        if (target.isEnabled()) {
            return true;
        }

        try {
            Bukkit.getPluginManager().enablePlugin(target);
            return target.isEnabled();
        } catch (Exception exception) {
            getLogger().severe("Failed to enable plugin " + target.getName() + ": " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public boolean disablePlugin(Plugin target) {
        if (!target.isEnabled()) {
            return true;
        }

        try {
            Bukkit.getPluginManager().disablePlugin(target);
            return !target.isEnabled();
        } catch (Exception exception) {
            getLogger().severe("Failed to disable plugin " + target.getName() + ": " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || (!player.isOp() && !player.hasPermission("operator.use"))) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("tp");
            suggestions.add("tphere");
            suggestions.add("vwe");
            suggestions.add("wand");
            suggestions.add("plugin");
            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("tphere"))) {
            List<String> playerNames = new ArrayList<>();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                    playerNames.add(onlinePlayer.getName());
                }
            }
            return filterSuggestions(playerNames, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("plugin")) {
            return filterSuggestions(List.of("reload", "enabled", "disabled"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("plugin")
            && (args[1].equalsIgnoreCase("reload") || args[1].equalsIgnoreCase("enabled") || args[1].equalsIgnoreCase("disabled"))) {
            List<String> pluginNames = new ArrayList<>();
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (!plugin.getName().equalsIgnoreCase(getName())) {
                    pluginNames.add(plugin.getName());
                }
            }
            return filterSuggestions(pluginNames, args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterSuggestions(List<String> values, String input) {
        String loweredInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(loweredInput))
            .toList();
    }
}
