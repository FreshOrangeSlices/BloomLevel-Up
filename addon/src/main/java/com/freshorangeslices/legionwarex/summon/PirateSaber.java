package com.freshorangeslices.legionwarex.summon;

import com.freshorangeslices.legionwarex.AddonConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bosses'Rise's Pirate Saber, toned down (FreshOrangeSlices, 2026-11-22: "12 is high"): attack damage
 * {@code pirateSaberDamage} as its tooltip shows it (9; the player's own 1 plus the weapon's modifier), and
 * {@code pirateSaberCooldownTicks} (10 seconds) before it summons again, in place of its own 4. The cooldown is put on
 * after the saber's own, as the charge finishes. Its summons are {@link Summons}'.
 */
public final class PirateSaber {
	public static final ResourceLocation ID = new ResourceLocation("block_factorys_bosses", "pirate_saber");
	/** Item.BASE_ATTACK_DAMAGE_UUID (protected there): a weapon's own attack damage modifier. */
	static final UUID BASE_ATTACK_DAMAGE = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

	private PirateSaber() {
	}

	public static boolean is(ItemStack stack) {
		return !stack.isEmpty() && ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
	}

	@SubscribeEvent
	public static void onModifiers(ItemAttributeModifierEvent event) {
		if (event.getSlotType() != EquipmentSlot.MAINHAND || !is(event.getItemStack())) {
			return;
		}

		double shown = AddonConfig.pirateSaberDamage();

		for (AttributeModifier modifier : List.copyOf(event.getModifiers().get(Attributes.ATTACK_DAMAGE))) {
			if (BASE_ATTACK_DAMAGE.equals(modifier.getId())) {
				event.removeModifier(Attributes.ATTACK_DAMAGE, modifier);
				event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE, modifier.getName(), shown - 1.0,
						modifier.getOperation()));
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFinish(LivingEntityUseItemEvent.Finish event) {
		if (event.getEntity() instanceof ServerPlayer player && is(event.getItem())) {
			int ticks = AddonConfig.pirateSaberCooldown();

			if (ticks > 0) {
				player.getCooldowns().addCooldown(event.getItem().getItem(), ticks);
			} else {
				player.getCooldowns().removeCooldown(event.getItem().getItem());
			}
		}
	}
}
