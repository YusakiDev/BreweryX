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

package com.dre.brewery.recipe;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.configuration.sector.capsule.ConfigCustomItem;
import com.dre.brewery.integration.Hook;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MaterialUtil;
import com.dre.brewery.utility.MinecraftVersion;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Item that can be used in a Recipe.
 * <p>They are not necessarily only loaded from config
 * <p>They are immutable if used in a recipe. If one implements Ingredient,
 * it can be used as mutable copy directly in a
 * BIngredients. Otherwise, it needs to be converted to an Ingredient
 */
public abstract class RecipeItem implements Cloneable {

    private static final MinecraftVersion VERSION = BreweryPlugin.getMCVersion();

    private String cfgId;
    private int amount;
    private boolean immutable = false;


    /**
     * Does this RecipeItem match the given ItemStack?
     * <p>Used to determine if the given item corresponds to this recipeitem
     *
     * @param item The ItemStack for comparison
     * @return True if the given item matches this recipeItem
     */
    public abstract boolean matches(ItemStack item);

    /**
     * Does this Item match the given Ingredient?
     * <p>A RecipeItem matches an Ingredient if all required info of the RecipeItem are fulfilled on the Ingredient
     * <br>This does not imply that the same holds the other way round, as the ingredient item might have more info than needed
     *
     * @param ingredient The ingredient that needs to fulfill the requirements
     * @return True if the ingredient matches the required info of this
     */
    public abstract boolean matches(Ingredient ingredient);

    /**
     * Get the Corresponding Ingredient Item. For Items implementing Ingredient, just getMutableCopy()
     * <p>This is called when this recipe item is added to a BIngredients
     *
     * @param forItem The ItemStack that has previously matched this RecipeItem. Used if the resulting Ingredient needs more info from the ItemStack
     * @return The IngredientItem corresponding to this RecipeItem
     */
    @NotNull
    public abstract Ingredient toIngredient(ItemStack forItem);

    /**
     * Gets a Generic Ingredient for this recipe item
     */
    @NotNull
    public abstract Ingredient toIngredientGeneric();

    /**
     * @return True if this recipeItem has one or more materials that could classify an item. if true, getMaterials() is NotNull
     */
    public abstract boolean hasMaterials();

    /**
     * @return List of one or more Materials this recipeItem uses.
     */
    @Nullable
    public abstract List<Material> getMaterials();

    /**
     * @return The Id this Item uses in the config in the custom-items section
     */
    public String getConfigId() {
        return cfgId;
    }

    /**
     * @return The Amount of this Item in a Recipe
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Set the Amount of this Item in a Recipe.
     * <p>The amount can not be set on an existing item in a recipe or existing custom item.
     * <br>To change amount you need to use getMutableCopy() and change the amount on the copy
     *
     * @param amount The new amount
     */
    public void setAmount(int amount) {
        if (immutable) throw new IllegalStateException("Setting amount only possible on mutable copy");
        this.amount = amount;
    }

    /**
     * Makes this Item immutable, for example when loaded from config. Used so if this is added to BIngredients,
     * it needs to be cloned before changing anything like amount
     */
    public void makeImmutable() {
        immutable = true;
    }

