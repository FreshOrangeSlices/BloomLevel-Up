package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Calling a pet to its owner (everyone has it, any pet): one that's loaded comes at once, from any dimension; one that
 * isn't has its chunk loaded for a few seconds, and comes as soon as it's there.
 */
public final class Calling {
	private static final TicketType<ChunkPos> CALL = TicketType.create("bloompets_call", Comparator.comparingLong(ChunkPos::toLong), 100);
	private static final int WAIT_TICKS = 100;
	private static final List<Pending> PENDING = new ArrayList<>();

	private record Pending(UUID player, UUID pet, String name, int[] ticksLeft) {
	}

	private Calling() {
	}

	/** Calls the pet to the player; returns what to tell them. */
	public static Component call(ServerPlayer player, PetRegistry.Record record) {
		Entity pet = Pets.loaded(player.server, record.pet);

		// Calling a pet takes it off its post.
		record.post = null;

		if (pet != null) {
			Fainting.cameOut(pet);
			PetPosts.leave(pet);
			follow(bring(pet, player));
			return Component.translatable("bloompets.call.came", record.name);
		}

		ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(record.dimension)));

		if (level == null) {
			return Component.translatable("bloompets.call.lost", record.name);
		}

		ChunkPos chunk = new ChunkPos(record.pos);
		level.getChunkSource().addRegionTicket(CALL, chunk, 2, chunk);
		PENDING.removeIf(p -> p.pet().equals(record.pet));
		PENDING.add(new Pending(player.getUUID(), record.pet, record.name, new int[] {WAIT_TICKS}));
		return Component.translatable("bloompets.call.calling", record.name);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
			return;
		}

		MinecraftServer server = event.getServer();
		Iterator<Pending> it = PENDING.iterator();

		while (it.hasNext()) {
			Pending pending = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(pending.player());

			if (player == null) {
				it.remove();
				continue;
			}

			Entity pet = Pets.loaded(server, pending.pet());

			if (pet != null) {
				Fainting.cameOut(pet);
				PetPosts.leave(pet);
				follow(bring(pet, player));
				player.displayClientMessage(Component.translatable("bloompets.call.came", pending.name()), true);
				it.remove();
			} else if (--pending.ticksLeft()[0] <= 0) {
				player.sendSystemMessage(Component.translatable("bloompets.call.lost", pending.name()));
				it.remove();
			}
		}
	}

	/** A called pet comes to follow (FreshOrangeSlices' playtest: a call is "come here", whatever it was told before). */
	private static void follow(Entity pet) {
		if (pet instanceof Mob mob) {
			PetModes.set(mob, PetModes.Mode.FOLLOW);
		}
	}

	/** Brings the pet to the player's side (from any dimension); returns the pet as it is there. */
	public static Entity bring(Entity pet, Player player) {
		if (pet.isPassenger()) {
			pet.stopRiding();
		}

		pet.ejectPassengers();

		if (pet instanceof TamableAnimal tamable) {
			tamable.setOrderedToSit(false);
			tamable.setInSittingPose(false);
		}

		Vec3 to = spotNear(player, pet);

		if (pet.level() == player.level()) {
			pet.teleportTo(to.x, to.y, to.z);
			pet.fallDistance = 0;

			if (pet instanceof Mob mob) {
				mob.getNavigation().stop();
			}

			return pet;
		}

		if (!(player.level() instanceof ServerLevel destination)) {
			return pet;
		}

		Entity moved = pet.changeDimension(destination, new ITeleporter() {
			@Override
			public PortalInfo getPortalInfo(Entity entity, ServerLevel level, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
				return new PortalInfo(to, Vec3.ZERO, entity.getYRot(), entity.getXRot());
			}

			@Override
			public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel level, float yaw, Function<Boolean, Entity> reposition) {
				return reposition.apply(false);
			}

			@Override
			public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player, ServerLevel from, ServerLevel to) {
				return false;
			}
		});
		return moved == null ? pet : moved;
	}

	/** A free spot beside the player for the pet to stand on, else the player's own. */
	private static Vec3 spotNear(Player player, Entity pet) {
		Level level = player.level();
		BlockPos feet = player.blockPosition();
		int[][] offsets = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};

		for (int[] offset : offsets) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos at = feet.offset(offset[0], dy, offset[1]);
				Vec3 spot = Vec3.atBottomCenterOf(at);

				if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
						&& level.noCollision(pet, pet.getDimensions(pet.getPose()).makeBoundingBox(spot))) {
					return spot;
				}
			}
		}

		return player.position();
	}
}
