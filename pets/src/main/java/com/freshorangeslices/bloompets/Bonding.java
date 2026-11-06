package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bonding, the plugin's way: sneak and right-click a wild animal of the roster with its favourite food. Each feeding
 * counts (hearts above the hotbar show how far along); after enough (5 for most) it's the player's pet. Feeding without
 * sneaking still breeds animals as usual. Sneak-feeding your own pet feeds it (health, hunger, mood, and XP for a
 * non-combat party pet); sneak + right-click with an empty hand pats it. Your companions (other mods') the same way:
 * cooked vanilla food ({@code #bloompets:companion_food}) feeds one, an empty hand pats it; their own mod's right-click
 * (Migamigos' backpack) is left to a plain click.
 */
public final class Bonding {
	/** What a companion eats. */
	public static final TagKey<Item> COMPANION_FOOD = TagKey.create(Registries.ITEM, BloomPets.id("companion_food"));

	private Bonding() {
	}

	public enum Result {
		/** Not its food; yours already, and it didn't need feeding; someone else's; one feeding toward bonding; bonded. */
		NOT_FOOD, OWNED_BY_YOU, OWNED_BY_SOMEONE, FED, BONDED,
		/** Your own pet, fed: it healed (and a non-combat party pet earned XP). */
		FED_OWN
	}

	@SubscribeEvent
	public static void onInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();

		if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
				|| !(event.getTarget() instanceof Mob mob) || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		if (Pets.kind(mob) == Pets.Kind.MODDED) {
			companion(event, serverPlayer, mob);
			return;
		}

		Species species = Pets.species(mob);

		if (species == null) {
			return;
		}

		// An empty hand: its owner pats it (horses, donkeys, llamas and camels open their inventory that way instead).
		if (event.getItemStack().isEmpty()) {
			if (player.getUUID().equals(Pets.owner(mob)) && !(mob instanceof AbstractHorse)) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				PetMood.pat(serverPlayer, mob);
			}

			return;
		}

		if (!species.isFood(event.getItemStack())) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		feed(serverPlayer, mob, event.getItemStack());
	}

	/** Sneak + right-click on one of your companions: a pat with an empty hand, a meal with cooked food. */
	private static void companion(PlayerInteractEvent.EntityInteract event, ServerPlayer player, Mob mob) {
		ItemStack held = event.getItemStack();

		if (!player.getUUID().equals(Pets.owner(mob)) || !held.isEmpty() && !held.is(COMPANION_FOOD)) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (held.isEmpty()) {
			PetMood.pat(player, mob);
		} else {
			feedCompanion(player, mob, held);
		}
	}

	/** Feeds one of the player's companions (cooked vanilla food). Public for the game tests. */
	public static Result feedCompanion(ServerPlayer player, Mob mob, ItemStack food) {
		if (!food.is(COMPANION_FOOD)) {
			return Result.NOT_FOOD;
		}

		return player.getUUID().equals(Pets.owner(mob)) ? PetXp.feed(player, mob, food) : Result.OWNED_BY_SOMEONE;
	}

	/** One feeding toward bonding (the food is used up unless the player is in creative). Public for the game tests. */
	public static Result feed(ServerPlayer player, Mob mob, ItemStack food) {
		Species species = Pets.species(mob);

		if (species == null || !species.isFood(food)) {
			return Result.NOT_FOOD;
		}

		UUID owner = Pets.owner(mob);

		if (owner != null && owner.equals(player.getUUID())) {
			return PetXp.feed(player, mob, food);
		}

		if (owner != null) {
			player.displayClientMessage(Component.translatable("bloompets.bond.taken", mob.getName()), true);
			return Result.OWNED_BY_SOMEONE;
		}

		if (!player.getAbilities().instabuild) {
			food.shrink(1);
		}

		int fed = Pets.bondProgress(mob, player.getUUID()) + 1;
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() * 0.8, mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);

		if (fed < species.feeds()) {
			Pets.setBondProgress(mob, player.getUUID(), fed);
			player.displayClientMessage(Component.literal("♥".repeat(fed)).withStyle(ChatFormatting.RED)
					.append(Component.literal("♡".repeat(species.feeds() - fed)).withStyle(ChatFormatting.GRAY)), true);
			return Result.FED;
		}

		Pets.claim(mob, player);
		PetRegistry.get(player.server).upsert(player.getUUID(), mob);
		level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 10, 0.5, 0.4, 0.5, 0.0);
		player.sendSystemMessage(Component.translatable("bloompets.bond.done", mob.getName(), species.displayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		return Result.BONDED;
	}
}
