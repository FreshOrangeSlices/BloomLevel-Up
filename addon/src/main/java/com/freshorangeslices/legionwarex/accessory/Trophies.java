package com.freshorangeslices.legionwarex.accessory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Trophy slot (FreshOrangeSlices, agreed in the batch after 0.9.0): one accessory slot ({@code trophy}, the add-on's curios files) for
 * armor pieces with a special effect, worn there for the effect alone, never their armor (Curios never gives a worn
 * piece its armor stats). Their mods only look at the real head slot, so the add-on gives each its effect, read in
 * their jars (probe of 2026-11-26):
 * <ul>
 * <li>Orchid Mantis Goggles (Bosses Oddities): +2.5 reach, attack speed x1.25 (a trophy's share; it stacks on a full
 * set of armor);</li>
 * <li>the Ashlord's skull (Bosses'Rise): Night Vision;</li>
 * <li>Lord Pumpkinhead's Hat (Born in Chaos): Fire Resistance, Infernal Flame, Soul Stratification and Living Bomb
 * taken off, and attackers set alight;</li>
 * <li>the Monstrous Helm (Cataclysm): at half health or below, a burst that hits everything within 4 blocks for half
 * the wearer's attack damage and throws it back, then Monstrous for 10 s (armor, toughness, knockback resistance);
 * every 17.5 s, as on the head;</li>
 * <li>the Infernal Crown (Skarrier Mobs): hurt by fire, its +20% damage for 2 s; by lava, +40%.</li>
 * </ul>
 * Each piece's tooltip names the slot and what it does there.
 */
public final class Trophies {
	public static final String SLOT = "trophy";
	public static final String GOGGLES = "oddities:orchid_mantis_goggle_helmet";
	public static final String SKULL = "block_factorys_bosses:dragon_skull";
	public static final String HAT = "born_in_chaos_v1:lord_pumpkinheads_hat_helmet";
	public static final String HELM = "cataclysm:monstrous_helm";
	public static final String CROWN = "skarrier_mobs:infernal_crown_helmet";
	/** What each does in the slot, for its tooltip. */
	public static final Map<String, String> SAYS = Map.of(
			GOGGLES, "+2.5 reach, x1.25 attack speed",
			SKULL, "Night Vision",
			HAT, "fire immunity; sets attackers on fire",
			HELM, "at half health: a burst that throws back what's near (every 17.5 s)",
			CROWN, "+20% damage when burned, +40% in lava");
	static final int HELM_COOLDOWN = 350;

	private Trophies() {
	}

	public static void register() {
		CuriosReach.onAttributes(EventPriority.NORMAL, Trophies::onAttributes);
	}

	static String id(ItemStack stack) {
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
		return id == null ? "" : id.toString();
	}

	/** Whether an entity wears this piece in the Trophy slot. */
	public static boolean wears(LivingEntity entity, String item) {
		return CuriosReach.worn(entity, SLOT).stream().anyMatch(stack -> id(stack).equals(item));
	}

	static MobEffect effect(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		return key == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(key);
	}

	static UUID uuid(String what) {
		return UUID.nameUUIDFromBytes(("legionwarex trophy " + what).getBytes(StandardCharsets.UTF_8));
	}

	/** The goggles' reach and attack speed, as the slot's own stats. */
	static void onAttributes(CuriosReach.AttributeEvent event) {
		if (!SLOT.equals(event.slot()) || !GOGGLES.equals(id(event.stack()))) {
			return;
		}

		for (String reach : List.of("forge:block_reach", "forge:entity_reach")) {
			Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(reach));

			if (attribute != null) {
				event.add(attribute, new AttributeModifier(uuid(reach), "Trophy: Orchid Mantis Goggles", 2.5, AttributeModifier.Operation.ADDITION));
			}
		}

