package com.raihanadf.blossom.cooking;

import static com.raihanadf.blossom.registry.BlossomBlocks.RICE;
import static com.raihanadf.blossom.registry.BlossomItems.APPLE_PORRIDGE;
import static com.raihanadf.blossom.registry.BlossomItems.BEEF_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.CHICKEN_PIECES;
import static com.raihanadf.blossom.registry.BlossomItems.MIE_GORENG;
import static com.raihanadf.blossom.registry.BlossomItems.NASI_GORENG;
import static com.raihanadf.blossom.registry.BlossomItems.NOODLES;
import static com.raihanadf.blossom.registry.BlossomItems.SOTO_AYAM;
import static com.raihanadf.blossom.registry.BlossomItems.SOTO_SAPI;
import static com.raihanadf.blossom.registry.BlossomItems.APPLE_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.BERRY_COCOA;
import static com.raihanadf.blossom.registry.BlossomItems.CARROT_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.CARROT_SOUP;
import static com.raihanadf.blossom.registry.BlossomItems.FISH_FILLET;
import static com.raihanadf.blossom.registry.BlossomItems.FISH_SOUP;
import static com.raihanadf.blossom.registry.BlossomItems.POTATO_WEDGES;
import static com.raihanadf.blossom.registry.BlossomItems.VEGGIE_STEW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// what the cutting board and the cooking pot can make
public final class KitchenRecipes {
	public record PotRecipe(List<Item> ingredients, Item dish, int servings) {
	}

	// chopping one input gives this stack
	public static final Map<Item, ItemStack> BOARD = Map.of(
			Items.CARROT, new ItemStack(CARROT_SLICES, 2),
			Items.POTATO, new ItemStack(POTATO_WEDGES, 2),
			Items.APPLE, new ItemStack(APPLE_SLICES, 2),
			Items.COD, new ItemStack(FISH_FILLET, 2),
			Items.SALMON, new ItemStack(FISH_FILLET, 2),
			Items.WHEAT, new ItemStack(NOODLES, 2),
			Items.CHICKEN, new ItemStack(CHICKEN_PIECES, 2),
			Items.BEEF, new ItemStack(BEEF_SLICES, 2));

	// ingredients must match exactly, order does not matter
	public static final List<PotRecipe> POT = List.of(
			new PotRecipe(List.of(CARROT_SLICES, CARROT_SLICES, POTATO_WEDGES), CARROT_SOUP, 2),
			new PotRecipe(List.of(FISH_FILLET, FISH_FILLET, CARROT_SLICES, POTATO_WEDGES), FISH_SOUP, 3),
			new PotRecipe(List.of(CARROT_SLICES, POTATO_WEDGES, Items.BEETROOT, Items.BROWN_MUSHROOM), VEGGIE_STEW, 3),
			new PotRecipe(List.of(Items.WHEAT, Items.WHEAT, APPLE_SLICES, Items.SUGAR), APPLE_PORRIDGE, 2),
			new PotRecipe(List.of(Items.COCOA_BEANS, Items.SUGAR, Items.SWEET_BERRIES), BERRY_COCOA, 2),
			// the indonesian kitchen
			new PotRecipe(List.of(CHICKEN_PIECES, CHICKEN_PIECES, RICE, Items.EGG, POTATO_WEDGES), SOTO_AYAM, 3),
			new PotRecipe(List.of(BEEF_SLICES, BEEF_SLICES, RICE, POTATO_WEDGES, CARROT_SLICES), SOTO_SAPI, 3),
			new PotRecipe(List.of(NOODLES, NOODLES, Items.EGG, CHICKEN_PIECES), MIE_GORENG, 2),
			new PotRecipe(List.of(RICE, RICE, Items.EGG, CHICKEN_PIECES, CARROT_SLICES), NASI_GORENG, 2));

	private KitchenRecipes() {
	}

	public static boolean isPotIngredient(ItemStack stack) {
		return POT.stream().anyMatch(recipe -> recipe.ingredients().contains(stack.getItem()));
	}

	public static PotRecipe findPotRecipe(List<ItemStack> contents) {
		List<Item> items = contents.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).toList();
		for (PotRecipe recipe : POT) {
			List<Item> left = new ArrayList<>(recipe.ingredients());
			if (items.size() == left.size() && items.stream().allMatch(left::remove)) {
				return recipe;
			}
		}
		return null;
	}
}
