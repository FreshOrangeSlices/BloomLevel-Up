package com.freshorangeslices.legionwarex.companion;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A fallen companion's keepsake: used on the ground once it has rested, it brings the companion back there. */
public class CompanionKeepsakeItem extends Item {
	public CompanionKeepsakeItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		return stack.hasTag() ? Component.translatable("item.legionwarex.companion_keepsake.of", Keepsakes.name(stack)) : super.getName(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		Player player = context.getPlayer();
		BlockPos clicked = context.getClickedPos();
		// On the block's face, like a spawn egg (into the block itself when it's grass or snow to walk through).
		BlockPos spot = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
		Vec3 at = Vec3.atBottomCenterOf(spot);
		float yaw = player == null ? 0.0F : player.getYRot() + 180.0F;
		Component name = Keepsakes.name(stack);

		switch (Keepsakes.revive((ServerLevel) level, at, yaw, stack)) {
			case REVIVED -> {
				((ServerLevel) level).sendParticles(ParticleTypes.HEART, at.x, at.y + 1.0, at.z, 8, 0.5, 0.5, 0.5, 0.1);
				level.playSound(null, spot, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
				message(player, Component.translatable("message.legionwarex.revived", name).withStyle(ChatFormatting.GREEN));
				stack.shrink(1);
				return InteractionResult.CONSUME;
			}
			case RESTING -> {
				message(player, Component.translatable("message.legionwarex.resting", name, Keepsakes.time(Keepsakes.ticksLeft(level, stack))));
				return InteractionResult.FAIL;
			}
			case EMPTY -> {
				message(player, Component.translatable("message.legionwarex.empty"));
				return InteractionResult.FAIL;
			}
			default -> {
				message(player, Component.translatable("message.legionwarex.revive_failed", name).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
		}
	}

	private static void message(Player player, Component text) {
		if (player != null) {
			player.displayClientMessage(text, true);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
		if (!stack.hasTag()) {
			lines.add(Component.translatable("item.legionwarex.companion_keepsake.empty").withStyle(ChatFormatting.GRAY));
			return;
		}

		int companionLevel = Keepsakes.companionLevel(stack);

		if (companionLevel > 0) {
			lines.add(Component.translatable("item.legionwarex.companion_keepsake.level", companionLevel).withStyle(ChatFormatting.GRAY));
		}

		long left = level == null ? 0 : Keepsakes.ticksLeft(level, stack);
		lines.add(left > 0
				? Component.translatable("item.legionwarex.companion_keepsake.resting", Keepsakes.time(left)).withStyle(ChatFormatting.GOLD)
				: Component.translatable("item.legionwarex.companion_keepsake.ready").withStyle(ChatFormatting.GREEN));
	}
}
