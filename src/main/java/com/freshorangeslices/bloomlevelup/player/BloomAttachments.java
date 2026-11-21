package com.freshorangeslices.bloomlevelup.player;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
				event.addListener(provider::invalidate);
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

	/** Callers already logged as asking for a profile that isn't there (playtest log, 2026-11-21: dozens a death). */
	private static final Set<String> MISSING_CALLERS = ConcurrentHashMap.newKeySet();
	private static final StackWalker WALKER = StackWalker.getInstance();

	/** The live, mutable profile for a player. Server-side truth only. */
	public static BloomProfile profile(Player player) {
		return player.getCapability(PROFILE).map(Holder::profile).orElseGet(() -> {
			// A dead player's body is removed a second after death, which drops its capabilities; until they press
			// Respawn, the tick code that walks the player list still asks about it (dozens of warnings a death in the
			// playtest log, 2026-11-21). The profile itself lives on: onClone copies it to the new body. So a removed
			// player gets a blank stand-in quietly; anything else is unexpected and logged once per caller, with its calls.
			if (player.isRemoved()) {
				return new BloomProfile();
			}

			List<String> calls = WALKER.walk(frames -> frames.skip(1)
					.map(frame -> frame.getClassName() + "." + frame.getMethodName())
					.filter(call -> !call.startsWith(BloomAttachments.class.getName()) && !call.startsWith("net.minecraftforge.common.util.LazyOptional")
							&& !call.startsWith("java.") && !call.startsWith("jdk.") && !call.startsWith("net.minecraftforge.eventbus."))
					.limit(10).toList());
			String caller = calls.isEmpty() ? "?" : calls.get(0);

			if (MISSING_CALLERS.size() < 50 && MISSING_CALLERS.add(caller)) {
				BloomLevelUp.LOGGER.warn("Bloom profile asked for {} without one attached (alive: {}, removed: {}; once per caller); calls: {}",
						player.getName().getString(), player.isAlive(), player.isRemoved(), String.join(" < ", calls));
			}

			return new BloomProfile();
		});
	}

	private static final class Provider implements ICapabilitySerializable<CompoundTag> {
		private final Holder holder = new Holder();
		private LazyOptional<Holder> optional = LazyOptional.of(() -> holder);

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			// A dead player's capabilities are invalidated (which ends this LazyOptional for good); reviveCaps() lets
			// Forge ask again during the Clone event, and a fresh one is handed out then, or the copy finds nothing
			// and the respawned player starts from an empty profile. Forge itself answers "empty" while invalid.
			if (!optional.isPresent()) {
				optional = LazyOptional.of(() -> holder);
			}

			return PROFILE.orEmpty(capability, optional);
		}

		/** Called when Forge invalidates the player's capabilities (removed, died): ends the one handed out now. */
		void invalidate() {
			optional.invalidate();
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
