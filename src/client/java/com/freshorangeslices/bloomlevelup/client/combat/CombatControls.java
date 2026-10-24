package com.freshorangeslices.bloomlevelup.client.combat;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import com.freshorangeslices.bloomlevelup.client.compat.BetterCombatClient;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.mixin.MinecraftInvoker;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Client side of the input Majors. The client only says what the player did; the server decides.
 *
 * <p>One rebindable <b>weapon-skill key</b> (default: R) runs every weapon Major that needs input. What it
 * does depends on the weapon in hand, the weapon's own skill first:
 * <ul>
 *   <li>a mace with Wind Impact: a wind burst on the block you aim at (or under you);</li>
 *   <li>a trident with Lightning Summon: lightning where you aim;</li>
 *   <li>hold to charge (Charge Attack / Heavy Hands); once the swing has recovered and the hold lasted long
 *   enough the meter turns gold, and letting go swings at whatever is in the crosshair for double damage;</li>
 *   <li>tap to throw (Catch!).</li>
 * </ul>
 * Majors that would share the key on one weapon can't be owned together (Major JSON conflicts), so normally
 * only one applies. Master of Arms lifts that and combines them: with a charge Major every press is a hold,
 * and letting go decides. A full charge released on a mob in reach is a charged swing; otherwise it is the
 * charged version of the weapon's skill (Charged Burst, Storm Call, Charged Throw). A tap or early release
 * is the normal skill. Gale Throw (Catch! + Wind Impact on a mace): aiming at a block bursts, anything else
 * throws.
 *
 * <p>If the key is bound to right click instead, it steps aside for vanilla: blocks and mobs that use a
 * click, anything in the off hand, and items with their own right click keep working as usual.
 */
public final class CombatControls {
	/** Released sooner than this (ticks) counts as a tap: a throw. */
	private static final int TAP_TICKS = 6;

	private static KeyMapping skillKey;
	private static boolean holding;
	private static int holdTicks;
	private static boolean charged;

	private CombatControls() {
	}

