package com.freshorangeslices.legionwarex.chart;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A pirate chart, not yet read: use it and it works out where it leads (the nearest boss lair no one on the world has
 * beaten, within a few thousand blocks; {@link LairSearch}) and becomes that treasure map ({@link Charts#read}). Reading
 * takes a moment, a tick per lair structure, so the server never stalls on it. Nothing in reach: the chart stays blank
 * and comes back, to try somewhere else.
 */
public final class PirateChartItem extends Item {
	public PirateChartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer reader)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		if (ChartReading.isReading(reader)) {
			reader.displayClientMessage(Component.translatable("message.legionwarex.chart_busy"), true);
			return InteractionResultHolder.fail(stack);
		}

		player.getCooldowns().addCooldown(this, 40);
		ChartReading.start(server, reader, player.getAbilities().instabuild ? stack.copyWithCount(1) : stack.split(1));
		reader.displayClientMessage(Component.translatable("message.legionwarex.chart_reading"), true);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.legionwarex.pirate_chart.blank").withStyle(ChatFormatting.GRAY));
	}
}
