package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Weapons a boss drops on top of its own loot (FreshOrangeSlices, 2026-11-23: the Wretched Claymore "as a boss drop of
 * that mod, 1, 100% chance"; config {@code bossWeaponDrops}, "entity=item" or "entity=item@chance"). One each, when a
 * player had a hand in the kill (vanilla's test for its player-only drops: a hit of theirs or their tamed pet's within the
 * last 5 seconds) and mob loot is on.
 */
public final class BossDrops {
	/** For the game tests: this table in place of the config's (null for the config's). */
	public static Map<ResourceLocation, Drop> testTable;
	private static List<? extends String> parsedFrom;
	private static Map<ResourceLocation, Drop> parsed = Map.of();

	private BossDrops() {
	}

	public record Drop(ResourceLocation item, double chance) {
	}

	public static Map<ResourceLocation, Drop> table() {
		if (testTable != null) {
			return testTable;
		}

		List<? extends String> list = AddonConfig.bossDrops();

		if (list != parsedFrom) {
			Map<ResourceLocation, Drop> out = new HashMap<>();

			for (String entry : list) {
				String[] parts = entry.split("=", 2);
				String[] drop = parts.length < 2 ? new String[0] : parts[1].split("@", 2);
				ResourceLocation entity = ResourceLocation.tryParse(parts[0].trim());
				ResourceLocation item = drop.length == 0 ? null : ResourceLocation.tryParse(drop[0].trim());

				try {
					double chance = drop.length > 1 ? Double.parseDouble(drop[1].trim()) : 1.0;

					if (entity != null && item != null) {
						out.put(entity, new Drop(item, Math.max(0.0, Math.min(1.0, chance))));
						continue;
					}
				} catch (RuntimeException e) {
					// reported below
				}

				LegionWareX.LOGGER.warn("LegionWareX boss drops: entry \"{}\" isn't \"entity=item\" or \"entity=item@chance\"", entry);
			}

			parsed = Map.copyOf(out);
			parsedFrom = list;
		}

		return parsed;
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity boss = event.getEntity();

		if (boss.level().isClientSide || !event.isRecentlyHit() || !boss.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
			return;
		}

		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(boss.getType());
		Drop drop = id == null ? null : table().get(id);

		if (drop == null || !ForgeRegistries.ITEMS.containsKey(drop.item()) || boss.getRandom().nextDouble() >= drop.chance()) {
			return;
		}

		Item item = ForgeRegistries.ITEMS.getValue(drop.item());
		ItemEntity entity = new ItemEntity(boss.level(), boss.getX(), boss.getY() + 0.5, boss.getZ(), new ItemStack(item));
		entity.setDefaultPickUpDelay();
		event.getDrops().add(entity);
	}

	/** What the server will drop, as "LegionWareX boss drops:". */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> rows = new ArrayList<>();

		for (Map.Entry<ResourceLocation, Drop> entry : table().entrySet()) {
			boolean present = ForgeRegistries.ENTITY_TYPES.containsKey(entry.getKey()) && ForgeRegistries.ITEMS.containsKey(entry.getValue().item());
			rows.add(entry.getKey() + " drops " + entry.getValue().item() + " (" + Math.round(entry.getValue().chance() * 100.0) + "%)"
					+ (present ? "" : ", not in the game"));
		}

		LegionWareX.LOGGER.info("LegionWareX boss drops: {}", rows.isEmpty() ? "none" : String.join(", ", rows));
	}
}
