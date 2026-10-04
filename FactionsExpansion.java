package com.vyntra.factions;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class FactionsExpansion extends PlaceholderExpansion {

    private final VyntraFactions plugin;

    public FactionsExpansion(VyntraFactions plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vyntrafactions";
    }

    @Override
    public String getAuthor() {
        return "Claude";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * %vyntrafactions_tag%  -> "&a[Vyntra]" (colored, with brackets) or "" if not in a faction
     * %vyntrafactions_name% -> "Vyntra" (plain name, no color/brackets) or "" if not in a faction
     */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("tag")) {
            return plugin.getFactionManager().getFactionOfPlayer(player.getUniqueId())
                    .map(Faction::getTag)
                    .orElse("");
        }
        if (params.equalsIgnoreCase("name")) {
            return plugin.getFactionManager().getFactionOfPlayer(player.getUniqueId())
                    .map(Faction::getName)
                    .orElse("");
        }
        return "";
    }
}
