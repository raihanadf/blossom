package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.cooking.DishItem;
import com.raihanadf.blossom.motor.PlaceEntityItem;
import com.raihanadf.blossom.sports.RacketItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

public final class BlossomItems {
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Blossom.id("blossom"));

	// the pink heart is blossom's logo: the creative tab icon (same art as the mod icon)
	public static final Item BLOSSOM_HEART = register("blossom_heart", Item::new, new Item.Properties());

	// cooking tools and chopped ingredients
	public static final Item KITCHEN_KNIFE = register("kitchen_knife", Item::new, new Item.Properties().durability(250));
	// pentungan: the kampung satpam's night stick, a light wooden weapon
	public static final Item BATON = register("baton", Item::new, new Item.Properties().sword(ToolMaterial.WOOD, 3.0f, -2.2f));
	public static final Item CARROT_SLICES = register("carrot_slices", Item::new, new Item.Properties());
	public static final Item POTATO_WEDGES = register("potato_wedges", Item::new, new Item.Properties());
	public static final Item APPLE_SLICES = register("apple_slices", Item::new, new Item.Properties().food(food(2, 0.3f)));
	public static final Item FISH_FILLET = register("fish_fillet", Item::new, new Item.Properties());
	public static final Item NOODLES = register("noodles", Item::new, new Item.Properties());
	public static final Item CHICKEN_PIECES = register("chicken_pieces", Item::new, new Item.Properties());
	public static final Item BEEF_SLICES = register("beef_slices", Item::new, new Item.Properties());

	// a biscuit any friendly animal loves, see pets/PetFeeding
	public static final Item PET_TREAT = register("pet_treat", Item::new, new Item.Properties());

	// rides and sports
	public static final Item MOTOR = register("motor", p -> new PlaceEntityItem(BlossomEntities.MOTOR, p), new Item.Properties().stacksTo(1));
	public static final Item FOOTBALL = register("football", p -> new PlaceEntityItem(BlossomEntities.FOOTBALL, p), new Item.Properties().stacksTo(16));
	public static final Item RACKET = register("racket", p -> new RacketItem(p, false), new Item.Properties().stacksTo(1));
	public static final Item SHUTTLECOCK = register("shuttlecock", p -> new RacketItem(p, true), new Item.Properties().stacksTo(16));

	// dishes from the cooking pot, eaten from a bowl
	public static final Item CARROT_SOUP = dish("carrot_soup", 8, 0.7f);
	public static final Item FISH_SOUP = dish("fish_soup", 10, 0.8f);
	public static final Item VEGGIE_STEW = dish("veggie_stew", 10, 0.75f);
	public static final Item APPLE_PORRIDGE = dish("apple_porridge", 8, 0.6f);
	public static final Item BERRY_COCOA = dish("berry_cocoa", 6, 0.6f);
	public static final Item SOTO_AYAM = dish("soto_ayam", 12, 0.9f);
	public static final Item SOTO_SAPI = dish("soto_sapi", 12, 0.95f);
	public static final Item MIE_GORENG = dish("mie_goreng", 10, 0.8f);
	public static final Item NASI_GORENG = dish("nasi_goreng", 12, 0.85f);

	private BlossomItems() {
	}

	public static void init() {
		// every blossom item shows up in our own tab, in registration order, except the heart that only serves as its icon
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.blossom"))
				.icon(() -> new ItemStack(BLOSSOM_HEART))
				.displayItems((params, output) -> BuiltInRegistries.ITEM.stream()
						.filter(item -> item != BLOSSOM_HEART && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Blossom.MOD_ID))
						.forEach(output::accept))
				.build());
	}

	public static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Blossom.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item dish(String name, int nutrition, float saturation) {
		return register(name, DishItem::new, new Item.Properties().stacksTo(16).food(food(nutrition, saturation)).usingConvertsTo(Items.BOWL));
	}

	private static FoodProperties food(int nutrition, float saturation) {
		return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}
}
