package com.vyntra.factions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class VyntraFactions extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "join", "delete", "leave", "info", "accept", "deny", "requests");

    private FactionManager factionManager;

    @Override
    public void onEnable() {
        this.factionManager = new FactionManager(this);
        this.factionManager.load();

        getCommand("faction").setExecutor(this);
        getCommand("faction").setTabCompleter(this);

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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("faction")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) results.add(sub);
            }
            return results;
        }

        if (args.length == 2 && sender instanceof Player player) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (sub.equals("join") || sub.equals("info")) {
                List<String> results = new ArrayList<>();
                for (String name : factionManager.getAllFactionNames()) {
                    if (name.toLowerCase().startsWith(partial)) results.add(name);
                }
                return results;
            }
            if (sub.equals("accept") || sub.equals("deny")) {
                List<String> results = new ArrayList<>();
                for (UUID id : factionManager.getPendingRequests(player.getUniqueId())) {
                    String name = Bukkit.getOfflinePlayer(id).getName();
                    if (name != null && name.toLowerCase().startsWith(partial)) results.add(name);
                }
                return results;
            }
        }

        // No suggestions for "create" names, "delete", "leave", or "requests" - nothing useful to autocomplete there.
        return Collections.emptyList();
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
            msg(player, ChatColor.RED, "Usage: /faction <create|join|delete|leave|info|accept|deny|requests> [name]");
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
            case "accept" -> handleAccept(player, name);
            case "deny" -> handleDeny(player, name);
            case "requests" -> handleRequests(player);
            default -> msg(player, ChatColor.RED, "Usage: /faction <create|join|delete|leave|info|accept|deny|requests> [name]");
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
        FactionManager.Result result = factionManager.requestJoin(player.getUniqueId(), name);
        switch (result) {
            case REQUEST_SENT -> {
                msg(player, ChatColor.GREEN, "Join request sent to '" + name + "'. Waiting on the owner to approve it.");
                factionManager.getFactionByName(name).ifPresent(faction -> {
                    Player owner = Bukkit.getPlayer(faction.getOwner());
                    if (owner != null) {
                        msg(owner, ChatColor.GOLD, player.getName() + " wants to join your faction. "
                                + "Use /faction accept " + player.getName() + " or /faction deny " + player.getName() + ".");
                    }
                });
            }
            case NOT_FOUND -> msg(player, ChatColor.RED, "No faction with that name exists.");
            case ALREADY_IN_FACTION -> msg(player, ChatColor.YELLOW, "You're already in that faction.");
            case ALREADY_REQUESTED -> msg(player, ChatColor.YELLOW, "You've already requested to join that faction.");
            case ALREADY_OWNS_FACTION -> msg(player, ChatColor.RED,
                    "You own a faction. Delete it with /faction delete before joining another.");
            default -> msg(player, ChatColor.RED, "Could not send join request.");
        }
    }

    private void handleAccept(Player player, String targetName) {
        if (targetName == null) {
            msg(player, ChatColor.RED, "Usage: /faction accept <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Optional<Faction> result = factionManager.acceptRequest(player.getUniqueId(), target.getUniqueId());
        if (result.isPresent()) {
            msg(player, ChatColor.GREEN, targetName + " is now in your faction.");
            Player online = target.getPlayer();
            if (online != null) {
                msg(online, ChatColor.GREEN, "Your request to join '" + result.get().getName() + "' was accepted!");
            }
        } else {
            msg(player, ChatColor.RED, "No pending request from that player (or you don't own a faction).");
        }
    }

    private void handleDeny(Player player, String targetName) {
        if (targetName == null) {
            msg(player, ChatColor.RED, "Usage: /faction deny <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        boolean removed = factionManager.denyRequest(player.getUniqueId(), target.getUniqueId());
        if (removed) {
            msg(player, ChatColor.GREEN, "Denied " + targetName + "'s request.");
            Player online = target.getPlayer();
            if (online != null) {
                msg(online, ChatColor.RED, "Your request to join that faction was denied.");
            }
        } else {
            msg(player, ChatColor.RED, "No pending request from that player (or you don't own a faction).");
        }
    }

    private void handleRequests(Player player) {
        List<UUID> pending = factionManager.getPendingRequests(player.getUniqueId());
        if (pending.isEmpty()) {
            msg(player, ChatColor.GRAY, "No pending join requests.");
            return;
        }
        String names = pending.stream()
                .map(id -> Bukkit.getOfflinePlayer(id).getName())
                .collect(Collectors.joining(", "));
        msg(player, ChatColor.GOLD, "Pending requests (" + pending.size() + "): " + names);
        msg(player, ChatColor.GRAY, "Use /faction accept <player> or /faction deny <player>.");
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
