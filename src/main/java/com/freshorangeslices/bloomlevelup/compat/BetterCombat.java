package com.freshorangeslices.bloomlevelup.compat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Better Combat compatibility. Better Combat is optional: Bloom only compiles against it, everything here checks
 * {@link #installed()} first, and without it nothing changes. Code that touches Better Combat's own classes lives
 * in {@link BetterCombatBridge}, which is only loaded when it's installed.
 *
 * <p>With it, a swing can hit several mobs, each through {@code Player.attack} like vanilla, so every one of them
 * is a normal hit for Bloom. On top of that: Predator builds on any mob ({@code CombatSkill}), Bloom's own Batter
 * Up! sweep is skipped (Better Combat already hits everything in the arc), Full Sweep (swords) and Batter Up!
 * (everything else) cancel Better Combat's penalty for hitting several mobs at once, a charge lasts long enough
 * for the swing's wind-up, and a charged swing lands with a shockwave (the client plays a slam for it).
 */
public final class BetterCombat {
	public static final String MOD_ID = "bettercombat";
	/** Bloom's counter to Better Combat's crowd penalty, on attack damage for the length of one swing. */
	public static final Identifier CROWD_MOD = BloomLevelUp.id("bettercombat_crowd");
	private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
	/** How many mobs each player's current Better Combat swing is hitting, and the tick it was asked for. */
	private static final Map<UUID, Request> REQUESTS = new HashMap<>();
	/** Players carrying {@link #CROWD_MOD} right now (taken off again at the end of the tick at the latest). */
	private static final Set<UUID> COUNTERED = new HashSet<>();

	private record Request(int targets, long tick) {
	}

	/** The crowd rule, as Better Combat's server config has it. */
	public record CrowdRule(boolean enabled, double maxPenalty, int extraTargets, boolean vanillaSweeping) {
		/**
		 * Better Combat's change to a swing's attack damage when it hits {@code targets} mobs, with a sweep ratio of
		 * {@code sweepRatio}: up to -maxPenalty as more mobs are hit, plus maxPenalty x the sweep ratio.
		 */
		public double term(int targets, double sweepRatio) {
			if (!enabled || targets < 2 || extraTargets <= 0) {
				return 0.0;
			}

			return -maxPenalty / extraTargets * Math.min(extraTargets, targets - 1) + maxPenalty * sweepRatio;
		}
	}

	/** One swing of a weapon's Better Combat combo: its shape ("horizontal_plane", ...), arc and damage multiplier. */
	public record Swing(String shape, double angle, double damageMultiplier) {
	}

	private BetterCombat() {
	}

	public static boolean installed() {
		return INSTALLED;
	}

	public static void init() {
		if (!INSTALLED) {
			return;
		}

		ServerTickEvents.END_SERVER_TICK.register(BetterCombat::endTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REQUESTS.clear();
			COUNTERED.clear();
		});
	}

	/** Start of Better Combat's attack request (mixin): how many mobs this swing will hit. */
	public static void request(ServerPlayer player, int targets) {
		REQUESTS.put(player.getUUID(), new Request(targets, player.level().getGameTime()));
	}

	/** Whether Better Combat is in the middle of this player's swing (its combo counter is only set then). */
	public static boolean inSwing(Player player) {
		return INSTALLED && BetterCombatBridge.comboCount(player) >= 0;
	}

	/**
	 * Start of every {@code Player.attack}. Inside a Better Combat swing that hits several mobs, with Full Sweep on a
	 * sword or Batter Up! on any other melee weapon: puts Bloom's counter to the crowd penalty on attack damage, so
	 * every mob takes the full hit. Anywhere else: takes the counter off.
	 */
	public static void beforeAttack(ServerPlayer player) {
		if (!INSTALLED) {
			return;
		}

		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage == null) {
			return;
		}

		double counter = inSwing(player) ? crowdCounter(player) : 0.0;

		if (Math.abs(counter) < 1.0E-9) {
			damage.removeModifier(CROWD_MOD);
			COUNTERED.remove(player.getUUID());
		} else {
			damage.addOrUpdateTransientModifier(new AttributeModifier(CROWD_MOD, counter, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			COUNTERED.add(player.getUUID());
		}
	}

	/** What Bloom adds to Better Combat's crowd term so it never goes below zero, or 0 when nothing cancels it. */
	static double crowdCounter(ServerPlayer player) {
		Request request = REQUESTS.get(player.getUUID());

		if (request == null || request.tick() != player.level().getGameTime() || request.targets() < 2) {
			return 0.0;
		}

		ItemStack weapon = player.getMainHandItem();
		boolean fullSweep = CombatSkill.has(player, CombatIds.FULL_SWEEP, Family.SWORD, weapon);
		boolean batterUp = !Weapons.is(weapon, Family.SWORD) && Weapons.isMeleeWeapon(weapon) && CombatSkill.active(player, CombatIds.BATTER_UP);

		if (!fullSweep && !batterUp) {
			return 0.0;
		}

		AttributeInstance sweep = player.getAttribute(Attributes.SWEEPING_DAMAGE_RATIO);
		double ratio = sweep == null ? 0.0 : sweep.getValue();
		AttributeModifier fullSweepRatio = sweep == null ? null : sweep.getModifier(StatEffects.FULL_SWEEP_MOD);
		// Full Sweep's own ratio would turn into a bonus under Better Combat's rule; it only cancels the penalty.
		double bloomRatio = fullSweepRatio == null ? 0.0 : fullSweepRatio.amount();
		CrowdRule rule = crowdRule();
		return Math.max(0.0, rule.term(request.targets(), ratio - bloomRatio)) - rule.term(request.targets(), ratio);
	}

	/** Better Combat's crowd rule (its defaults when it isn't installed). */
	public static CrowdRule crowdRule() {
		return INSTALLED ? BetterCombatBridge.crowdRule() : new CrowdRule(true, 0.5, 4, false);
	}

	/** A weapon's Better Combat combo, or an empty list when Better Combat doesn't handle it. */
	public static List<Swing> swings(ItemStack weapon) {
		return INSTALLED ? BetterCombatBridge.swings(weapon) : List.of();
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

		level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.8F, 0.9F);
	}

	private static void endTick(MinecraftServer server) {
		if (!COUNTERED.isEmpty()) {
			for (UUID id : COUNTERED) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				AttributeInstance damage = player == null ? null : player.getAttribute(Attributes.ATTACK_DAMAGE);

				if (damage != null) {
					damage.removeModifier(CROWD_MOD);
				}
			}

			COUNTERED.clear();
		}

		REQUESTS.clear();
	}

	/** Takes the crowd counter off (game tests, whose fake players aren't in the player list). */
	public static void clearForTests(ServerPlayer player) {
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage != null) {
			damage.removeModifier(CROWD_MOD);
		}

		COUNTERED.remove(player.getUUID());
		REQUESTS.remove(player.getUUID());
	}
}
