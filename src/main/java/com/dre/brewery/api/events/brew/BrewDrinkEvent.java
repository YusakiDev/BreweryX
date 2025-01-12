/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.api.events.brew;

import com.dre.brewery.BPlayer;
import com.dre.brewery.Brew;
import com.dre.brewery.utility.PermissionUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Player Drinks a Brew.
 * <p>The amount of alcohol and quality that will be added to the player can be get/set here
 * <p>If cancelled the drinking will fail silently
 */
@Getter
@Setter
public class BrewDrinkEvent extends BrewEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final BPlayer bPlayer;
    private int addedAlcohol;
    private int quality;
    private boolean cancelled;

    @Nullable // Null if drinking from command
    private PlayerItemConsumeEvent predecessorEvent;

    public BrewDrinkEvent(Brew brew, ItemMeta meta, Player player, BPlayer bPlayer, @Nullable PlayerItemConsumeEvent predecessor) {
        super(brew, meta);
        this.player = player;
        this.bPlayer = bPlayer;
        addedAlcohol = calcAlcWSensitivity(brew.getOrCalcAlc());
        quality = brew.getQuality();
        predecessorEvent = predecessor;
    }

    /**
     * Calculate the Alcohol to add to the player using his sensitivity permission (if existing)
     *
     * <p>If the player has been given the brewery.sensitive.xx permission, will factor in the sensitivity to the given alcohol amount.
     * <p>Will return the calculated value without changing the event
     *
     * @param alc The base amount of alcohol
     * @return The amount of alcohol given the players alcohol-sensitivity
     */
    @Contract(pure = true)
    public int calcAlcWSensitivity(int alc) {
        int sensitive = PermissionUtil.getDrinkSensitive(player);
        if (sensitive == 0) {
            alc = 0;
        } else if (sensitive > 0) {
            alc *= (int) (((float) sensitive) / 100f);
        }
        return alc;
    }

    public void setQuality(int quality) {
        if (quality > 10 || quality < 0) {
            throw new IllegalArgumentException("Quality must be in range from 0 to 10");
        }
        this.quality = quality;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    // Required by Bukkit
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
