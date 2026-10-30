package com.raihanadf.blossom.kampung;

import com.raihanadf.blossom.registry.BlossomItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** the pentungan's melee rule: vanilla damage first, then its damage straight off health. */
public class BatonItem extends Item {
	public static final int DAMAGE = 17;
	public static final int USES = 255;

	public BatonItem(Properties properties) {
		super(properties.durability(USES));
	}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || stack.getItem() != BlossomItems.BATON || !(entity instanceof LivingEntity living)
					|| !(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.PASS;
			}
			var source = player.damageSources().playerAttack(player);
			if (!living.hurtServer(serverLevel, source, 1.0f)) {
				return InteractionResult.SUCCESS;
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
					SoundSource.PLAYERS, 1.0f, 1.0f);
			living.setHealth(living.getHealth() - DAMAGE);
			if (living.isDeadOrDying()) {
				living.die(source);
			}
			stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			return InteractionResult.SUCCESS;
		});
	}
}
