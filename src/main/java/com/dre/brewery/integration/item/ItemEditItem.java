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

package com.dre.brewery.integration.item;

import com.dre.brewery.integration.Hook;
import com.dre.brewery.recipe.Ingredient;
import com.dre.brewery.recipe.PluginItem;
import com.dre.brewery.recipe.BCauldronRecipe;
import com.dre.brewery.utility.Logging;
import emanondev.itemedit.ItemEdit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Integration for ItemEdit items
 * <p>
 * Since ItemEdit items are just regular items with custom properties (name, lore, etc.)
 * and don't have special NBT data, we need to use visual matching.
 */
public class ItemEditItem extends PluginItem {

    private ItemStack item;
    private static boolean initialized = false;

    public ItemEditItem() {
    }

    public ItemEditItem(String plugin, String itemId, ItemStack item) {
        super(plugin, itemId);
        this.item = item != null ? item.clone() : null;
    }

    @Override
    protected void onConstruct() {
        // We intentionally don't cache the item here, we'll look it up when needed
        // This prevents issues when items are updated in ItemEdit
    }

    /**
     * Override matches to always get the latest version of the item from ItemEdit storage
     * This way updates to the item will be recognized immediately
     */
    @Override
    public boolean matches(ItemStack itemToMatch) {
        // Always get the freshest version of the item from ItemEdit
        ItemStack freshItem = getFreshItem();

        if (freshItem == null) {
            return false;
        }

        // Use visual matching (name, lore, model data, etc.)
        return freshItem.isSimilar(itemToMatch);
    }

    /**
     * Gets the display item, always retrieving the latest version from ItemEdit
     */
    public ItemStack getDisplayItem() {
        ItemStack freshItem = getFreshItem();
        return freshItem != null ? freshItem.clone() : null;
    }

    /**
     * Helper method to always get the latest version of the item from ItemEdit
     */
    private ItemStack getFreshItem() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemEdit")) {
            Logging.errorLog("ItemEdit plugin not enabled, but trying to use ItemEdit item: " + getItemId());
            return null;
        }

        try {
            // Try server storage first
            ItemStack freshItem = ItemEdit.get().getServerStorage().getItem(getItemId());

            // If not found in server storage, check player storage
            if (freshItem == null) {
                for (OfflinePlayer player : ItemEdit.get().getPlayerStorage().getPlayers()) {
                    freshItem = ItemEdit.get().getPlayerStorage().getItem(player, getItemId());
                    if (freshItem != null) {
                        break;
                    }
                }
            }

            return freshItem;
        } catch (Exception e) {
            Logging.errorLog("Could not get ItemEdit item: " + getItemId(), e);
            return null;
        }
    }

    /**
     * The key function that makes ItemEdit work with cauldrons.
     * Registers all material types with the accepted materials list.
     *
     * This is needed because ItemEdit items don't have special NBT data
     * and would be rejected by the cauldron's quick material check otherwise.
     */
    public static void register() {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemEdit")) {
            // The PluginItem registration is already done in ConfigManager
            // We only need to add all materials to the accepted list
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isAir()) {
                    BCauldronRecipe.getAcceptedMaterials().add(material);
                }
            }

            Logging.log("ItemEdit integration enabled - all materials added to accepted list");
        }
    }

    /**
     * Unregister this Item Type from the PluginItem loader
     * Note: This is not normally necessary as the plugin handles this
     */
    public static void unregister() {
        PluginItem.unRegisterForConfig("itemedit");
    }

    /**
     * Override to ensure ingredient references are stored, not actual item properties
     * When used later for matching, this will check the latest item state
     */
    @NotNull
    @Override
    public Ingredient toIngredient(ItemStack forItem) {
        // Return a copy of this - the item will be loaded fresh when needed
        return (Ingredient) getMutableCopy();
    }

    /**
     * Override to ensure ingredient references are stored, not actual item properties
     * When used later for matching, this will check the latest item state
     */
    @NotNull
    @Override
    public Ingredient toIngredientGeneric() {
        // Return a copy of this - the item will be loaded fresh when needed
        return (Ingredient) getMutableCopy();
    }
}
