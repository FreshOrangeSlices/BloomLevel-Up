package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gear that monsters wear and the pack keeps rare: items in {@code #legionwarex:rare_mob_gear}, Epic Knights' rust
 * series (every rusted armor piece, the Rusted Bastard Sword and the Rusted Heavy Mace). FreshOrangeSlices,
 * 2026-11-22: "they drop too often", with the server on Hard. Epic Knights dresses monsters in them, more often on Hard,
 * and vanilla drops a worn piece 8.5% of the time, plus 1% per level of Looting.
 *
 * <p>Here a piece drops 1 time in 200 ({@code rareMobGearDropChance}), flat: Looting doesn't raise it. As in vanilla,
 * only when a player had a hand in the kill and mob loot is on, and a piece comes off worn (random damage). A piece the
 * mob picked up (a guaranteed drop) still always drops. The mob keeps wearing its gear: only the drop changes.
 */
public final class RareMobGear {
	public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, new ResourceLocation(LegionWareX.MOD_ID, "rare_mob_gear"));
	/** For the game tests: an item counted as rare gear, and a chance in place of the config's. */
	public static volatile Item testExtra;
	public static volatile Double testChance;
	private static final Map<LivingEntity, List<ItemStack>> HELD = new WeakHashMap<>();

	private RareMobGear() {
	}

	public static boolean rare(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(TAG) || (testExtra != null && stack.is(testExtra)));
	}

	public static double chance() {
		Double test = testChance;
		return test != null ? test : AddonConfig.rareMobGearDropChance();
	}

	/** As the mob dies (a death not cancelled): its rare pieces stop dropping vanilla's way and wait for the drops. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
			return;
		}

		List<ItemStack> held = null;
		CompoundTag saved = null;

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = mob.getItemBySlot(slot);

			if (!rare(stack)) {
				continue;
			}

			if (saved == null) {
				saved = mob.saveWithoutId(new CompoundTag());
			}

			if (dropChance(saved, slot) > 1.0F) {
				continue;
			}

			mob.setDropChance(slot, 0.0F);

			if (held == null) {
				held = new ArrayList<>();
			}

			held.add(stack.copy());
		}

		if (held != null) {
			HELD.put(mob, held);
		}
	}

	/** Before the drop rules that may cancel them (MobKillDrops), so those still apply to what's added here. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity dead = event.getEntity();
		List<ItemStack> held = HELD.remove(dead);

		if (held == null || !event.isRecentlyHit() || !dead.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
			return;
		}

		double chance = chance();
		RandomSource random = dead.getRandom();

		for (ItemStack stack : held) {
			if (EnchantmentHelper.hasVanishingCurse(stack) || random.nextDouble() >= chance) {
				continue;
			}

			if (stack.isDamageableItem()) {
				stack.setDamageValue(stack.getMaxDamage() - random.nextInt(1 + random.nextInt(Math.max(stack.getMaxDamage() - 3, 1))));
			}

			ItemEntity drop = new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), stack);
			drop.setDefaultPickUpDelay();
			event.getDrops().add(drop);
		}
	}

	/** What's in the tag and the chance, for the boot test (server-rare-gear). */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> items = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(TAG).forEach(holder -> items.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
		LegionWareX.LOGGER.info("LegionWareX rare gear: {} items drop 1 in {} when worn: {}", items.size(),
				Math.round(1.0 / Math.max(chance(), 1.0E-9)), String.join(", ", items));
	}

	/** The slot's drop chance as the mob saves it (vanilla keeps it protected): above 1 is a guaranteed drop. */
	private static float dropChance(CompoundTag saved, EquipmentSlot slot) {
		ListTag list = saved.getList(slot.getType() == EquipmentSlot.Type.ARMOR ? "ArmorDropChances" : "HandDropChances", Tag.TAG_FLOAT);
		return slot.getIndex() < list.size() ? list.getFloat(slot.getIndex()) : 0.085F;
	}
}
