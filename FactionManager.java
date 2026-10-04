package com.vyntra.factions;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class FactionManager {

    public static final int MAX_NAME_LENGTH = 10;

    // Visually distinct colors to randomly assign to new factions.
    private static final ChatColor[] PALETTE = {
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN,
            ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE, ChatColor.DARK_PURPLE,
            ChatColor.DARK_AQUA, ChatColor.DARK_GREEN, ChatColor.GRAY, ChatColor.WHITE
    };

    private final Random random = new Random();

    // Keyed by lowercase faction name
    private final Map<String, Faction> factionsByName = new LinkedHashMap<>();
    // Keyed by player UUID -> lowercase faction name they belong to
    private final Map<UUID, String> membership = new LinkedHashMap<>();
    // Keyed by lowercase faction name -> set of player UUIDs awaiting owner approval
    private final Map<String, Set<UUID>> pendingRequests = new LinkedHashMap<>();

    private final VyntraFactions plugin;
    private final File file;

    public FactionManager(VyntraFactions plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "factions.yml");
    }

    // ---- Queries ----

    public Optional<Faction> getFactionOfPlayer(UUID playerId) {
        String name = membership.get(playerId);
        if (name == null) return Optional.empty();
        return Optional.ofNullable(factionsByName.get(name));
    }

    public Optional<Faction> getFactionByName(String name) {
        return Optional.ofNullable(factionsByName.get(name.toLowerCase()));
    }

    public boolean ownsAFaction(UUID playerId) {
        return factionsByName.values().stream().anyMatch(f -> f.getOwner().equals(playerId));
    }

    public List<String> getAllFactionNames() {
        List<String> names = new ArrayList<>();
        for (Faction f : factionsByName.values()) names.add(f.getName());
        return names;
    }

    public Optional<Faction> getOwnedFaction(UUID playerId) {
        return factionsByName.values().stream()
                .filter(f -> f.getOwner().equals(playerId))
                .findFirst();
    }

    /** Pending join requests for the faction owned by this player, empty list if they own none. */
    public List<UUID> getPendingRequests(UUID ownerId) {
        Optional<Faction> owned = getOwnedFaction(ownerId);
        if (owned.isEmpty()) return List.of();
        Set<UUID> set = pendingRequests.get(owned.get().getName().toLowerCase());
        return set == null ? List.of() : new ArrayList<>(set);
    }

    private ChatColor pickColor() {
        List<ChatColor> used = new ArrayList<>();
        for (Faction f : factionsByName.values()) used.add(f.getColor());

        List<ChatColor> unused = new ArrayList<>();
        for (ChatColor c : PALETTE) {
            if (!used.contains(c)) unused.add(c);
        }
        if (!unused.isEmpty()) {
            return unused.get(random.nextInt(unused.size()));
        }
        // All colors taken at least once, just pick any at random
        return PALETTE[random.nextInt(PALETTE.length)];
    }

    // ---- Mutations ----

    public enum Result {
        OK,
        REQUEST_SENT,
        NAME_TAKEN,
        INVALID_NAME,
        ALREADY_OWNS_FACTION,
        ALREADY_IN_FACTION,
        ALREADY_REQUESTED,
        NO_PENDING_REQUEST,
        NOT_FOUND,
        NOT_OWNER,
        OWNER_CANNOT_LEAVE
    }

    public Result createFaction(UUID playerId, String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH || !name.matches("[A-Za-z0-9_]+")) {
            return Result.INVALID_NAME;
        }
        if (ownsAFaction(playerId)) {
            return Result.ALREADY_OWNS_FACTION;
        }
        if (factionsByName.containsKey(name.toLowerCase())) {
            return Result.NAME_TAKEN;
        }
        // If they're a plain member of another faction, pull them out of it first
        membership.remove(playerId);

        Faction faction = new Faction(name, playerId, pickColor());
        factionsByName.put(name.toLowerCase(), faction);
        membership.put(playerId, name.toLowerCase());
        save();
        return Result.OK;
    }

    public Result deleteFaction(UUID playerId) {
        Optional<Faction> owned = getOwnedFaction(playerId);
        if (owned.isEmpty()) {
            return Result.NOT_OWNER;
        }
        Faction faction = owned.get();
        String key = faction.getName().toLowerCase();
        factionsByName.remove(key);
        membership.values().removeIf(v -> v.equals(key));
        pendingRequests.remove(key);
        save();
        return Result.OK;
    }

    /** Sends a join request to the faction's owner instead of joining immediately. */
    public Result requestJoin(UUID playerId, String name) {
        Optional<Faction> target = getFactionByName(name);
        if (target.isEmpty()) {
            return Result.NOT_FOUND;
        }
        if (ownsAFaction(playerId)) {
            // Owners must delete their own faction before joining another
            return Result.ALREADY_OWNS_FACTION;
        }
        String key = target.get().getName().toLowerCase();
        if (key.equals(membership.get(playerId))) {
            return Result.ALREADY_IN_FACTION;
        }
        Set<UUID> requests = pendingRequests.computeIfAbsent(key, k -> new LinkedHashSet<>());
        if (requests.contains(playerId)) {
            return Result.ALREADY_REQUESTED;
        }
        requests.add(playerId);
        save();
        return Result.REQUEST_SENT;
    }

    /** Owner approves a pending request, adding the player to their faction. Returns the faction on success. */
    public Optional<Faction> acceptRequest(UUID ownerId, UUID requesterId) {
        Optional<Faction> owned = getOwnedFaction(ownerId);
        if (owned.isEmpty()) return Optional.empty();
        String key = owned.get().getName().toLowerCase();

        Set<UUID> requests = pendingRequests.get(key);
        if (requests == null || !requests.remove(requesterId)) {
            return Optional.empty();
        }

        // Leave old faction (as a plain member) if any
        String oldKey = membership.get(requesterId);
        if (oldKey != null) {
            Faction old = factionsByName.get(oldKey);
            if (old != null) old.getMembers().remove(requesterId);
        }
        owned.get().getMembers().add(requesterId);
        membership.put(requesterId, key);
        save();
        return owned;
    }

    /** Owner rejects a pending request. Returns true if a request was actually removed. */
    public boolean denyRequest(UUID ownerId, UUID requesterId) {
        Optional<Faction> owned = getOwnedFaction(ownerId);
        if (owned.isEmpty()) return false;
        String key = owned.get().getName().toLowerCase();

        Set<UUID> requests = pendingRequests.get(key);
        if (requests == null || !requests.remove(requesterId)) {
            return false;
        }
        save();
        return true;
    }

    public Result leaveFaction(UUID playerId) {
        String key = membership.get(playerId);
        if (key == null) {
            return Result.NOT_FOUND;
        }
        Faction faction = factionsByName.get(key);
        if (faction != null && faction.getOwner().equals(playerId)) {
            return Result.OWNER_CANNOT_LEAVE;
        }
        if (faction != null) faction.getMembers().remove(playerId);
        membership.remove(playerId);
        save();
        return Result.OK;
    }

    // ---- Persistence ----

    public void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("factions")) return;

        for (String key : config.getConfigurationSection("factions").getKeys(false)) {
            String path = "factions." + key;
            String displayName = config.getString(path + ".display-name", key);
            String ownerStr = config.getString(path + ".owner");
            if (ownerStr == null) continue;
            UUID owner = UUID.fromString(ownerStr);

            ChatColor color;
            try {
                color = ChatColor.valueOf(config.getString(path + ".color", "WHITE"));
            } catch (IllegalArgumentException e) {
                color = ChatColor.WHITE;
            }

            Faction faction = new Faction(displayName, owner, color);
            faction.getMembers().clear();
            for (String memberStr : config.getStringList(path + ".members")) {
                UUID member = UUID.fromString(memberStr);
                faction.getMembers().add(member);
                membership.put(member, key);
            }
            factionsByName.put(key, faction);

            Set<UUID> requests = new LinkedHashSet<>();
            for (String reqStr : config.getStringList(path + ".pending-requests")) {
                requests.add(UUID.fromString(reqStr));
            }
            if (!requests.isEmpty()) {
                pendingRequests.put(key, requests);
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Faction> entry : factionsByName.entrySet()) {
            String path = "factions." + entry.getKey();
            Faction faction = entry.getValue();
            config.set(path + ".display-name", faction.getName());
            config.set(path + ".owner", faction.getOwner().toString());
            config.set(path + ".color", faction.getColor().name());
            config.set(path + ".members", faction.getMembers().stream().map(UUID::toString).toList());

            Set<UUID> requests = pendingRequests.get(entry.getKey());
            if (requests != null && !requests.isEmpty()) {
                config.set(path + ".pending-requests", requests.stream().map(UUID::toString).toList());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save factions.yml", e);
        }
    }
}