	public static void init(KeyMapping.Category category) {
		skillKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.weapon_skill", InputConstants.Type.KEYBOARD,
				InputConstants.KEY_R, category));

		ClientTickEvents.END_CLIENT_TICK.register(CombatControls::tick);

		// Right click that no block or mob used: the item's own use would be next, so the skill can take it.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			Minecraft minecraft = Minecraft.getInstance();

			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || player != minecraft.player || !onUseKey(minecraft)) {
				return InteractionResult.PASS;
			}

			if (holding) {
				return InteractionResult.FAIL;
			}

			ItemStack held = player.getMainHandItem();

			if (!claimable(player, held)) {
				return InteractionResult.PASS;
			}

			return begin(held) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
	}

	// ------------------------------------------------------------------ what the player can do (synced profile)

	private static boolean active(String majorId) {
		BloomProfile profile = ClientBloomState.profile();
		return ClientBloomState.ready() && profile != null && ClientBloomState.service().isMajorActive(profile, majorId);
	}

	private static Optional<String> copied() {
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		return profile == null ? Optional.empty() : service.activeCopySource(profile, CombatIds.COPYCAT);
	}

	private static boolean has(String majorId, Family family, ItemStack weapon) {
		return active(majorId) && (Weapons.is(weapon, family) || copied().map(majorId::equals).orElse(false));
	}

	private static boolean canCharge(ItemStack weapon) {
		return has(CombatIds.CHARGE_ATTACK, Family.AXE, weapon) || (active(CombatIds.HEAVY_HANDS) && Weapons.isMeleeWeapon(weapon));
	}

	private static boolean canSummon(ItemStack weapon) {
		return has(CombatIds.LIGHTNING_SUMMON, Family.TRIDENT, weapon);
	}

	private static boolean canThrow(ItemStack weapon) {
		return active(CombatIds.CATCH) && CombatAbilities.throwable(weapon);
	}

	private static boolean canWindBurst(ItemStack weapon) {
		return Weapons.is(weapon, Family.MACE) && active(CombatIds.WIND_IMPACT);
	}

	/** Master of Arms: the weapon skills combine. */
	private static boolean combos() {
		return active(CombatIds.MASTER_OF_ARMS);
	}

	private static boolean aimingAtBlock(Minecraft minecraft) {
		return minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK;
	}

	/** Gale Throw's split, and a mace's burst-or-throw in general: a block in the crosshair bursts, anything else throws. */
	private static boolean burstHere(Minecraft minecraft, ItemStack weapon) {
		return canWindBurst(weapon) && (!canThrow(weapon) || aimingAtBlock(minecraft));
	}

	/** Anything the weapon-skill key does with this item. */
	private static boolean hasSkill(ItemStack weapon) {
		return canWindBurst(weapon) || canSummon(weapon) || canCharge(weapon) || canThrow(weapon);
	}

	private static void send(CombatAction action) {
		if (ClientPlayNetworking.canSend(BloomPayloads.CombatInput.TYPE)) {
			ClientPlayNetworking.send(new BloomPayloads.CombatInput(action));
		}
	}

	private static boolean onUseKey(Minecraft minecraft) {
		return skillKey != null && skillKey.same(minecraft.options.keyUse);
	}

	/** Whether right click may be taken from vanilla for this item at all. */
	private static boolean claimable(Player player, ItemStack held) {
		return !player.isSpectator() && player.getOffhandItem().isEmpty() && (held.isEmpty() || held.getUseDuration(player) <= 0)
				&& hasSkill(held);
	}

	/**
	 * Starts a press of the weapon-skill key. Returns whether a skill took it. The weapon's own skill wins
	 * (Wind Impact on a mace, Lightning Summon on a trident), then a charge (hold), then a throw (tap).
	 * With Master of Arms a charge comes first and the release picks (see the class comment).
	 */
	private static boolean begin(ItemStack held) {
		if (combos()) {
			if (canCharge(held)) {
				startHold();
				return true;
			}

			if (canWindBurst(held) && canThrow(held)) {
				send(burstHere(Minecraft.getInstance(), held) ? CombatAction.WIND_BURST : CombatAction.THROW);
				return true;
			}
		}

		if (canWindBurst(held)) {
			send(CombatAction.WIND_BURST);
			return true;
		}

		if (canSummon(held)) {
			send(CombatAction.SUMMON_LIGHTNING);
			return true;
		}

		boolean charge = canCharge(held);

		if (!charge && canThrow(held)) {
			send(CombatAction.THROW); // nothing to charge: throw at once
			return true;
		}

		if (charge) {
			startHold();
			return true;
		}

		return false;
	}

	private static void startHold() {
		holding = true;
		holdTicks = 0;
		charged = false;
	}

	// ------------------------------------------------------------------ vanilla hooks (MinecraftMixin)

	/**
	 * Right click aimed at the air or at an enemy (things a right click does nothing to), before vanilla
	 * handles it. Blocks and other mobs go through vanilla first and reach the skill through
	 * {@link UseItemCallback} only if they didn't use the click. Returns true to cancel vanilla's use.
	 */
	public static boolean interceptUse(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || !ClientBloomState.ready() || !onUseKey(minecraft)) {
			return false;
		}

		if (holding) {
			return true;
		}

		HitResult hit = minecraft.hitResult;
		boolean air = hit == null || hit.getType() == HitResult.Type.MISS;
		boolean enemy = hit instanceof EntityHitResult entityHit && isEnemy(entityHit.getEntity());
		ItemStack held = player.getMainHandItem();

		if ((air || enemy) && claimable(player, held)) {
			return begin(held);
		}

		return false;
	}

	private static boolean isEnemy(Entity entity) {
		return entity.getType().getCategory() == MobCategory.MONSTER || entity instanceof Player;
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || skillKey == null) {
			holding = false;
			return;
		}

		// Bound to its own key: presses arrive as clicks (vanilla's right click is untouched).
		if (!onUseKey(minecraft)) {
			while (skillKey.consumeClick()) {
				ItemStack held = player.getMainHandItem();

				if (!holding && !player.isSpectator() && hasSkill(held)) {
					begin(held);
				}
			}
		}

		if (!holding) {
			return;
		}

		KeyMapping trigger = onUseKey(minecraft) ? minecraft.options.keyUse : skillKey;
		ItemStack held = player.getMainHandItem();

		if (!canCharge(held)) {
			holding = false;
			charged = false;
			return;
		}

		if (!trigger.isDown()) {
			release(minecraft, held);
			return;
		}

		holdTicks++;

		if (!charged && holdTicks >= chargeTicks() && player.getAttackStrengthScale(0.0F) >= 1.0F) {
			charged = true;
			// Charged cue: a heavy whoosh, then crit sparks while held (and the HUD meter turns gold).
			player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.5F, 0.6F);
		}

		if (charged && holdTicks % 4 == 0 && minecraft.level != null) {
			minecraft.level.addParticle(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 0.0, 0.1, 0.0);
		}
	}

	private static void release(Minecraft minecraft, ItemStack held) {
		holding = false;
		boolean wasCharged = charged;
		charged = false;
		boolean combos = combos();
		boolean atMob = minecraft.hitResult instanceof EntityHitResult; // the crosshair only finds mobs in reach

		if (wasCharged) {
			// Master of Arms: a charge let go anywhere but on a mob is the charged version of the weapon's skill.
			if (combos && !atMob && minecraft.gui.screen() == null) {
				if (burstHere(minecraft, held)) {
					send(CombatAction.CHARGED_BURST);
					return;
				}

				if (canSummon(held)) {
					send(CombatAction.STORM_CALL);
					return;
				}

				if (canThrow(held)) {
					send(CombatAction.CHARGED_THROW);
					return;
				}
			}

			send(CombatAction.CHARGED);

			// Swing at what's in the crosshair. Never at a block (letting go must not start mining it), and not
			// when the key was "released" by a screen opening.
			// With Better Combat the swing it starts plays a slam (BetterCombatClient).
			if (!aimingAtBlock(minecraft) && minecraft.gui.screen() == null) {
				BetterCombatClient.chargedSwing(minecraft, () -> ((MinecraftInvoker) minecraft).bloomlevelup$startAttack());
			}

			return;
		}

		// Not charged: the normal skill (Master of Arms held it back to see whether this would be a charge).
		if (combos && burstHere(minecraft, held)) {
			send(CombatAction.WIND_BURST);
		} else if (combos && canSummon(held)) {
			send(CombatAction.SUMMON_LIGHTNING);
		} else if (holdTicks < TAP_TICKS && canThrow(held)) {
			send(CombatAction.THROW);
		}
	}

	private static int chargeTicks() {
		return ClientBloomState.config() == null ? 20 : ClientBloomState.config().chargeAttackTicks;
	}

	// ------------------------------------------------------------------ HUD

	/** Charge meter: 0..1 while the weapon-skill key is held for a charge, or -1 when not charging. */
	public static float chargeProgress() {
		if (!holding) {
			return -1.0F;
		}

		return charged ? 1.0F : Math.min(0.99F, holdTicks / (float) Math.max(1, chargeTicks()));
	}

	public static boolean isCharged() {
		return holding && charged;
	}
}
