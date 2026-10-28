package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.cooking.CookingPotBlock;
import com.raihanadf.blossom.cooking.CookingPotBlockEntity;
import com.raihanadf.blossom.cooking.CuttingBoardBlock;
import com.raihanadf.blossom.cooking.CuttingBoardBlockEntity;
import com.raihanadf.blossom.cooking.RiceCropBlock;
import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.furniture.SeatBlock;
import com.raihanadf.blossom.furniture.ShapedBlock;
import com.raihanadf.blossom.sports.NetBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;

public final class BlossomBlocks {
	// every vanilla wood, furniture is made from (and looks like) the planks you craft it with
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "pale_oak",
			"mangrove", "cherry", "poplar", "bamboo", "crimson", "warped");

	public static final Block CUTTING_BOARD = register("cutting_board", CuttingBoardBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1f).sound(SoundType.WOOD).noOcclusion());
	public static final Block COOKING_POT = register("cooking_pot", CookingPotBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.COPPER).noOcclusion());

	public static final Map<String, Block> CHAIRS = new LinkedHashMap<>();
	public static final Map<String, Block> TABLES = new LinkedHashMap<>();
	public static final Block LAMP = register("lamp", LampBlock::new, wood("oak").lightLevel(LampBlock::lightLevel));

	// padi: the crop block has no item of its own, planting the rice grains places it (like wheat seeds)
	public static final Block RICE_CROP = registerBlock("rice_crop", RiceCropBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
	public static final Item RICE = BlossomItems.register("rice", properties -> new BlockItem(RICE_CROP, properties), new Item.Properties());

	// sports: the goal net scores when a football rolls into it, the badminton net is a thin panel
	public static final Block GOAL_NET = register("goal_net", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOL).strength(0.3f).sound(SoundType.WOOL).noCollision().noOcclusion());
	public static final Block SPORTS_NET = register("sports_net", NetBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOL).strength(0.3f).sound(SoundType.WOOL).noOcclusion());

	public static final BlockEntityType<CuttingBoardBlockEntity> CUTTING_BOARD_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Blossom.id("cutting_board"), new BlockEntityType<>(CuttingBoardBlockEntity::new, Set.of(CUTTING_BOARD)));
	public static final BlockEntityType<CookingPotBlockEntity> COOKING_POT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Blossom.id("cooking_pot"), new BlockEntityType<>(CookingPotBlockEntity::new, Set.of(COOKING_POT)));

	static {
		for (String wood : WOODS) {
			// chair seat is flat so a vanilla cushion can sit on it, table top is flush so a carpet makes a tablecloth
			CHAIRS.put(wood, register(wood + "_chair", p -> new SeatBlock(p, 10 / 16.0,
					Shapes.or(Block.box(2, 0, 2, 14, 10, 14), Block.box(2, 10, 12, 14, 19, 14))), wood(wood)));
			TABLES.put(wood, register(wood + "_table", p -> new ShapedBlock(p, Shapes.or(Block.box(1, 13, 1, 15, 16, 15),
					Block.box(2, 0, 2, 4, 13, 4), Block.box(12, 0, 2, 14, 13, 4), Block.box(2, 0, 12, 4, 13, 14), Block.box(12, 0, 12, 14, 13, 14))),
					wood(wood)));
		}
	}

	private BlossomBlocks() {
	}

	public static void init() {
		// breaking grass now and then turns up a few rice grains, the same way wheat seeds are found
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin() && (key.equals(Blocks.SHORT_GRASS.getLootTable().orElseThrow()) || key.equals(Blocks.TALL_GRASS.getLootTable().orElseThrow()))) {
				table.withPool(LootPool.lootPool().add(LootItem.lootTableItem(RICE)).when(LootItemRandomChanceCondition.randomChance(0.06f)));
			}
		});
	}

	private static BlockBehaviour.Properties wood(String wood) {
		SoundType sound = switch (wood) {
			case "cherry" -> SoundType.CHERRY_WOOD;
			case "bamboo" -> SoundType.BAMBOO_WOOD;
			case "crimson", "warped" -> SoundType.NETHER_WOOD;
			default -> SoundType.WOOD;
		};
		return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(sound).noOcclusion();
	}

	// registers the block and its block item under the same name
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = registerBlock(name, factory, properties);
		BlossomItems.register(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Blossom.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
