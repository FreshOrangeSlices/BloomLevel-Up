package com.freshorangeslices.bloomlevelup.player;

import com.mojang.serialization.DataResult;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * The player profile as a Forge capability (the Fabric build uses a data attachment): saved with the player under
 * {@code ForgeCaps."bloomlevelup:profile"}, and carried over to the new player on death and on the way back from
 * the End. Client sync is handled separately by our own packets (sent as deltas, not every tick).
 */
public final class BloomAttachments {
	public static final Capability<Holder> PROFILE = CapabilityManager.get(new CapabilityToken<>() {
	});

	private BloomAttachments() {
	}

	/**
	 * The capability's value: one player's live profile, plus item storage kept with it (Hoarder's Pocket, Rune
	 * Embedding). Both are replaced wholesale when a player is cloned, so they survive death and the End.
	 */
	public static final class Holder {
		private BloomProfile profile = new BloomProfile();
		private CompoundTag items = new CompoundTag();

		public BloomProfile profile() {
			return profile;
		}

		void set(BloomProfile profile) {
			this.profile = profile;
		}

		/** Named item lists ({@code pocket}, {@code runes}); live, so writes stick. */
		public CompoundTag items() {
			return items;
		}

		void setItems(CompoundTag items) {
			this.items = items;
		}
	}

	/** Item storage that travels with the profile (see {@link Holder#items}); an empty tag when the player has none. */
	public static CompoundTag items(Player player) {
		return player.getCapability(PROFILE).map(Holder::items).orElseGet(CompoundTag::new);
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(Holder.class));
		MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> event) -> {
			if (event.getObject() instanceof Player) {
				Provider provider = new Provider();
				event.addCapability(BloomLevelUp.id("profile"), provider);
				event.addListener(provider.optional::invalidate);
			}
		});
		MinecraftForge.EVENT_BUS.addListener(BloomAttachments::onClone);
	}

	private static void onClone(PlayerEvent.Clone event) {
		Player original = event.getOriginal();
		// The old player's capabilities are invalidated by now; bring them back long enough to copy.
		original.reviveCaps();

		try {
			original.getCapability(PROFILE).ifPresent(old -> event.getEntity().getCapability(PROFILE)
					.ifPresent(fresh -> {
						fresh.set(old.profile().copy());
						fresh.setItems(old.items().copy());
					}));
		} finally {
			original.invalidateCaps();
		}
	}

	/** The live, mutable profile for a player. Server-side truth only. */
	public static BloomProfile profile(Player player) {
		return player.getCapability(PROFILE).map(Holder::profile).orElseGet(() -> {
			// Only a player whose capabilities are already gone (removed, mid-clone) lands here.
			BloomLevelUp.LOGGER.warn("Bloom profile asked for {} without one attached", player.getName().getString());
			return new BloomProfile();
		});
	}

	private static final class Provider implements ICapabilitySerializable<CompoundTag> {
		private final Holder holder = new Holder();
		private final LazyOptional<Holder> optional = LazyOptional.of(() -> holder);

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			return PROFILE.orEmpty(capability, optional);
		}

		@Override
		public CompoundTag serializeNBT() {
			CompoundTag tag = new CompoundTag();
			DataResult<Tag> encoded = ProfileCodecs.PROFILE.encodeStart(NbtOps.INSTANCE, holder.profile());
			encoded.resultOrPartial(error -> BloomLevelUp.LOGGER.error("Couldn't save a Bloom profile: {}", error))
					.ifPresent(profile -> tag.put("profile", profile));

			if (!holder.items().isEmpty()) {
				tag.put("items", holder.items().copy());
			}

			return tag;
		}

		@Override
		public void deserializeNBT(CompoundTag tag) {
			if (tag.contains("profile")) {
				ProfileCodecs.PROFILE.parse(NbtOps.INSTANCE, tag.get("profile"))
						.resultOrPartial(error -> BloomLevelUp.LOGGER.error("Couldn't load a Bloom profile: {}", error))
						.ifPresent(holder::set);
			}

			holder.setItems(tag.getCompound("items").copy());
		}
	}
}
