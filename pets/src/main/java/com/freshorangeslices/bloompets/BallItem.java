package com.freshorangeslices.bloompets;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Ball (the Heart of the Sea's look, FreshOrangeSlices 2026-11-05): right-click to throw it for your party pet ({@link Fetch}). */
public final class BallItem extends Item {
	public BallItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResultHolder.success(held);
		}

		if (player instanceof ServerPlayer serverPlayer && Fetch.throwBall(serverPlayer, held)) {
			// Thrown, not lost: it comes back when the game's over.
			held.shrink(1);
			player.getCooldowns().addCooldown(this, 10);
			return InteractionResultHolder.consume(held);
		}

		return InteractionResultHolder.fail(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.bloompets.ball.tip").withStyle(ChatFormatting.GRAY));
	}
}
