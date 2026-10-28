package com.raihanadf.blossom.cooking;

import com.raihanadf.blossom.registry.BlossomBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CuttingBoardBlockEntity extends KitchenBlockEntity {
	public static final int CHOPS_NEEDED = 3;

	private int chops;

	public CuttingBoardBlockEntity(BlockPos pos, BlockState state) {
		super(BlossomBlocks.CUTTING_BOARD_ENTITY, pos, state, 1);
	}

	public ItemStack getItem() {
		return items.getFirst();
	}

	public int getChops() {
		return chops;
	}

	public void place(Player player, ItemStack held) {
		items.set(0, held.consumeAndReturn(1, player));
		chops = 0;
		level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1.2f);
		markUpdated();
	}

	public void takeBack(Player player) {
		give(player, items.getFirst());
		items.set(0, ItemStack.EMPTY);
		chops = 0;
		level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1.2f);
		markUpdated();
	}

	// a few knife taps, then the ingredient turns into its chopped form
	public void chop(ServerLevel serverLevel, Player player, InteractionHand hand) {
		ItemStack input = items.getFirst();
		chops++;
		serverLevel.playSound(null, worldPosition, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1f, 1.4f + chops * 0.15f);
		serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, input.getItem()),
				worldPosition.getX() + 0.5, worldPosition.getY() + 0.15, worldPosition.getZ() + 0.5, 6, 0.12, 0.05, 0.12, 0.05);

		if (chops >= CHOPS_NEEDED) {
			give(player, KitchenRecipes.BOARD.get(input.getItem()).copy());
			player.getItemInHand(hand).hurtAndBreak(1, player, hand);
			items.set(0, ItemStack.EMPTY);
			chops = 0;
			serverLevel.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.3f);
		}
		markUpdated();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		chops = input.getIntOr("Chops", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Chops", chops);
	}
}
