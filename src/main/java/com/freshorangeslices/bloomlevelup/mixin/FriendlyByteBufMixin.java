package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Stack Overflow: item stacks over 127 cross the network whole. Vanilla sends a stack's count as one signed byte;
 * a count that doesn't fit goes as the marker -128 followed by the count as a var-int. (Both sides run Bloom.)
 */
@Mixin(FriendlyByteBuf.class)
abstract class FriendlyByteBufMixin {
	@Unique
	private static final int BIG_COUNT = -128;

	@WrapOperation(method = "writeItemStack", remap = false,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;", remap = false))
	private ByteBuf bloomlevelup$writeBigCount(FriendlyByteBuf buf, int count, Operation<ByteBuf> original) {
		if (count > Byte.MAX_VALUE) {
			original.call(buf, BIG_COUNT);
			return buf.writeVarInt(count);
		}

		return original.call(buf, count);
	}

	@WrapOperation(method = "readItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readByte()B", remap = false))
	private byte bloomlevelup$readBigCountMarker(FriendlyByteBuf buf, Operation<Byte> original) {
		byte count = original.call(buf);
		// The marker is read back as a byte here; the var-int count follows (see bloomlevelup$bigCount).
		bloomlevelup$pending = count == BIG_COUNT ? buf.readVarInt() : -1;
		return count == BIG_COUNT ? 1 : count;
	}

	@Unique
	private int bloomlevelup$pending = -1;

	@WrapOperation(method = "readItem",
			at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/item/ItemStack;"))
	private net.minecraft.world.item.ItemStack bloomlevelup$bigCount(net.minecraft.world.level.ItemLike item, int count,
			Operation<net.minecraft.world.item.ItemStack> original) {
		int pending = bloomlevelup$pending;
		bloomlevelup$pending = -1;
		return original.call(item, pending > 0 ? pending : count);
	}
}
