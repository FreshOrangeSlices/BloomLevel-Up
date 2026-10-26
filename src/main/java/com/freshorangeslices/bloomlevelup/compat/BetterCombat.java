package com.freshorangeslices.bloomlevelup.compat;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Better Combat compatibility, Forge 1.20.1 build (Better Combat 1.9.0+1.20.1, the newest for 1.20.1; with an older
 * one the compat stays off). Better Combat is optional: Bloom only compiles against it, everything here checks
 * {@link #installed()} first, and without it nothing changes. Code that touches Better Combat's own classes lives in
 * {@link BetterCombatBridge}, which is only loaded when it's installed.
 *
 * <p>With it, a swing can hit several mobs, each through {@code Player.attack} like vanilla, so every one of them is
 * a normal hit for Bloom. On top of that: Predator builds on any mob ({@code CombatSkill}), Bloom's own Batter Up!
 * sweep is skipped (Better Combat already hits everything in the arc), Full Sweep (swords) and Batter Up! (everything
 * else) cancel Better Combat's penalty for hitting several mobs at once, a charge lasts long enough for the swing's
 * wind-up, and a charged swing lands with a shockwave (the client plays a slam for it).
 *
 * <p>Better Combat's server runs a whole swing at once: it puts its combo multiplier (and, for a swing that hits
 * several mobs, its crowd penalty) on the player's attack damage, calls {@code Player.attack} on each mob, then takes
 * them off. Bloom recognises the swing by those modifiers, so it needs no mixin into Better Combat.
 */
public final class BetterCombat {
	public static final String MOD_ID = "bettercombat";
	/** The oldest Better Combat this compat was checked against (its 1.20.1 API). */
	static final int[] MIN_VERSION = {1, 9, 0};
	/** The names Better Combat gives its attack damage modifiers for the length of a swing (1.20.1). */
	static final String SWING_MODIFIER = "COMBO_DAMAGE_MULTIPLIER";
	static final String CROWD_MODIFIER = "SWEEPING_DAMAGE_MODIFIER";
	/** Bloom's counter to the crowd penalty: on attack damage for the length of one {@code Player.attack}. */
	public static final StatEffects.ModifierId CROWD_MOD = StatEffects.ModifierId.of("bettercombat_crowd");
	private static Boolean installed;

	/** The crowd rule, as Better Combat's server config has it. */
	public record CrowdRule(boolean enabled, double maxPenalty, int extraTargets, double enchantRestores, boolean vanillaSweeping) {
		/** Sweeping Edge's top level (Better Combat's restore is shared out over it). */
		private static final int SWEEPING_MAX = 3;

		/**
		 * Better Combat's multiplier on a swing's attack damage when it hits {@code targets} mobs with Sweeping Edge at
		 * {@code sweeping}: down to 1 - maxPenalty as more mobs are hit, each Sweeping Edge level giving some back,
		 * never above 1.
		 */
		public double multiplier(int targets, int sweeping) {
			if (!enabled || targets < 2 || extraTargets <= 0) {
				return 1.0;
			}

			double multiplier = 1.0 - maxPenalty / extraTargets * Math.min(extraTargets, targets - 1) + sweeping * enchantRestores / SWEEPING_MAX;
			return Math.min(multiplier, 1.0);
		}
	}

	/** One swing of a weapon's Better Combat combo: its shape ("horizontal_plane", ...), arc and damage multiplier. */
	public record Swing(String shape, double angle, double damageMultiplier) {
	}

	private BetterCombat() {
	}

	/** Whether Better Combat (1.9.0 or newer) is installed, so the compat runs. */
	public static boolean installed() {
		if (installed == null) {
			ModList mods = ModList.get();

			if (mods == null) {
				return false; // too early to tell; asked again later
			}

			installed = mods.getModContainerById(MOD_ID).map(container -> supported(container.getModInfo().getVersion().toString())).orElse(false);
		}

		return installed;
	}

	/** Whether this Better Combat version ("1.9.0+1.20.1") is one the compat was checked against. */
	static boolean supported(String version) {
		String[] parts = version.split("[+-]", 2)[0].split("\\.");

		for (int i = 0; i < MIN_VERSION.length; i++) {
			int part;

			try {
				part = i < parts.length ? Integer.parseInt(parts[i]) : 0;
			} catch (NumberFormatException e) {
				return false;
			}

			if (part != MIN_VERSION[i]) {
				return part > MIN_VERSION[i];
			}
		}

		return true;
	}

	/** Better Combat present at all, even one too old for the compat (for the log). */
	public static Optional<String> presentVersion() {
		ModList mods = ModList.get();
		return mods == null ? Optional.empty() : mods.getModContainerById(MOD_ID).map(container -> container.getModInfo().getVersion().toString());
	}

	public static void init() {
		presentVersion().ifPresent(version -> {
			if (installed()) {
				com.freshorangeslices.bloomlevelup.BloomLevelUp.LOGGER.info("Better Combat {} found: Bloom's Better Combat compat is on", version);
			} else {
				com.freshorangeslices.bloomlevelup.BloomLevelUp.LOGGER.warn(
						"Better Combat {} is older than Bloom's compat needs (1.9.0+1.20.1); Bloom ignores it", version);
			}
		});
	}

