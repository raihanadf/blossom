package com.raihanadf.blossom.pets;

import com.raihanadf.blossom.cooking.DishItem;
import com.raihanadf.blossom.critters.Critter;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomItems;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

// any friendly animal can be fed treats and home cooking, not just the farm ones: it gets happy, heals,
// grows up a bit faster, may fall in love, wild cats, wolves and parrots may even decide you're theirs,
// and it trots after you for half a minute
public final class PetFeeding {
	private static final int FOLLOW_TICKS = 600;

	private record Follow(UUID player, int[] ticksLeft) {
	}

	// which animal follows whom, per world, only kept in memory
	private static final Map<UUID, Follow> FOLLOWING = new HashMap<>();

	private PetFeeding() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!isPetFood(held) || !isPet(entity)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel serverLevel) {
				feed(serverLevel, player, (PathfinderMob) entity, held);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_LEVEL_TICK.register(PetFeeding::tickFollowers);
	}

	public static boolean isPetFood(ItemStack stack) {
		Set<Item> snacks = Set.of(BlossomItems.PET_TREAT, BlossomItems.CARROT_SLICES, BlossomItems.APPLE_SLICES, BlossomItems.FISH_FILLET,
				BlossomItems.CHICKEN_PIECES, BlossomItems.BEEF_SLICES, BlossomBlocks.RICE);
		return snacks.contains(stack.getItem()) || stack.getItem() instanceof DishItem;
	}

	private static boolean isPet(Entity entity) {
		return entity instanceof PathfinderMob mob && !(entity instanceof Enemy) && !(entity instanceof Npc) && mob.isAlive()
				&& (entity instanceof AgeableMob || entity instanceof Animal);
	}

	private static void feed(ServerLevel level, Player player, PathfinderMob pet, ItemStack food) {
		pet.heal(4);
		if (pet instanceof AgeableMob young && young.isBaby()) {
			young.ageUp(60, true);
		} else if (pet instanceof Animal animal && animal.canFallInLove() && food.is(BlossomItems.PET_TREAT)) {
			animal.setInLove(player);
		}
		if (pet instanceof TamableAnimal wild && !wild.isTame() && level.getRandom().nextInt(3) == 0) {
			wild.tame(player);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY() + pet.getBbHeight(), pet.getZ(), 8, 0.4, 0.3, 0.4, 0);
		}
		if (pet instanceof Critter critter && critter.getCritterPose() != Critter.SLEEPING) {
			critter.triggerAnim("actions", "happy");
		}
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 4, 0.3, 0.2, 0.3, 0);
		// smaller animals munch higher
		float pitch = 1.6f - Math.min(pet.getBbWidth(), 1.2f) * 0.6f;
		level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.GENERIC_EAT.value(), pet.getSoundSource(), 0.8f, pitch);
		pet.getNavigation().stop();
		pet.getLookControl().setLookAt(player);

		if (!player.getAbilities().instabuild) {
			boolean dish = food.getItem() instanceof DishItem;
			food.shrink(1);
			if (dish && !player.getInventory().add(new ItemStack(Items.BOWL))) {
				Block.popResource(level, player.blockPosition(), new ItemStack(Items.BOWL));
			}
		}
		FOLLOWING.put(pet.getUUID(), new Follow(player.getUUID(), new int[] {FOLLOW_TICKS}));
	}

	private static void tickFollowers(ServerLevel level) {
		Iterator<Map.Entry<UUID, Follow>> entries = FOLLOWING.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Follow> entry = entries.next();
			Entity entity = level.getEntity(entry.getKey());
			if (entity == null) {
				// in another world or unloaded: the countdown still runs out so the entry doesn't linger
				if (--entry.getValue().ticksLeft()[0] <= 0) {
					entries.remove();
				}
				continue;
			}
			Player player = level.getPlayerByUUID(entry.getValue().player());
			int[] left = entry.getValue().ticksLeft();
			if (!(entity instanceof PathfinderMob pet) || !pet.isAlive() || player == null || --left[0] <= 0 || pet.isPassenger() || pet.isLeashed()) {
				entries.remove();
				continue;
			}
			if (left[0] % 10 != 0) {
				continue;
			}
			double distance = pet.distanceToSqr(player);
			if (distance > 9 && distance < 400) {
				pet.getNavigation().moveTo(player, 1.15);
			} else if (distance <= 9) {
				pet.getNavigation().stop();
				pet.getLookControl().setLookAt(player);
			}
			if (left[0] % 80 == 0) {
				level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 1, 0.2, 0.1, 0.2, 0);
			}
		}
	}
}
