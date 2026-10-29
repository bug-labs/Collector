package me.iamsamarth.riftcollector.ability;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Turns picked up items into what a furnace would make of them. The recipe list is read once,
 * lazily, because recipes are only complete after every plugin has finished loading.
 */
public final class AutoSmelter {

    private @Nullable Map<Material, ItemStack> results;

    /** The smelted form of the whole stack, or null when the item cannot be smelted. */
    public @Nullable ItemStack smelt(ItemStack stack) {
        ItemStack result = recipes().get(stack.getType());
        if (result == null) {
            return null;
        }
        ItemStack smelted = result.clone();
        smelted.setAmount(result.getAmount() * stack.getAmount());
        return smelted;
    }

    private Map<Material, ItemStack> recipes() {
        if (results != null) {
            return results;
        }
        Map<Material, ItemStack> found = new EnumMap<>(Material.class);
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof FurnaceRecipe furnace)) {
                continue;
            }
            // A recipe may accept a tag full of materials, so every accepted item is mapped.
            if (furnace.getInputChoice() instanceof RecipeChoice.MaterialChoice materials) {
                for (Material material : materials.getChoices()) {
                    found.putIfAbsent(material, furnace.getResult());
                }
            } else if (furnace.getInputChoice() instanceof RecipeChoice.ExactChoice exact) {
                for (ItemStack choice : exact.getChoices()) {
                    found.putIfAbsent(choice.getType(), furnace.getResult());
                }
            }
        }
        results = found;
        return found;
    }
}
