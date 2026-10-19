package com.freshorangeslices.bloomlevelup.skill.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.gamerules.GameRules;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.item.BloomComponents;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.mixin.MobEffectInstanceAccessor;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Misc: XP from collected orbs, Wisdom / Endurance (Speed is an attribute in StatEffects), and the rule-bending
 * Majors. Hoarder's Pocket lives in {@link HoardersPocket}. Stack Overflow and Rune Embedding are not built yet
 * (their definitions are flagged disabled so they can't be bought).
 */
public final class MiscSkill {
	/** Water to Wine: ids of the effects it stretched, so switching it off only cuts those. */
	public static final AttachmentType<List<String>> WINE_EXTENDED = AttachmentRegistry.create(BloomLevelUp.id("wine_extended"),
			builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of));

	/** Ascension: players knocked out of the sky, until they land. */
	private static final Set<UUID> GROUNDED = new HashSet<>();
	/** XP Insurance: what came back, announced once the player has respawned. */
	private static final Map<UUID, Integer> INSURED = new HashMap<>();

	private MiscSkill() {
	}

	public static void init() {
		ServerMobEffectEvents.ALLOW_ADD.register((instance, entity, context) ->
				context.isFromCommand() || !(entity instanceof ServerPlayer player) || allowEffect(instance, player));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				afterDamage(player);
			}
		});
		ServerPlayerEvents.COPY_FROM.register(MiscSkill::copyFrom);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Integer returned = INSURED.remove(newPlayer.getUUID());

			if (returned != null && returned > 0) {
				newPlayer.sendSystemMessage(Component.translatable("bloom.misc.xp_insurance.returned", returned).withStyle(ChatFormatting.GREEN));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(MiscSkill::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			GROUNDED.remove(listener.player.getUUID());
			INSURED.remove(listener.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GROUNDED.clear();
			INSURED.clear();
		});
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	private static boolean active(ServerPlayer player, String majorId) {
		return BloomServices.progression().isMajorActive(BloomAttachments.profile(player), majorId);
	}

	private static int stat(ServerPlayer player, String statId) {
		return BloomServices.progression().statValue(BloomAttachments.profile(player), statId);
	}

	private static boolean harmful(MobEffectInstance instance) {
		return instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
	}

	// ------------------------------------------------------------------ XP orbs (ExperienceOrb mixin)

	/** Misc XP mirrors the orb's own value: before Wisdom, and whatever Mending takes. */
	public static void orbCollected(ServerPlayer player, int value) {
		if (value > 0) {
			SkillXp.award(player, SkillXp.MISC, value * config().miscXpPerOrbPoint);
		}
	}

	/** Wisdom: multiplies the vanilla XP an orb gives the player (what's left after Mending). */
	public static int wisdomXp(Player player, int amount) {
		if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0) {
			return amount;
		}

		int wisdom = stat(serverPlayer, MiscIds.WISDOM);
		return wisdom <= 0 ? amount : StatFormulas.scaledXp(amount, StatFormulas.wisdomMultiplier(wisdom), ThreadLocalRandom.current());
	}

	// ------------------------------------------------------------------ food (Player / FoodProperties / milk mixins)

	/** Endurance: less food exhaustion, so saturation and hunger last longer. */
	public static float exhaustion(Player player, float amount) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(amount > 0.0F)) {
			return amount;
		}

		int endurance = stat(serverPlayer, MiscIds.ENDURANCE);
		return endurance <= 0 ? amount : (float) (amount * StatFormulas.enduranceExhaustionFactor(endurance));
	}

	/** Second Serving: eat at any hunger level. Runs on both sides (the client decides whether eating starts). */
	public static boolean canAlwaysEat(Player player) {
		return SideBridge.majorActive(player, MiscIds.SECOND_SERVING);
	}

	/** Iron Stomach: unsafe food is at least as filling as cooked chicken. Better food keeps its own values. */
	public static FoodProperties ironStomachFood(LivingEntity eater, ItemStack stack, FoodProperties food) {
		if (!(eater instanceof ServerPlayer player) || !stack.is(BloomTags.FOOD_UNSAFE) || !active(player, MiscIds.IRON_STOMACH)) {
			return food;
		}

		BloomConfig config = config();
		int nutrition = Math.max(food.nutrition(), config.ironStomachNutrition);
		float saturation = Math.max(food.saturation(), (float) config.ironStomachSaturation);
		return nutrition == food.nutrition() && saturation == food.saturation() ? food : new FoodProperties(nutrition, saturation, food.canAlwaysEat());
	}

	/** Iron Stomach: harmful effects from unsafe food you chose to eat never land. */
	private static boolean allowEffect(MobEffectInstance instance, ServerPlayer player) {
		ItemStack eating = ConsumeContext.consumed(player);
		return eating.isEmpty() || !harmful(instance) || !eating.is(BloomTags.FOOD_UNSAFE) || !active(player, MiscIds.IRON_STOMACH);
	}

	/** Selective Cleansing on milk: returns true when it handled the cleanse (negative effects only). */
	public static boolean selectiveMilk(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !active(player, MiscIds.SELECTIVE_CLEANSING)) {
			return false;
		}

		cleanseNegatives(player);
		return true;
	}

	/** Removes every harmful effect and keeps the rest. Returns whether anything was removed. */
	public static boolean cleanseNegatives(LivingEntity entity) {
		List<Holder<MobEffect>> remove = new ArrayList<>();

		for (MobEffectInstance instance : entity.getActiveEffects()) {
			if (harmful(instance)) {
				remove.add(instance.getEffect());
			}
		}

		for (Holder<MobEffect> effect : remove) {
			entity.removeEffect(effect);
		}

		return !remove.isEmpty();
	}

	/** After a food or drink is consumed: Selective Cleansing vegetables, Become My Fuel imprints. */
	public static void afterConsume(ServerPlayer player, ItemStack consumed, boolean fromOffhand) {
		if (consumed.isEmpty()) {
			return;
		}

		if (consumed.is(BloomTags.FOOD_VEGETABLES) && !consumed.is(BloomTags.FOOD_CLEANSE_EXCLUDED) && active(player, MiscIds.SELECTIVE_CLEANSING)) {
			cleanseNegatives(player);
		}

		PotionContents potion = consumed.get(DataComponents.POTION_CONTENTS);

		if (potion != null && fromOffhand && active(player, MiscIds.BECOME_MY_FUEL)) {
			imprint(player, player.getMainHandItem(), potion);
		}
	}

	// ------------------------------------------------------------------ Become My Fuel

	/** Weapons that can carry fuel: anything in a weapon family (not an empty hand). */
	public static boolean fuelEligible(ItemStack weapon) {
		return !weapon.isEmpty() && !Weapons.families(weapon).isEmpty() && !Weapons.families(weapon).contains(Weapons.Family.UNARMED);
	}

	/** Stores the potion's first harmful, non-instant effect on the weapon (overwriting any earlier one). */
	public static boolean imprint(ServerPlayer player, ItemStack weapon, PotionContents potion) {
		if (!fuelEligible(weapon)) {
			return false;
		}

		for (MobEffectInstance instance : potion.getAllEffects()) {
			if (harmful(instance) && !instance.getEffect().value().isInstantaneous()) {
				weapon.set(BloomComponents.FUEL, new FuelImprint(instance.getEffect(), instance.getAmplifier()));
				player.sendSystemMessage(Component.translatable("bloom.misc.fuel.imprinted", effectName(instance.getEffect(), instance.getAmplifier()),
						weapon.getHoverName()).withStyle(ChatFormatting.DARK_PURPLE), true);
				return true;
			}
		}

		return false;
	}

	public static Component effectName(Holder<MobEffect> effect, int amplifier) {
		Component name = Component.translatable(effect.value().getDescriptionId());
		return amplifier <= 0 ? name : Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + amplifier));
	}

	/** Every hit with a fuelled weapon applies its effect for 10 seconds (it belongs to the weapon, not the wielder). */
	public static void applyFuel(ServerPlayer attacker, LivingEntity target, ItemStack weapon) {
		FuelImprint fuel = weapon.isEmpty() ? null : weapon.get(BloomComponents.FUEL);

		if (fuel != null && target.isAlive()) {
			target.addEffect(new MobEffectInstance(fuel.effect(), config().fuelEffectTicks, fuel.amplifier()), attacker);
		}
	}

	// ------------------------------------------------------------------ Water to Wine / Overdose (LivingEntity.addEffect mixin)

	public static MobEffectInstance modifyIncomingEffect(LivingEntity entity, MobEffectInstance incoming) {
		if (incoming == null || !(entity instanceof ServerPlayer player) || incoming.getEffect().value().isInstantaneous()) {
			return incoming;
		}

		BloomConfig config = config();
		MobEffectInstance result = incoming;

		// Overdose: re-applying an effect you already have pushes it one level higher (beacons/conduits excluded).
		if (!incoming.isAmbient() && active(player, MiscIds.OVERDOSE)) {
			MobEffectInstance current = player.getEffect(incoming.getEffect());

			if (current != null) {
				int amplifier = StatFormulas.overdoseAmplifier(current.getAmplifier(), incoming.getAmplifier(), config.overdoseMaxBoost);

				if (amplifier != incoming.getAmplifier()) {
					result = copy(result, result.getDuration(), amplifier);
				}
			}
		}

		// Water to Wine: every new timed effect lasts at least 10 minutes. Remember which ones it stretched.
		if (!result.isInfiniteDuration() && active(player, MiscIds.WATER_TO_WINE)) {
			String id = effectId(result.getEffect());

			if (result.getDuration() < config.waterToWineMinTicks) {
				result = copy(result, config.waterToWineMinTicks, result.getAmplifier());
				setWineMark(player, id, true);
			} else {
				setWineMark(player, id, false);
			}
		}

		return result;
	}

	private static MobEffectInstance copy(MobEffectInstance from, int duration, int amplifier) {
		return new MobEffectInstance(from.getEffect(), duration, amplifier, from.isAmbient(), from.isVisible(), from.showIcon());
	}

	private static String effectId(Holder<MobEffect> effect) {
		return effect.unwrapKey().map(key -> key.identifier().toString()).orElse("");
	}

	private static void setWineMark(ServerPlayer player, String id, boolean marked) {
		if (id.isEmpty()) {
			return;
		}

		List<String> current = player.getAttachedOrElse(WINE_EXTENDED, List.of());

		if (current.contains(id) == marked) {
			return;
		}

		Set<String> next = new LinkedHashSet<>(current);

		if (marked) {
			next.add(id);
		} else {
			next.remove(id);
		}

		player.setAttached(WINE_EXTENDED, List.copyOf(next));
	}

	/** Water to Wine switched off (or lost): the effects it stretched drop to 2 minutes left. Others are untouched. */
	public static void cutWineEffects(ServerPlayer player) {
		List<String> extended = player.getAttachedOrElse(WINE_EXTENDED, List.of());

		if (extended.isEmpty()) {
			return;
		}

		int cut = config().waterToWineCutTicks;

		for (String id : extended) {
			Identifier key = Identifier.tryParse(id);
			Optional<Holder.Reference<MobEffect>> effect = key == null ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.get(key);

			if (effect.isEmpty()) {
				continue;
			}

			MobEffectInstance instance = player.getEffect(effect.get());

			if (instance != null && !instance.isInfiniteDuration() && instance.getDuration() > cut) {
				((MobEffectInstanceAccessor) instance).bloomlevelup$setDuration(cut);

				if (player.connection != null) {
					player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), instance, false));
				}
			}
		}

		player.removeAttached(WINE_EXTENDED);
	}

	// ------------------------------------------------------------------ XP Insurance

	private static void copyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive || !active(oldPlayer, MiscIds.XP_INSURANCE) || newPlayer.experienceLevel > 0 || newPlayer.experienceProgress > 0.0F
				|| keepInventory(oldPlayer)) {
			return;
		}

		int returned = StatFormulas.insuredXp(oldPlayer.experienceLevel, oldPlayer.experienceProgress, config().xpInsuranceShare);

		if (returned > 0) {
			newPlayer.giveExperiencePoints(returned);
			INSURED.put(newPlayer.getUUID(), returned);
		}
	}

	private static boolean keepInventory(ServerPlayer player) {
		return player.level() instanceof ServerLevel level && Boolean.TRUE.equals(level.getGameRules().get(GameRules.KEEP_INVENTORY));
	}

	/** For game tests: what XP Insurance would hand back to this player right now. */
	public static int insuredXpFor(ServerPlayer player) {
		return StatFormulas.insuredXp(player.experienceLevel, player.experienceProgress, config().xpInsuranceShare);
	}

	// ------------------------------------------------------------------ Ascension

	/** Re-checked with the stat refresh (on every profile change and every half second). */
	public static void refresh(ServerPlayer player) {
		updateFlight(player);

		if (!active(player, MiscIds.WATER_TO_WINE)) {
			cutWineEffects(player);
		}
	}

	private static void tick(MinecraftServer server) {
		if (GROUNDED.isEmpty()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			checkLanding(player);
		}
	}

	/** A grounded Ascension player gets flight back once they touch the ground (or water). */
	public static void checkLanding(ServerPlayer player) {
		if (GROUNDED.contains(player.getUUID()) && (player.onGround() || player.isInWater())) {
			GROUNDED.remove(player.getUUID());
			updateFlight(player);
		}
	}

	/** Grants or takes away the double-jump flight Ascension gives (only in survival/adventure). */
	public static void updateFlight(ServerPlayer player) {
		BloomProfile profile = BloomAttachments.profile(player);
		boolean granted = profile.counter(MiscIds.ASCENSION_GRANTED) > 0;

		if (player.isCreative() || player.isSpectator()) {
			if (granted) {
				profile.setCounter(MiscIds.ASCENSION_GRANTED, 0); // the game mode owns flight now
			}

			return;
		}

		Abilities abilities = player.getAbilities();
		boolean want = active(player, MiscIds.ASCENSION) && !GROUNDED.contains(player.getUUID());

		if (want && !abilities.mayfly) {
			abilities.mayfly = true;
			profile.setCounter(MiscIds.ASCENSION_GRANTED, 1);
			player.onUpdateAbilities();
		} else if (!want && granted) {
			abilities.mayfly = false;
			abilities.flying = false;
			profile.setCounter(MiscIds.ASCENSION_GRANTED, 0);
			player.onUpdateAbilities();
		}
	}

	/** Taking damage while flying on Ascension drops you until you're back on the ground. */
	private static void afterDamage(ServerPlayer player) {
		if (player.getAbilities().flying && BloomAttachments.profile(player).counter(MiscIds.ASCENSION_GRANTED) > 0) {
			GROUNDED.add(player.getUUID());
			updateFlight(player);
		}
	}

	/** For game tests. */
	public static boolean grounded(ServerPlayer player) {
		return GROUNDED.contains(player.getUUID());
	}

	// ------------------------------------------------------------------ Overenchant (anvil mixins, both sides)

	public static boolean overenchant(Player player) {
		return SideBridge.majorActive(player, MiscIds.OVERENCHANT);
	}

	/** An enchantment's level cap in the anvil: Overenchant raises it to 8 (never lowers a modded cap). */
	public static int anvilMaxLevel(Player player, int vanillaMax) {
		return overenchant(player) ? Math.max(vanillaMax, config().overenchantMaxLevel) : vanillaMax;
	}

	// ------------------------------------------------------------------ Rainbow

	/** Rainbow: bows and crossbows don't use up any ammo in the Rainbow tag (you still need one to shoot). */
	public static boolean rainbowFreeAmmo(ItemStack weapon, ItemStack ammo, ServerPlayer player) {
		return (Weapons.is(weapon, Weapons.Family.BOW) || Weapons.is(weapon, Weapons.Family.CROSSBOW)) && ammo.is(BloomTags.RAINBOW_AMMO)
				&& active(player, MiscIds.RAINBOW);
	}
}