		event.add(Attributes.ATTACK_SPEED, new AttributeModifier(uuid("attack_speed"), "Trophy: Orchid Mantis Goggles", 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	/** Once a second: the skull's and the hat's effects; every 5 ticks: the helm's burst. */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		Player player = event.player;

		if (event.phase != TickEvent.Phase.END || player.level().isClientSide() || player.tickCount % 5 != 2) {
			return;
		}

		List<ItemStack> worn = CuriosReach.worn(player, SLOT);

		if (worn.isEmpty()) {
			return;
		}

		for (ItemStack stack : worn) {
			String id = id(stack);

			if (player.tickCount % 20 == 2) {
				if (id.equals(SKULL)) {
					MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);

					if (current == null || current.getDuration() < 220) {
						player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, true));
					}
				} else if (id.equals(HAT)) {
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));

					for (String bad : List.of("born_in_chaos_v1:infernal_flame", "born_in_chaos_v1:soul_stratification", "born_in_chaos_v1:living_bomb")) {
						MobEffect effect = effect(bad);

						if (effect != null) {
							player.removeEffect(effect);
						}
					}
				}
			}

			if (id.equals(HELM)) {
				helm(player, stack);
			}
		}
	}

	/** The Monstrous Helm's burst, as Cataclysm's own (its inventory tick on the head). */
	static void helm(Player player, ItemStack stack) {
		if (player.getHealth() > player.getMaxHealth() / 2.0F || player.getCooldowns().isOnCooldown(stack.getItem())) {
			return;
		}

		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) / 2.0F;

		for (Entity near : player.level().getEntities(player, player.getBoundingBox().inflate(4.0))) {
			if (near instanceof LivingEntity living) {
				living.hurt(player.level().damageSources().mobAttack(player), damage);
				double dx = living.getX() - player.getX();
				double dz = living.getZ() - player.getZ();
				double d = Math.max(dx * dx + dz * dz, 0.001);
				living.push(dx / d * 1.5, 0.15, dz / d * 1.5);
			}
		}

		player.getCooldowns().addCooldown(stack.getItem(), HELM_COOLDOWN);
		MobEffect monstrous = effect("cataclysm:monstrous");

		if (monstrous != null) {
			player.addEffect(new MobEffectInstance(monstrous, 200, 0, false, true));
		}
	}

	/** The hat sets attackers alight; the crown turns being burned into damage. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity hurt = event.getEntity();

		if (hurt.level().isClientSide() || !(hurt instanceof Player player)) {
			return;
		}

		Entity attacker = event.getSource().getEntity();

		if (attacker instanceof LivingEntity living && attacker != player && wears(player, HAT)) {
			living.setSecondsOnFire(5);
		}

		boolean lava = event.getSource().is(DamageTypes.LAVA);
		boolean fire = event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE);

		if ((lava || fire) && wears(player, CROWN)) {
			MobEffect bonus = effect("skarrier_mobs:infernal_crown_damage_bonus");

			if (bonus != null) {
				player.addEffect(new MobEffectInstance(bonus, 40, lava ? 1 : 0, false, false));
			}
		}
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		String says = SAYS.get(id(event.getItemStack()));

		if (says != null) {
			event.getToolTip().add(Component.literal("Trophy slot: " + says).withStyle(ChatFormatting.GOLD));
		}
	}

	/** For the boot test: which pieces and effects this layer has. */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> here = SAYS.keySet().stream().sorted().filter(id -> ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))).toList();
		List<String> effects = List.of("cataclysm:monstrous", "skarrier_mobs:infernal_crown_damage_bonus").stream()
				.filter(id -> effect(id) != null).toList();
		com.freshorangeslices.legionwarex.LegionWareX.LOGGER.info("LegionWareX trophies: {} of {} pieces in the game {}, effects {}", here.size(), SAYS.size(), here, effects);
		java.util.Map<String, Integer> slots = CuriosReach.playerSlots();

		if (!slots.isEmpty()) {
			com.freshorangeslices.legionwarex.LegionWareX.LOGGER.info("LegionWareX accessories: player slots {}", slots);
		}
	}
}
