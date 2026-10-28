package com.raihanadf.blossom.cooking;

import com.raihanadf.blossom.registry.BlossomBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// drop ingredients in, keep it over a fire, serve the dish with bowls
public class CookingPotBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);

	public CookingPotBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean serve = !pot.getResult().isEmpty() && held.is(Items.BOWL);
		boolean add = pot.getResult().isEmpty() && KitchenRecipes.isPotIngredient(held) && pot.ingredientCount() < CookingPotBlockEntity.SLOTS;
		if (!serve && !add) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			if (serve) {
				pot.serve(player, held);
			} else {
				pot.addIngredient(player, held);
			}
		}
		return InteractionResult.SUCCESS;
	}

	// empty hand: sneak to take an ingredient back, otherwise tell the player what the pot needs
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!pot.getResult().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.blossom.pot.serve", pot.getResult().getCount(), pot.getResult().getHoverName()));
		} else if (player.isSecondaryUseActive() && pot.hasIngredients()) {
			pot.takeLastIngredient(player);
		} else if (!pot.hasIngredients()) {
			player.sendOverlayMessage(Component.translatable("message.blossom.pot.empty"));
		} else if (KitchenRecipes.findPotRecipe(pot.getItems()) == null) {
			player.sendOverlayMessage(Component.translatable("message.blossom.pot.no_recipe"));
		} else if (!CookingPotBlockEntity.isHeated(level, pos)) {
			player.sendOverlayMessage(Component.translatable("message.blossom.pot.no_heat"));
		} else {
			player.sendOverlayMessage(Component.translatable("message.blossom.pot.cooking"));
		}
		return InteractionResult.SUCCESS;
	}

	// bubbles while cooking with steam that thickens as it gets close, a little steam when the dish is ready
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
			return;
		}
		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
		if (!pot.getResult().isEmpty()) {
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.6, z, 0, 0.03, 0);
			}
		} else if (CookingPotBlockEntity.isHeated(level, pos) && KitchenRecipes.findPotRecipe(pot.getItems()) != null) {
			level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.55, z, 0, 0.02, 0);
			for (int i = 0; i <= pot.cookedFraction() * 3; i++) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.6, z, 0, 0.03 + i * 0.01, 0);
			}
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CookingPotBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, BlossomBlocks.COOKING_POT_ENTITY, CookingPotBlockEntity::serverTick);
	}
}
