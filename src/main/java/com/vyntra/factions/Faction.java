package com.vyntra.factions;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Faction {

    private final String name;
    private final UUID owner;
    private final ChatColor color;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Faction(String name, UUID owner, ChatColor color) {
        this.name = name;
        this.owner = owner;
        this.color = color;
        this.members.add(owner);
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public ChatColor getColor() {
        return color;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    /** e.g. "&a[Vyntra]" with the faction's assigned color, no trailing space. */
    public String getTag() {
        return color.toString() + "[" + name + "]";
    }
}
