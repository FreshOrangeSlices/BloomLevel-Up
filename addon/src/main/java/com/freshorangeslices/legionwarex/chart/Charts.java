package com.freshorangeslices.legionwarex.chart;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A read pirate chart is a treasure map, as vanilla's explorer maps are: centred on the lair, its terrain sketched in,
 * a red X on the lair's start, and your own marker at the edge until you're on it. It carries where it leads
 * ({@link #TAG}), and once you're within a few dozen blocks of the X it has done its job and crumbles.
 */
public final class Charts {
	/** On a read chart (a filled map): {x, z, dimension, structure, boss}. */
	public static final String TAG = "legionwarex_chart";
	/** 1:4, 512 blocks across: the scale of vanilla's mansion and monument maps. */
	private static final byte SCALE = 2;

	private Charts() {
	}

	/** The read chart for a lair found from a search. */
	public static ItemStack read(ServerLevel level, LairSearch.Found found) {
		BlockPos pos = found.pos();
		ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), SCALE, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecoration.Type.RED_X);
		Component boss = LairSearch.bossName(found.boss());
		map.setHoverName(Component.translatable("item.legionwarex.pirate_chart").withStyle(style -> style.withItalic(false)));
		CompoundTag display = map.getOrCreateTagElement("display");
		ListTag lore = new ListTag();
		lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(found.rematch()
				? "item.legionwarex.pirate_chart.leads_to_beaten" : "item.legionwarex.pirate_chart.leads_to", boss)
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		display.put("Lore", lore);
		CompoundTag chart = new CompoundTag();
		chart.putInt("x", pos.getX());
		chart.putInt("z", pos.getZ());
		chart.putString("dimension", level.dimension().location().toString());
		chart.putString("structure", found.structure().toString());
		chart.putString("boss", found.boss().toString());
		map.getOrCreateTag().put(TAG, chart);
		return map;
	}

	public static boolean isReadChart(ItemStack stack) {
		return !stack.isEmpty() && stack.getTag() != null && stack.getTag().contains(TAG, CompoundTag.TAG_COMPOUND);
	}

	/**
	 * Once a second: a read chart a player carries crumbles when they're within reach of its X (same dimension),
	 * telling them whose lair it is.
	 */
	public static void checkArrival(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int arrive = AddonConfig.chartArriveBlocks();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!isReadChart(stack)) {
				continue;
			}

			CompoundTag chart = stack.getTag().getCompound(TAG);

			if (!player.level().dimension().location().toString().equals(chart.getString("dimension"))) {
				continue;
			}

			double dx = player.getX() - chart.getInt("x");
			double dz = player.getZ() - chart.getInt("z");

			if (dx * dx + dz * dz > (double) arrive * arrive) {
				continue;
			}

			ResourceLocation boss = ResourceLocation.tryParse(chart.getString("boss"));
			inventory.setItem(slot, ItemStack.EMPTY);
			player.sendSystemMessage(Component.translatable("message.legionwarex.chart_arrived",
					boss == null ? Component.literal("?") : LairSearch.bossName(boss)).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
			LegionWareX.LOGGER.info("LegionWareX chart: {} reached {} ({})", player.getScoreboardName(), chart.getString("structure"), boss);
		}
	}
}
