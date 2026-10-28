package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class BlossomItems {
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Blossom.id("blossom"));

	public static final Item SAKURA_PETAL = register("sakura_petal", Item::new, new Item.Properties());

	private BlossomItems() {
	}

	public static void init() {
		// every blossom item shows up in our own tab, in registration order
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.blossom"))
				.icon(() -> new ItemStack(SAKURA_PETAL))
				.displayItems((params, output) -> BuiltInRegistries.ITEM.stream()
						.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Blossom.MOD_ID))
						.forEach(output::accept))
				.build());
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Blossom.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
