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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

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
 * <ul>
 *   <li>Charge Attack / Heavy Hands: hold attack (default: left click) on a mob or the air; once the swing has
 *   recovered and the hold lasts long enough the weapon is charged; releasing swings. A tap still attacks
 *   normally (on release). The "Charge attack" key can be moved to its own key in Controls.</li>
 *   <li>Lightning Summon: sneak + attack with a trident while not aiming at a mob.</li>
 *   <li>Catch!: use (default: right click) with a sword, axe or mace throws it. "Throw weapon" can be rebound.</li>
 * </ul>
 */
public final class CombatControls {
	private static KeyMapping chargeKey;
	private static KeyMapping throwKey;
	private static boolean holding;
	private static int holdTicks;
	private static boolean charged;
	private static boolean performing;

	private CombatControls() {
	}

	public static void init(KeyMapping.Category category) {
		chargeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.charge", InputConstants.Type.MOUSE,
				InputConstants.MOUSE_BUTTON_LEFT, category));
		throwKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.throw", InputConstants.Type.MOUSE,
				InputConstants.MOUSE_BUTTON_RIGHT, category));

		ClientTickEvents.END_CLIENT_TICK.register(CombatControls::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || throwKey == null) {
				return InteractionResult.PASS;
			}

			Minecraft minecraft = Minecraft.getInstance();

			if (player == minecraft.player && throwKey.same(minecraft.options.keyUse) && canThrow(player.getMainHandItem())) {
				send(CombatAction.THROW);
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
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

	private static void send(CombatAction action) {
		if (ClientPlayNetworking.canSend(BloomPayloads.CombatInput.TYPE)) {
			ClientPlayNetworking.send(new BloomPayloads.CombatInput(action));
		}
	}

	// ------------------------------------------------------------------ attack key (Minecraft.startAttack mixin)

	/** Returns true to cancel vanilla's attack for this click. */
	public static boolean interceptAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (performing || player == null || !ClientBloomState.ready() || player.isSpectator()) {
			return false;
		}

		ItemStack held = player.getMainHandItem();
		HitResult hit = minecraft.hitResult;
		boolean aimingAtMob = hit != null && hit.getType() == HitResult.Type.ENTITY;
		boolean aimingAtBlock = hit != null && hit.getType() == HitResult.Type.BLOCK;

		if (player.isShiftKeyDown() && !aimingAtMob && canSummon(held)) {
			send(CombatAction.SUMMON_LIGHTNING);
			return true;
		}

		if (!aimingAtBlock && chargeKey != null && chargeKey.same(minecraft.options.keyAttack) && canCharge(held)) {
			holding = true;
			holdTicks = 0;
			charged = false;
			return true;
		}

		return false;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || chargeKey == null) {
			holding = false;
			return;
		}

		boolean sameKey = chargeKey.same(minecraft.options.keyAttack);

		// Key clicks only register while no screen is open, and opening a screen releases held keys.
		// A separately bound charge key starts charging on its own (vanilla's attack is untouched).
		if (!sameKey) {
			while (chargeKey.consumeClick()) {
				if (!holding && canCharge(player.getMainHandItem())) {
					holding = true;
					holdTicks = 0;
					charged = false;
				}
			}
		}

		if (!throwKey.same(minecraft.options.keyUse)) {
			while (throwKey.consumeClick()) {
				if (canThrow(player.getMainHandItem())) {
					send(CombatAction.THROW);
				}
			}
		}

		if (!holding) {
			return;
		}

		KeyMapping trigger = sameKey ? minecraft.options.keyAttack : chargeKey;

		if (!canCharge(player.getMainHandItem())) {
			holding = false;
			charged = false;
			return;
		}

		if (!trigger.isDown()) {
			release(minecraft);
			return;
		}

		holdTicks++;

		if (!charged && holdTicks >= chargeTicks() && player.getAttackStrengthScale(0.0F) >= 1.0F) {
			charged = true;
			// Charged cue: a heavy whoosh, then crit sparks while held.
			player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.5F, 0.6F);
		}

		if (charged && holdTicks % 4 == 0 && minecraft.level != null) {
			minecraft.level.addParticle(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 0.0, 0.1, 0.0);
		}
	}

	private static int chargeTicks() {
		return ClientBloomState.config() == null ? 20 : ClientBloomState.config().chargeAttackTicks;
	}

	private static void release(Minecraft minecraft) {
		holding = false;

		if (charged) {
			send(CombatAction.CHARGED);
		}

		charged = false;
		performing = true;

		try {
			((MinecraftInvoker) minecraft).bloomlevelup$startAttack();
		} finally {
			performing = false;
		}
	}
}