	/** Whether Better Combat is in the middle of this player's swing (its combo multiplier is on attack damage). */
	public static boolean inSwing(Player player) {
		return installed() && named(player.getAttribute(Attributes.ATTACK_DAMAGE), SWING_MODIFIER) != null;
	}

	/**
	 * Start of every {@code Player.attack}. Inside a Better Combat swing that hits several mobs, with Full Sweep on a
	 * sword or Batter Up! on any other melee weapon: puts Bloom's counter to the crowd penalty on attack damage, so
	 * every mob takes the full hit. {@link #afterAttack} takes it off again.
	 */
	public static void beforeAttack(ServerPlayer player) {
		if (!installed()) {
			return;
		}

		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage == null) {
			return;
		}

		damage.removeModifier(CROWD_MOD.uuid());
		double counter = crowdCounter(player, damage);

		if (counter > 1.0E-9) {
			damage.addTransientModifier(new AttributeModifier(CROWD_MOD.uuid(), CROWD_MOD.name(), counter, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** End of every {@code Player.attack} (mixin): the counter never outlives the hit it was for. */
	public static void afterAttack(Player player) {
		if (!installed() || player.level().isClientSide()) {
			return;
		}

		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage != null) {
			damage.removeModifier(CROWD_MOD.uuid());
		}
	}

	/**
	 * What cancels Better Combat's crowd penalty on this hit, as a total multiplier (0 when nothing does): its penalty
	 * is a total multiplier of (1 + a) with a below 0, so Bloom adds one of 1 / (1 + a) - 1.
	 */
	static double crowdCounter(ServerPlayer player, AttributeInstance damage) {
		if (named(damage, SWING_MODIFIER) == null) {
			return 0.0;
		}

		AttributeModifier crowd = named(damage, CROWD_MODIFIER);

		if (crowd == null || crowd.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL || crowd.getAmount() >= 0.0
				|| crowd.getAmount() <= -0.999) {
			return 0.0;
		}

		ItemStack weapon = player.getMainHandItem();
		boolean fullSweep = CombatSkill.has(player, CombatIds.FULL_SWEEP, Family.SWORD, weapon);
		boolean batterUp = !Weapons.is(weapon, Family.SWORD) && Weapons.isMeleeWeapon(weapon) && CombatSkill.active(player, CombatIds.BATTER_UP);

		if (!fullSweep && !batterUp) {
			return 0.0;
		}

		return 1.0 / (1.0 + crowd.getAmount()) - 1.0;
	}

	private static AttributeModifier named(AttributeInstance instance, String name) {
		if (instance == null) {
			return null;
		}

		for (AttributeModifier modifier : instance.getModifiers()) {
			if (name.equals(modifier.getName())) {
				return modifier;
			}
		}

		return null;
	}

	/** Better Combat's crowd rule (its defaults when it isn't installed). */
	public static CrowdRule crowdRule() {
		return installed() ? BetterCombatBridge.crowdRule() : new CrowdRule(true, 0.5, 4, 0.5, false);
	}

	/** A weapon's Better Combat combo, or an empty list when Better Combat doesn't handle it. */
	public static List<Swing> swings(ItemStack weapon) {
		return installed() ? BetterCombatBridge.swings(weapon) : List.of();
	}

	/** As above, as this player swings it (the same on this build: Batter Up! changes no weapon's swings here). */
	public static List<Swing> swings(Player player, ItemStack weapon) {
		return swings(weapon);
	}

	/** The animation of the swing this player's combo is on (for tests), or null without Better Combat. */
	public static String attackAnimation(Player player, int combo) {
		return installed() ? BetterCombatBridge.attackAnimation(player, combo) : null;
	}

	/** A charged swing's landing under Better Combat: a ring of dust, a small blast and a heavy thud. */
	public static void slamShockwave(ServerLevel level, LivingEntity target) {
		double x = target.getX();
		double y = target.getY() + 0.1;
		double z = target.getZ();
		level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.4, z, 1, 0.0, 0.0, 0.0, 0.0);

		for (int i = 0; i < 18; i++) {
			double angle = Math.PI * 2.0 * i / 18.0;
			// Count 0: the three "spread" numbers are the particle's velocity, so the ring rolls outward.
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, Math.cos(angle) * 0.35, 0.02, Math.sin(angle) * 0.35, 1.0);
		}

		// Better Combat's own hammer slam (1.20.1 has no mace); a falling anvil's thud if it's missing.
		SoundEvent slam = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(MOD_ID, "hammer_slam"));
		level.playSound(null, x, y, z, slam != null ? slam : SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, slam != null ? 1.0F : 0.4F, 0.9F);
	}

	/** Takes the crowd counter off (game tests). */
	public static void clearForTests(ServerPlayer player) {
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage != null) {
			damage.removeModifier(CROWD_MOD.uuid());
		}
	}
}
