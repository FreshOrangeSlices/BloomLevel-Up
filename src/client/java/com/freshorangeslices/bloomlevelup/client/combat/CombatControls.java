package com.freshorangeslices.bloomlevelup.client.combat;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
 *
 * <p>One rebindable <b>weapon-skill key</b> (default: right click) runs the weapon Majors:
 * <ul>
 *   <li>hold it to charge (Charge Attack / Heavy Hands); once the swing has recovered and the hold lasted long
 *   enough the weapon glows, and letting go swings at whatever is in the crosshair for double damage;</li>
 *   <li>tap it to throw (Catch!).</li>
 * </ul>
 * While it shares right click, it steps aside for vanilla: a block that does something when clicked (chests,
 * doors, stripping logs), a mob that does something when clicked (villagers, horses), anything in the off
 * hand, and items with their own right click (tridents, spears, bows, food) keep working as usual. A mace
 * aimed at the ground does Wind Impact instead. Bound to its own key, it never touches right click.
 *
 * <p>Lightning Summon stays on attack: sneak + left click with a trident while not aiming at a mob.
 */
public final class CombatControls {
	/** Released sooner than this (ticks) counts as a tap: a throw. */
	private static final int TAP_TICKS = 6;

	private static KeyMapping skillKey;
	private static boolean holding;
	private static int holdTicks;
	private static boolean charged;
	private static boolean performing;

	private CombatControls() {
	}

	public static void init(KeyMapping.Category category) {
		skillKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.weapon_skill", InputConstants.Type.MOUSE,
				InputConstants.MOUSE_BUTTON_RIGHT, category));

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

			if (windBurstAimed(minecraft, held) || !claimable(player, held)) {
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
				&& (canCharge(held) || canThrow(held));
	}

	/** A mace pointed at the top of a block with Wind Impact: that click is the wind burst (the server does it). */
	private static boolean windBurstAimed(Minecraft minecraft, ItemStack held) {
		return Weapons.is(held, Family.MACE) && active(CombatIds.WIND_IMPACT) && minecraft.hitResult instanceof BlockHitResult block
				&& block.getType() == HitResult.Type.BLOCK && block.getDirection() == Direction.UP;
	}

	/** Starts a press of the weapon-skill key. Returns whether the skill took it. */
	private static boolean begin(ItemStack held) {
		boolean charge = canCharge(held);

		if (!charge && canThrow(held)) {
			send(CombatAction.THROW); // nothing to charge: throw at once
			return true;
		}

		if (charge) {
			holding = true;
			holdTicks = 0;
			charged = false;
			return true;
		}

		return false;
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

	/** Returns true to cancel vanilla's attack for this click (Lightning Summon). */
	public static boolean interceptAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (performing || player == null || !ClientBloomState.ready() || player.isSpectator()) {
			return false;
		}

		HitResult hit = minecraft.hitResult;
		boolean aimingAtMob = hit != null && hit.getType() == HitResult.Type.ENTITY;

		if (player.isShiftKeyDown() && !aimingAtMob && canSummon(player.getMainHandItem())) {
			send(CombatAction.SUMMON_LIGHTNING);
			return true;
		}

		return false;
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

				if (!holding && !player.isSpectator() && (canCharge(held) || canThrow(held))) {
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

		if (wasCharged) {
			send(CombatAction.CHARGED);
			HitResult hit = minecraft.hitResult;

			// Swing at what's in the crosshair. Never at a block (letting go must not start mining it), and not
			// when the key was "released" by a screen opening.
			if ((hit == null || hit.getType() != HitResult.Type.BLOCK) && minecraft.gui.screen() == null) {
				performing = true;

				try {
					((MinecraftInvoker) minecraft).bloomlevelup$startAttack();
				} finally {
					performing = false;
				}
			}
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
