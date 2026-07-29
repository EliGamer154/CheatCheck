package com.tradeshop.moderation;

import com.tradeshop.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns and manages "fake player" combat dummies for testing kill-aura and other checks. They're armored,
 * named zombies (so kill-aura locks onto them) that don't burn in daylight. Levels: 1 AFK, 2 wanders without
 * attacking, 3 attacks with fists, 4 iron gear + attacks, 5 diamond gear + attacks. Removed all at once with
 * {@code /unspawnfakeplayers}.
 */
public final class FakePlayers {
	private static final FakePlayers INSTANCE = new FakePlayers();
	private static final String TAG = "tradeshop_fake_player";

	private final List<Mob> bots = new ArrayList<>();

	private FakePlayers() {
	}

	public static FakePlayers get() {
		return INSTANCE;
	}

	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			bots.removeIf(bot -> !bot.isAlive());
			for (Mob bot : bots) {
				bot.setRemainingFireTicks(0); // keep them from visibly burning in daylight
			}
		});
	}

	/** Spawns one fake player of the given level in front of the admin. Returns false if spawning failed. */
	public boolean spawn(ServerPlayer admin, int level) {
		ServerLevel world = (ServerLevel) admin.level();
		Zombie bot = EntityType.ZOMBIE.create(world, EntitySpawnReason.COMMAND);
		if (bot == null) {
			return false;
		}

		bot.setBaby(false);
		bot.setPersistenceRequired();
		bot.addTag(TAG);
		bot.setCustomName(Component.literal("FakePlayer" + level));
		bot.setCustomNameVisible(true);
		bot.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
				MobEffectInstance.INFINITE_DURATION, 0, false, false, false));

		AttributeInstance maxHealth = bot.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(60.0);
		}
		bot.setHealth(bot.getMaxHealth());

		Vec3 look = admin.getLookAngle();
		bot.snapTo(admin.getX() + look.x * 2.0, admin.getY(), admin.getZ() + look.z * 2.0, admin.getYRot() + 180.0f, 0);

		switch (level) {
			case 1 -> bot.setNoAi(true); // AFK: stands still, never attacks
			case 2 -> ((MobAccessor) bot).tradeshop$targetSelector().removeAllGoals(goal -> true); // wanders, no attacking
			case 4 -> equip(bot, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD);
			case 5 -> equip(bot, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD);
			default -> { /* level 3: default zombie AI attacks with fists */ }
		}

		world.addFreshEntity(bot);
		bots.add(bot);
		return true;
	}

	/** Removes every fake player currently tracked. Returns how many were removed. */
	public int unspawnAll(MinecraftServer server) {
		int removed = 0;
		for (Mob bot : bots) {
			if (bot.isAlive()) {
				bot.discard();
				removed++;
			}
		}
		bots.clear();
		return removed;
	}

	private static void equip(Mob bot, Item head, Item chest, Item legs, Item feet, Item weapon) {
		setGear(bot, EquipmentSlot.HEAD, head);
		setGear(bot, EquipmentSlot.CHEST, chest);
		setGear(bot, EquipmentSlot.LEGS, legs);
		setGear(bot, EquipmentSlot.FEET, feet);
		setGear(bot, EquipmentSlot.MAINHAND, weapon);
	}

	private static void setGear(Mob bot, EquipmentSlot slot, Item item) {
		bot.setItemSlot(slot, new ItemStack(item));
		bot.setDropChance(slot, 0.0f); // don't scatter gear when killed
	}
}