    /**
     * Gets a shallow clone of this RecipeItem whose fields like amount can be changed.
     *
     * @return A mutable copy of this
     */
    public RecipeItem getMutableCopy() {
        try {
            RecipeItem i = (RecipeItem) super.clone();
            i.immutable = false;
            return i;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Tries to find a matching RecipeItem for this item. It checks custom items and if it has found a unique custom item
     * it will return that. If there are multiple matching custom items, a new CustomItem with all item info is returned.
     * <br>If there is no matching CustomItem, it will return a SimpleItem with the items type
     *
     * @param item      The Item for which to find a matching RecipeItem
     * @param acceptAll If true it will accept any item and return a SimpleItem even if not on the accepted list
     *                  <br>If false it will return null if the item is not acceptable by the Cauldron
     * @return The Matched CustomItem, new CustomItem with all item info or SimpleItem
     */
    @Nullable
    @Contract("_, true -> !null")
    public static RecipeItem getMatchingRecipeItem(ItemStack item, boolean acceptAll) {
        RecipeItem rItem = null;
        boolean multiMatch = false;
        for (RecipeItem ri : BCauldronRecipe.acceptedCustom) {
            // If we already have a multi match, only check if there is a PluginItem that matches more strictly
            if (!multiMatch || (ri instanceof PluginItem)) {
                if (ri.matches(item)) {
                    // If we match a plugin item, that's a very strict match, so immediately return it
                    if (ri instanceof PluginItem) {
                        return ri;
                    }
                    if (rItem == null) {
                        rItem = ri;
                    } else {
                        multiMatch = true;
                    }
                }
            }
        }
        if (multiMatch) {
            // We have multiple Custom Items matching, so just store all item info
            return new CustomItem(item);
        }
        if (rItem == null && (acceptAll || BCauldronRecipe.acceptedSimple.contains(item.getType()))) {
            // No Custom item found
            if (VERSION.isOrLater(MinecraftVersion.V1_13)) {
                return new SimpleItem(item.getType());
            } else {
                @SuppressWarnings("deprecation")
                short durability = item.getDurability();
                return new SimpleItem(item.getType(), durability);
            }
        }
        return rItem;
    }

    @Nullable
    public static RecipeItem fromConfigCustom(String id, ConfigCustomItem configCustomItem) {
        RecipeItem rItem;
        if (configCustomItem.getMatchAny() != null && configCustomItem.getMatchAny()) {
            rItem = new CustomMatchAnyItem();
        } else {
            rItem = new CustomItem();
        }

        rItem.cfgId = id;
        rItem.immutable = true;

        List<Material> materials = BUtil.getListSafely(configCustomItem.getMaterial(), Material.class);
        List<String> names = BUtil.colorArrayList(BUtil.getListSafely(configCustomItem.getName()));
        List<String> lore = BUtil.colorArrayList(BUtil.getListSafely(configCustomItem.getLore()));
        List<Integer> customModelDatas = BUtil.getListSafely(configCustomItem.getCustomModelData());

        if ((materials == null || materials.isEmpty()) && (names == null || names.isEmpty()) && (lore == null || lore.isEmpty()) && (customModelDatas == null || customModelDatas.isEmpty())) {
            return null;
        }

        if (rItem instanceof CustomItem cItem) {
            if (!materials.isEmpty()) {
                cItem.setMat(materials.get(0));
            }
            if (!names.isEmpty()) {
                cItem.setName(names.get(0));
            }
            cItem.setLore(lore);
            if (!customModelDatas.isEmpty()) {
                cItem.setCustomModelData(customModelDatas.get(0));
            }
        } else {
            CustomMatchAnyItem maItem = (CustomMatchAnyItem) rItem;
            maItem.setMaterials(materials);
            maItem.setNames(names);
            maItem.setLore(lore);
            maItem.setCustomModelDatas(customModelDatas);
        }

        return rItem;
    }

    @Nullable
    protected static List<Material> loadMaterials(List<String> ingredientsList) {
        List<Material> materials = new ArrayList<>(ingredientsList.size());
        for (String item : ingredientsList) {
            String[] ingredParts = item.split("/");
            if (ingredParts.length == 2) {
                Logging.errorLog("Item Amount can not be specified for Custom Items: " + item);
                return null;
            }
            Material mat = MaterialUtil.getMaterialSafely(ingredParts[0]);

            if (mat == null && VERSION.isOrEarlier(MinecraftVersion.V1_14) && ingredParts[0].equalsIgnoreCase("cornflower")) {
                // Using this in default custom-items, but will error on < 1.14
                materials.add(Material.BEDROCK);
                continue;
            }

            if (mat == null && Hook.VAULT.isEnabled()) {
                try {
                    net.milkbowl.vault.item.ItemInfo vaultItem = net.milkbowl.vault.item.Items.itemByString(ingredParts[0]);
                    if (vaultItem != null) {
                        mat = vaultItem.getType();
                    }
                } catch (Exception e) {
                    Logging.errorLog("Could not check vault for Item Name", e);
                }
            }
            if (mat != null) {
                materials.add(mat);
            } else {
                Logging.errorLog("Unknown Material: " + ingredParts[0]);
                return null;
            }
        }
        return materials;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeItem that)) return false;
        return amount == that.amount &&
            immutable == that.immutable &&
            Objects.equals(cfgId, that.cfgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cfgId, amount, immutable);
    }

    @Override
    public String toString() {
        return "RecipeItem{(" + getClass().getSimpleName() + ") ID: " + getConfigId() + " Materials: " + (hasMaterials() ? getMaterials().size() : 0) + " Amount: " + getAmount();
    }

    /**
     * Converts this RecipeItem to a String that can be used in a config
     *
     * @return The config String
     */
    public String toConfigString() {
        String amtAppend = "/" + this.getAmount();
        if (this instanceof SimpleItem simpleItem) {
            return simpleItem.getMaterial().toString().toLowerCase() + amtAppend;
        } else if (this instanceof PluginItem pluginItem) {
            return pluginItem.getPlugin() + ":" + pluginItem.getItemId() + amtAppend;
        } else if (this instanceof CustomItem || this instanceof CustomMatchAnyItem) {
            return this.getConfigId() + amtAppend;
        } else {
            throw new IllegalStateException("Unknown RecipeItem Type!");
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
