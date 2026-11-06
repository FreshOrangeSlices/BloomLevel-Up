package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A Bloom pet's mood and hunger (docs/design/PETS.md: "Mood stays ... and pets get a second bar, hunger"), the plugin's
 * happiness with a food bar beside it. Both live on the pet ({@code bloompets.mood}, {@code bloompets.hunger}).
 *
 * <ul>
 *   <li><b>Mood</b>, 0 to 100 (shown as 10 hearts). It slowly drops while the pet is in the party (a point every 3
 *   minutes), three times as fast once it's tired (after 90 minutes in the party), twice as fast while it's hungry and
 *   four times while it's starving. At home (out of the party) it rests: tiredness goes in 5 minutes, and a fed pet
 *   cheers up 10 points a minute. Feeding (+15, once a minute) and petting (sneak + right-click with an empty hand,
 *   +5, every 30 seconds) cheer it up; reaching full that way makes it <b>love you</b> for 30 minutes (its mood drops
 *   at a quarter of the pace; fainting ends it). Below 30 it's <b>sad</b> and sulks: no skills, owner bonus, job or
 *   storage until it cheers up (nothing is lost).</li>
 *   <li><b>Hunger</b>, 0 to 20 like the player's food. It empties while the pet is in the party (a point every 4
 *   minutes: a full bar lasts 80 minutes). Its favourite food fills 6; a pet with that food in its storage eats it by
 *   itself when it's down to 14. Well fed (15 and up) it slowly heals; hungry (6 and under) it gets glum fast.</li>
 * </ul>
 * <b>Companions</b> (other mods', Phase 5a: FreshOrangeSlices, 2026-11-05) have the same bars and rates. They never join
 * the party: one is "out" while it's within {@value #WITH_YOU} blocks of its owner (it tires, gets hungry, and its mood
 * drops as a party pet's), and rests as a pet at home when it's anywhere else, so a whole army left at a base needs no
 * feeding. They eat cooked vanilla food ({@code #bloompets:companion_food}), sneak + right-click (a plain right-click is
 * their own mod's), and one that's out takes it from its own inventory when it's down to 14 ({@link CompanionInventory}:
 * a Migamigos companion's backpack, a Zoni's companion's slots). Their news goes above the hotbar, not to chat. A
 * sulking companion's tree (and, later, its Spirit) rests.
 */
public final class PetMood {
	public static final double MAX_MOOD = 100.0;
	public static final double START_MOOD = 80.0;
	public static final double SAD_BELOW = 30.0;
	public static final double MAX_HUNGER = 20.0;
	public static final double HUNGRY_AT = 6.0;
	public static final double FED_AT = 15.0;
	private static final double EATS_AT = 14.0;
	private static final double MEAL = 6.0;
	private static final double DROP_PER_SECOND = 1.0 / 180.0;
	private static final double POST_DROP_PER_SECOND = 1.0 / 120.0;
	private static final double HUNGER_PER_SECOND = 1.0 / 240.0;
	private static final double REST_PER_SECOND = 10.0 / 60.0;
	private static final long TIRED_AFTER = 90 * 60;
	private static final long REST_SECONDS = 5 * 60;
	private static final double FEED_CHEER = 15.0;
	private static final double PET_CHEER = 5.0;
	private static final long FEED_CHEER_EVERY = 1200;
	private static final long PET_EVERY = 600;
	private static final long LOVED_FOR = 30 * 60 * 20;
	/** How near its owner a companion has to be to count as out with them. */
	public static final int WITH_YOU = 32;

	private PetMood() {
	}

	/** Bloom pets and other mods' companions have mood and hunger. */
	private static boolean has(Entity pet) {
		Pets.Kind kind = Pets.kind(pet);
		return kind == Pets.Kind.BLOOM || kind == Pets.Kind.MODDED;
	}

	/** A companion out with its owner: they're online, in the same dimension and within {@value #WITH_YOU} blocks. */
	public static boolean withOwner(Entity pet) {
		ServerPlayer owner = owner(pet);
		return owner != null && owner.level() == pet.level() && owner.distanceToSqr(pet) <= WITH_YOU * WITH_YOU;
	}

	private static long now(Entity pet) {
		return pet.getServer() != null ? pet.getServer().overworld().getGameTime() : pet.level().getGameTime();
	}

	public static double mood(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data == null || !data.contains("mood") ? START_MOOD : data.getDouble("mood");
	}

	public static double hunger(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data == null || !data.contains("hunger") ? MAX_HUNGER : data.getDouble("hunger");
	}

	public static void setMood(Entity pet, double mood) {
		Pets.data(pet).putDouble("mood", Math.max(0.0, Math.min(MAX_MOOD, mood)));
	}

	public static void setHunger(Entity pet, double hunger) {
		Pets.data(pet).putDouble("hunger", Math.max(0.0, Math.min(MAX_HUNGER, hunger)));
	}

	/** A sad (sulking) pet: no skills, owner bonus, job or storage until it cheers up (a companion: no tree or Spirit). */
	public static boolean sad(Entity pet) {
		return has(pet) && mood(pet) < SAD_BELOW;
	}

	public static boolean loved(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data != null && data.getLong("lovedUntil") > now(pet);
	}

	public static boolean tired(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data != null && data.getLong("tired") >= TIRED_AFTER;
	}

	/** How fast its mood drops right now (1 = a point every 3 minutes). */
	public static double dropPace(Entity pet) {
		double hunger = hunger(pet);
		double pace = hunger <= 0 ? 4.0 : hunger <= HUNGRY_AT ? 2.0 : 1.0;

		if (tired(pet)) {
			pace *= 3.0;
		}

		return loved(pet) ? pace * 0.25 : pace;
	}

	/** Once a second for a loaded Bloom pet. */
	static void second(LivingEntity pet) {
		if (has(pet) && !Fainting.fainted(pet)) {
			update(pet, 1);
		}
	}

	/** {@code seconds} of the pet's day: working in the party (a companion: out with its owner), or resting at home. */
	public static void update(LivingEntity pet, int seconds) {
		update(pet, seconds, Pets.kind(pet) == Pets.Kind.MODDED ? withOwner(pet) : Pets.inParty(pet));
	}

	/** As above, with whether it's out (in the party, or a companion with its owner) given. Public for the game tests. */
	public static void update(LivingEntity pet, int seconds, boolean out) {
		CompoundTag data = Pets.data(pet);
		boolean wasSad = sad(pet);
		boolean wasTired = tired(pet);
		boolean wasHungry = hunger(pet) <= HUNGRY_AT;

		boolean posted = PetPosts.posted(pet);

		if (posted) {
			// At its post: a point every 2 minutes while its owner's online (no one to tire it out); hungry as in the party.
			if (owner(pet) != null) {
				setMood(pet, mood(pet) - POST_DROP_PER_SECOND * dropPace(pet) * seconds);
				setHunger(pet, hunger(pet) - HUNGER_PER_SECOND * seconds);

				if (hunger(pet) <= EATS_AT) {
					eatFromStorage(pet);
				}
			}
		} else if (out) {
			setMood(pet, mood(pet) - DROP_PER_SECOND * dropPace(pet) * seconds);
			setHunger(pet, hunger(pet) - HUNGER_PER_SECOND * seconds);
			data.putLong("tired", Math.min(TIRED_AFTER, data.getLong("tired") + seconds));

			if (hunger(pet) <= EATS_AT) {
				eatFromStorage(pet);
			}

			// Well fed: it slowly mends (a point every 10 seconds).
			if (hunger(pet) >= FED_AT && pet.getHealth() < pet.getMaxHealth() && (pet.tickCount / 20) % 10 == 0) {
				pet.heal(1.0F);
			}
		} else {
			data.putLong("tired", Math.max(0L, data.getLong("tired") - TIRED_AFTER * seconds / REST_SECONDS));

			if (hunger(pet) > HUNGRY_AT) {
				setMood(pet, mood(pet) + REST_PER_SECOND * seconds);
			}
		}

		show(pet);
		ServerPlayer owner = owner(pet);

		if (owner == null) {
			return;
		}

		if (wasSad != sad(pet)) {
			PetSkills.refresh(pet);
		}

		boolean companion = Pets.kind(pet) == Pets.Kind.MODDED;

		if (!wasSad && sad(pet)) {
			tell(owner, pet, Component.translatable(companion ? "bloompets.mood.sad.companion" : "bloompets.mood.sad", pet.getName())
					.withStyle(ChatFormatting.RED));

			if (!companion) {
				owner.playNotifySound(SoundEvents.WOLF_WHINE, SoundSource.NEUTRAL, 0.6F, 1.2F);
			}
		} else if (wasSad && !sad(pet)) {
			feltBetter(owner, pet);
		}

		if (!wasTired && tired(pet)) {
			tell(owner, pet, Component.translatable(companion ? "bloompets.mood.tired.companion" : "bloompets.mood.tired", pet.getName())
					.withStyle(ChatFormatting.GOLD));
		}

		if (!wasHungry && hunger(pet) <= HUNGRY_AT) {
			tell(owner, pet, Component.translatable(companion ? "bloompets.mood.hungry.companion" : "bloompets.mood.hungry", pet.getName())
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** A word to the owner about how a pet is doing, above the hotbar (not chat: FreshOrangeSlices, 2026-11-05). */
	private static void tell(ServerPlayer owner, Entity pet, Component text) {
		owner.displayClientMessage(text, true);
	}

	/** A rain cloud over a sad pet, a heart now and then over one that loves you. */
	private static void show(LivingEntity pet) {
		if (!(pet.level() instanceof ServerLevel level)) {
			return;
		}

		int second = pet.tickCount / 20;

		if (sad(pet) && second % 2 == 0) {
			level.sendParticles(ParticleTypes.FALLING_WATER, pet.getX(), pet.getY() + pet.getBbHeight() + 0.5, pet.getZ(), 3,
					pet.getBbWidth() / 3, 0.05, pet.getBbWidth() / 3, 0.0);
		} else if (loved(pet) && second % 4 == 0) {
			level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
		}
	}

	/**
	 * A party pet that's getting hungry takes its favourite food from its storage (a companion, cooked food from its own
	 * backpack, or else from Bloom Pets' food pouch).
	 */
	private static void eatFromStorage(LivingEntity pet) {
		boolean companion = Pets.kind(pet) == Pets.Kind.MODDED;

		if (companion && CompanionInventory.takeOne(pet, stack -> stack.is(Bonding.COMPANION_FOOD))) {
			setHunger(pet, hunger(pet) + MEAL);
			pet.playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.0F);
			return;
		}

		Species species = Pets.species(pet);
		java.util.function.Predicate<ItemStack> food = companion ? stack -> stack.is(Bonding.COMPANION_FOOD) : species == null ? null : species::isFood;
		PetRegistry.Record record = pet.getServer() == null ? null : PetRegistry.get(pet.getServer()).find(pet.getUUID());

		if (food == null || record == null || record.items.isEmpty()) {
			return;
		}

		SimpleContainer box = new SimpleContainer(Math.max(record.slots, record.items.size()));
		box.fromTag(record.items);

		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack stack = box.getItem(i);

			if (food.test(stack)) {
				stack.shrink(1);
				box.setChanged();
				record.items = box.createTag();
				PetRegistry.get(pet.getServer()).setDirty();
				setHunger(pet, hunger(pet) + MEAL);
				pet.playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.0F);
				return;
			}
		}
	}

	/** Its owner fed it its favourite food: fuller, and cheered up (at most once a minute). */
	static void fed(ServerPlayer owner, LivingEntity pet) {
		setHunger(pet, hunger(pet) + MEAL);
		CompoundTag data = Pets.data(pet);
		long now = now(pet);

		if (now - data.getLong("cheeredAt") >= FEED_CHEER_EVERY || !data.contains("cheeredAt")) {
			data.putLong("cheeredAt", now);
			cheer(owner, pet, FEED_CHEER);
		}
	}

	/** A game of fetch: +{@value Fetch#MOOD} (its own 15 seconds apart, kept by {@link Fetch}). */
	static void played(ServerPlayer owner, LivingEntity pet) {
		cheer(owner, pet, Fetch.MOOD);
	}

	/** Sneak + right-click with an empty hand: a pat on the head (cheers it up every 30 seconds). Public for the game tests. */
	public static void pat(ServerPlayer owner, LivingEntity pet) {
		ServerLevel level = owner.serverLevel();
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 2, 0.25, 0.15, 0.25, 0.0);
		level.playSound(null, pet.blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 0.6F, 1.3F);
		CompoundTag data = Pets.data(pet);
		long now = now(pet);

		if (data.contains("pettedAt") && now - data.getLong("pettedAt") < PET_EVERY) {
			owner.displayClientMessage(Component.translatable("bloompets.mood.pat", pet.getName(), hearts(pet)), true);
			return;
		}

		data.putLong("pettedAt", now);
		cheer(owner, pet, PET_CHEER);
		owner.displayClientMessage(Component.translatable("bloompets.mood.pat", pet.getName(), hearts(pet)), true);
	}

	private static Component hearts(Entity pet) {
		int full = Math.max(1, Math.min(10, (int) Math.ceil(mood(pet) / 10.0 - 1.0E-9)));
		ChatFormatting color = loved(pet) ? ChatFormatting.LIGHT_PURPLE : sad(pet) ? ChatFormatting.RED : full >= 8 ? ChatFormatting.GREEN
				: full >= 6 ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
		return Component.literal("❤".repeat(full)).withStyle(color).append(Component.literal("❤".repeat(10 - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static void cheer(ServerPlayer owner, LivingEntity pet, double amount) {
		boolean wasSad = sad(pet);
		setMood(pet, mood(pet) + amount);

		if (mood(pet) >= MAX_MOOD) {
			boolean already = loved(pet);
			Pets.data(pet).putLong("lovedUntil", now(pet) + LOVED_FOR);

			if (!already) {
				owner.serverLevel().sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 10,
						0.5, 0.4, 0.5, 0.0);
				owner.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.8F);
				tell(owner, pet, Component.translatable("bloompets.mood.loved", pet.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}

		if (wasSad && !sad(pet)) {
			feltBetter(owner, pet);
		}
	}

	private static void feltBetter(ServerPlayer owner, LivingEntity pet) {
		tell(owner, pet, Component.translatable("bloompets.mood.better", pet.getName()).withStyle(ChatFormatting.GREEN));
		PetSkills.refresh(pet);
	}

	/** Fainting ends "loves you". */
	static void fainted(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);

		if (data != null) {
			data.remove("lovedUntil");
		}
	}

	private static ServerPlayer owner(Entity pet) {
		UUID owner = Pets.owner(pet);
		return owner == null || pet.getServer() == null ? null : pet.getServer().getPlayerList().getPlayer(owner);
	}
}
