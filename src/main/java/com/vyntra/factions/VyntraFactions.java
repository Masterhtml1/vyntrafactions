package com.vyntra.factions;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.stream.Collectors;

public class VyntraFactions extends JavaPlugin implements CommandExecutor {

    private FactionManager factionManager;

    @Override
    public void onEnable() {
        this.factionManager = new FactionManager(this);
        this.factionManager.load();

        getCommand("faction").setExecutor(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FactionsExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI (%vyntrafactions_tag%).");
        } else {
            getLogger().warning("PlaceholderAPI not found - faction tags will not show in TAB/scoreboards.");
        }

        getLogger().info("VyntraFactions enabled.");
    }

    @Override
    public void onDisable() {
        if (factionManager != null) {
            factionManager.save();
        }
        getLogger().info("VyntraFactions disabled.");
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("faction")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            msg(player, ChatColor.RED, "Usage: /faction <create|join|delete|leave|info> [name]");
            return true;
        }

        String sub = args[0].toLowerCase();
        String name = args.length >= 2 ? args[1] : null;

        switch (sub) {
            case "create" -> handleCreate(player, name);
            case "join" -> handleJoin(player, name);
            case "delete", "del" -> handleDelete(player);
            case "leave" -> handleLeave(player);
            case "info" -> handleInfo(player, name);
            default -> msg(player, ChatColor.RED, "Usage: /faction <create|join|delete|leave|info> [name]");
        }
        return true;
    }

    private void handleCreate(Player player, String name) {
        if (name == null) {
            msg(player, ChatColor.RED, "Usage: /faction create <name>");
            return;
        }
        FactionManager.Result result = factionManager.createFaction(player.getUniqueId(), name);
        switch (result) {
            case OK -> msg(player, ChatColor.GREEN, "Faction '" + name + "' created! You are the owner.");
            case INVALID_NAME -> msg(player, ChatColor.RED,
                    "Invalid name. Use 1-" + FactionManager.MAX_NAME_LENGTH + " letters, numbers, or underscores only.");
            case NAME_TAKEN -> msg(player, ChatColor.RED, "A faction with that name already exists.");
            case ALREADY_OWNS_FACTION -> msg(player, ChatColor.RED,
                    "You already own a faction. Delete it first with /faction delete.");
            default -> msg(player, ChatColor.RED, "Could not create faction.");
        }
    }

    private void handleJoin(Player player, String name) {
        if (name == null) {
            msg(player, ChatColor.RED, "Usage: /faction join <name>");
            return;
        }
        FactionManager.Result result = factionManager.joinFaction(player.getUniqueId(), name);
        switch (result) {
            case OK -> msg(player, ChatColor.GREEN, "You joined faction '" + name + "'.");
            case NOT_FOUND -> msg(player, ChatColor.RED, "No faction with that name exists.");
            case ALREADY_IN_FACTION -> msg(player, ChatColor.YELLOW, "You're already in that faction.");
            case ALREADY_OWNS_FACTION -> msg(player, ChatColor.RED,
                    "You own a faction. Delete it with /faction delete before joining another.");
            default -> msg(player, ChatColor.RED, "Could not join faction.");
        }
    }

    private void handleDelete(Player player) {
        Optional<Faction> owned = factionManager.getOwnedFaction(player.getUniqueId());
        String name = owned.map(Faction::getName).orElse(null);
        FactionManager.Result result = factionManager.deleteFaction(player.getUniqueId());
        switch (result) {
            case OK -> msg(player, ChatColor.GREEN, "Faction '" + name + "' deleted. You can create a new one.");
            case NOT_OWNER -> msg(player, ChatColor.RED, "You don't own a faction.");
            default -> msg(player, ChatColor.RED, "Could not delete faction.");
        }
    }

    private void handleLeave(Player player) {
        FactionManager.Result result = factionManager.leaveFaction(player.getUniqueId());
        switch (result) {
            case OK -> msg(player, ChatColor.GREEN, "You left your faction.");
            case NOT_FOUND -> msg(player, ChatColor.RED, "You're not in a faction.");
            case OWNER_CANNOT_LEAVE -> msg(player, ChatColor.RED,
                    "You own this faction — use /faction delete instead of leaving.");
            default -> msg(player, ChatColor.RED, "Could not leave faction.");
        }
    }

    private void handleInfo(Player player, String name) {
        Optional<Faction> faction = (name != null)
                ? factionManager.getFactionByName(name)
                : factionManager.getFactionOfPlayer(player.getUniqueId());

        if (faction.isEmpty()) {
            msg(player, ChatColor.RED, "Faction not found (or you're not in one — try /faction info <name>).");
            return;
        }
        Faction f = faction.get();
        String ownerName = getServer().getOfflinePlayer(f.getOwner()).getName();
        String members = f.getMembers().stream()
                .map(id -> getServer().getOfflinePlayer(id).getName())
                .collect(Collectors.joining(", "));
        player.sendMessage(f.getTag() + ChatColor.GOLD + " Faction Info");
        msg(player, ChatColor.GRAY, "Owner: " + ownerName);
        msg(player, ChatColor.GRAY, "Members (" + f.getMembers().size() + "): " + members);
    }

    private void msg(Player player, ChatColor color, String text) {
        player.sendMessage(color + text);
    }
}
