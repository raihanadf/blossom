package com.raihanadf.blossom.cooking;

import com.raihanadf.blossom.registry.BlossomBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CookingPotBlockEntity extends KitchenBlockEntity {
	public static final int SLOTS = 6;
	public static final int COOK_TIME = 200;

	private ItemStack result = ItemStack.EMPTY;
	private int progress;

	public CookingPotBlockEntity(BlockPos pos, BlockState state) {
		super(BlossomBlocks.COOKING_POT_ENTITY, pos, state, SLOTS);
	}

	public ItemStack getResult() {
		return result;
	}

	public float cookedFraction() {
		return progress / (float) COOK_TIME;
	}

	public boolean hasIngredients() {
		return ingredientCount() > 0;
	}

	// ingredients are always packed at the front, so this is also the count
	public int ingredientCount() {
		int count = 0;
		while (count < SLOTS && !items.get(count).isEmpty()) {
			count++;
		}
		return count;
	}

	public static boolean isHeated(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK)
				|| CampfireBlock.isLitCampfire(below);
	}

	public void addIngredient(Player player, ItemStack held) {
		items.set(ingredientCount(), held.consumeAndReturn(1, player));
		progress = 0;
		level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.6f);
		markUpdated();
	}

	public void takeLastIngredient(Player player) {
		int last = ingredientCount() - 1;
		give(player, items.get(last));
		items.set(last, ItemStack.EMPTY);
		progress = 0;
		level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f);
		markUpdated();
	}

	// one bowl in, one serving out
	public void serve(Player player, ItemStack bowl) {
		bowl.shrink(1);
		give(player, result.split(1));
		level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1.2f);
		markUpdated();
	}

	// village cooks drop a whole recipe in at once, from their own pantry
	public void fill(List<Item> ingredients) {
		for (int i = 0; i < ingredients.size(); i++) {
			items.set(i, new ItemStack(ingredients.get(i)));
		}
		progress = 0;
		level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1.4f);
		markUpdated();
	}

	// and scoop the whole finished pot out for themselves
	public ItemStack takeResult() {
		ItemStack taken = result;
		result = ItemStack.EMPTY;
		level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1.2f);
		markUpdated();
		return taken;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
		if (!pot.result.isEmpty()) {
			return;
		}
		KitchenRecipes.PotRecipe recipe = KitchenRecipes.findPotRecipe(pot.items);
		if (recipe == null || !isHeated(level, pos)) {
			pot.progress = 0;
			return;
		}

		pot.progress++;
		if (pot.progress % 30 == 0) {
			level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.6f, 0.8f + level.getRandom().nextFloat() * 0.4f);
		}
		// let clients know how far along it is now and then, the steam grows with it
		if (pot.progress % 20 == 0) {
			pot.markUpdated();
		}
		if (pot.progress >= COOK_TIME) {
			pot.items.clear();
			pot.result = new ItemStack(recipe.dish(), recipe.servings());
			pot.progress = 0;
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.5f);
			((ServerLevel) level).sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0);
			pot.markUpdated();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		Block.popResource(level, pos, result);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		result = input.read("Result", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		progress = input.getIntOr("Progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("Result", ItemStack.OPTIONAL_CODEC, result);
		output.putInt("Progress", progress);
	}
}
