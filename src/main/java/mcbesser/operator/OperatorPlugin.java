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

    @Override
    public void onEnable() {
        this.menu = new OperatorMenu(this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        if (getCommand("operator") != null) {
            getCommand("operator").setTabCompleter(this);
        }
        getLogger().info("Operator enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Operator disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("operator.use")) {
            player.sendMessage(ChatColor.RED + "You must be an operator to use this menu.");
            return true;
        }

        if (args.length == 0) {
            menu.openMainMenu(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("tp")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /operator tp <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already at your own location.");
                return true;
            }

            player.teleport(target.getLocation());
            player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");
            return true;
        }

        if (subcommand.equals("tphere")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /operator tphere <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You cannot bring yourself to yourself.");
                return true;
            }

            target.teleport(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Brought " + target.getName() + " to your location.");
            target.sendMessage(ChatColor.YELLOW + "You were teleported to " + player.getName() + ".");
            return true;
        }

        if (subcommand.equals("plugin")) {
            if (args.length == 1) {
                menu.openPluginMenu(player, 0);
                return true;
            }

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /operator plugin <reload|enabled|disabled> <name>");
                return true;
            }

            String action = args[1].toLowerCase(Locale.ROOT);
            Plugin target = Bukkit.getPluginManager().getPlugin(args[2]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Plugin not found.");
                return true;
            }

            if (getName().equalsIgnoreCase(target.getName())) {
                player.sendMessage(ChatColor.RED + "Operator cannot manage itself.");
                return true;
            }

            if (action.equals("reload")) {
                boolean restarted = restartPlugin(target);
                if (restarted) {
                    player.sendMessage(ChatColor.GREEN + "Plugin " + target.getName() + " was restarted.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " could not be restarted. Check console.");
                }
                return true;
            }

            if (action.equals("enabled")) {
                boolean enabled = enablePlugin(target);
                if (enabled) {
                    player.sendMessage(ChatColor.GREEN + "Plugin " + target.getName() + " was enabled.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " could not be enabled. Check console.");
                }
                return true;
            }

            if (action.equals("disabled")) {
                boolean disabled = disablePlugin(target);
                if (disabled) {
                    player.sendMessage(ChatColor.YELLOW + "Plugin " + target.getName() + " was disabled.");
                } else {
                    player.sendMessage(ChatColor.RED + "Plugin " + target.getName() + " could not be disabled. Check console.");
                }
                return true;
            }

            player.sendMessage(ChatColor.RED + "Usage: /operator plugin <reload|enabled|disabled> <name>");
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /operator [tp <player>|tphere <player>|plugin <reload|enabled|disabled> <name>]");
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
