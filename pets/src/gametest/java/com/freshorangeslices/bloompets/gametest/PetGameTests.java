package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Bonding;
import com.freshorangeslices.bloompets.Calling;
import com.freshorangeslices.bloompets.Party;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.PetsConfig;
import com.freshorangeslices.bloompets.Species;
import com.freshorangeslices.bloompets.SpeciesData;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 1: the roster, bonding, the pet list, the party, calling, storage and owners' own hits. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static Cow cow(GameTestHelper helper, BlockPos at) {
		Cow cow = helper.spawn(EntityType.COW, at);
		cow.setNoAi(true);
		return cow;
	}

	/** A cow bonded to the player, as five feedings of wheat would. */
	private static Cow pet(GameTestHelper helper, ServerPlayer player, BlockPos at) {
		Cow cow = cow(helper, at);
		ItemStack wheat = new ItemStack(Items.WHEAT, 5);

		for (int i = 0; i < 5; i++) {
			Bonding.feed(player, cow, wheat);
		}

		return cow;
	}

	private static PetRegistry.Record record(GameTestHelper helper, UUID pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet);
	}

	@GameTest(template = "empty")
	public static void theRosterIsLoaded(GameTestHelper helper) {
		helper.assertTrue(SpeciesData.all().size() == 25, "25 species in the roster, not " + SpeciesData.all().size());
		Species wolf = SpeciesData.of(EntityType.WOLF);
		helper.assertTrue(wolf != null && wolf.combat() && wolf.storage() == 18 && wolf.isFood(new ItemStack(Items.COOKED_BEEF)),
				"The wolf: combat, medium storage, eats meat");
		helper.assertTrue(SpeciesData.of(EntityType.SNIFFER).feeds() == 6, "A sniffer takes 6 feedings");
		helper.assertTrue(SpeciesData.of(EntityType.BEE).isFood(new ItemStack(Items.POPPY)), "A bee bonds with any flower (a tag)");
		helper.assertTrue(SpeciesData.of(EntityType.ZOMBIE) == null && SpeciesData.of(EntityType.VEX) == null,
				"No zombies, and no Vex until its gear comes");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aCowBondsAfterFiveFeedingsOfWheat(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 1, 4));
		Cow cow = cow(helper, new BlockPos(1, 2, 1));
		ItemStack wheat = new ItemStack(Items.WHEAT, 10);
		helper.assertTrue(Bonding.feed(player, cow, new ItemStack(Items.CARROT)) == Bonding.Result.NOT_FOOD, "A carrot isn't a cow's food");

		for (int i = 0; i < 4; i++) {
			helper.assertTrue(Bonding.feed(player, cow, wheat) == Bonding.Result.FED, "Feeding " + (i + 1) + " counts");
		}

		helper.assertTrue(Pets.owner(cow) == null, "Not bonded after four");
		helper.assertTrue(Bonding.feed(player, cow, wheat) == Bonding.Result.BONDED, "The fifth bonds it");
		helper.assertTrue(player.getUUID().equals(Pets.owner(cow)), "It's the player's");
		helper.assertTrue(wheat.getCount() == 5, "Five wheat used, not " + (10 - wheat.getCount()));
		helper.assertTrue(cow.isPersistenceRequired() && cow.hasCustomName(), "Kept for good, and named");
		PetRegistry.Record record = record(helper, cow.getUUID());
		helper.assertTrue(record != null && record.kind == Pets.Kind.BLOOM && record.slots == 18, "On the player's list, with 18 storage slots");
		ServerPlayer other = player(helper, new BlockPos(5, 1, 5));
		helper.assertTrue(Bonding.feed(other, cow, wheat) == Bonding.Result.OWNED_BY_SOMEONE, "Someone else can't bond it now");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aWolfTamedTheVanillaWayIsListed(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 2, 1));
		wolf.setNoAi(true);
		helper.assertFalse(Pets.listed(wolf), "A wild wolf isn't anyone's pet");
		wolf.setTame(true);
		wolf.setOwnerUUID(player.getUUID());
		PetRegistry.Record record = PetEvents.observe(wolf);
		helper.assertTrue(record != null && record.kind == Pets.Kind.BLOOM && player.getUUID().equals(PetRegistry.get(helper.getLevel().getServer()).ownerOf(wolf.getUUID())),
				"A wolf tamed with bones is the owner's Bloom pet");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thePartyHasOneBloomPetAndACooldown(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 1, 4));
		Cow first = pet(helper, player, new BlockPos(1, 2, 1));
		Cow second = pet(helper, player, new BlockPos(6, 2, 1));
		PetRegistry registry = PetRegistry.get(helper.getLevel().getServer());
		first.setHealth(2.0F);

		Party.toggle(player, record(helper, first.getUUID()));
		helper.assertTrue(record(helper, first.getUUID()).party && Pets.inParty(first), "The first cow joined the party");
		helper.assertTrue(first.getHealth() == first.getMaxHealth(), "Joining filled its health");

		Party.toggle(player, record(helper, second.getUUID()));
		helper.assertFalse(record(helper, second.getUUID()).party, "Not again before the cooldown is over");

		registry.setPartyChanged(player.getUUID(), helper.getLevel().getServer().overworld().getGameTime() - PetsConfig.partyCooldownTicks() - 1);
		Party.toggle(player, record(helper, second.getUUID()));
		helper.assertTrue(record(helper, second.getUUID()).party && Pets.inParty(second), "After the cooldown the second joins");
		helper.assertFalse(record(helper, first.getUUID()).party || Pets.inParty(first), "and the first left: one Bloom pet at a time");
		helper.assertTrue(Pets.home(first) != null, "The one that left stays near where it was");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anOwnersHitsNeverLandOnTheirPet(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		ServerPlayer stranger = player(helper, new BlockPos(5, 1, 5));
		Cow cow = pet(helper, owner, new BlockPos(1, 2, 1));
		float full = cow.getHealth();
		helper.assertFalse(cow.hurt(helper.getLevel().damageSources().playerAttack(owner), 4.0F), "The owner's hit doesn't land");
		helper.assertTrue(cow.getHealth() == full, "Not hurt by its owner");
		helper.assertTrue(cow.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0F), "A stranger's does");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void callingBringsAPetToItsOwner(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		Cow cow = pet(helper, owner, new BlockPos(1, 2, 1));
		Calling.call(owner, record(helper, cow.getUUID()));
		helper.assertTrue(cow.distanceToSqr(owner) < 3.0 * 3.0, "The cow is beside its owner, " + String.format("%.1f", Math.sqrt(cow.distanceToSqr(owner))) + " blocks off");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPetThatDiesLeavesItsStorageAndTheList(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow cow = pet(helper, owner, new BlockPos(2, 2, 2));
		PetRegistry.Record record = record(helper, cow.getUUID());
		SimpleContainer box = new SimpleContainer(9);
		box.addItem(new ItemStack(Items.DIAMOND));
		record.items = box.createTag();
		cow.hurt(helper.getLevel().damageSources().generic(), 100.0F);
		helper.assertTrue(cow.isDeadOrDying(), "The cow died");
		helper.assertTrue(record(helper, cow.getUUID()) == null, "It's off its owner's list");
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(cow.blockPosition()).inflate(3.0));
		helper.assertTrue(drops.stream().anyMatch(i -> i.getItem().is(Items.DIAMOND)), "What it carried is where it fell");
		helper.assertTrue(record.items.isEmpty() || record.items.equals(new ListTag()), "and its storage is empty");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPetsEntryKeepsItsLookAndHealthForTheScreen(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow cow = pet(helper, owner, new BlockPos(2, 2, 2));
		cow.setHealth(6.0F);
		PetRegistry.Record record = PetEvents.observe(cow);
		helper.assertTrue(record.health == 6.0F && record.maxHealth == cow.getMaxHealth(), "Its health, for when it's away");
		helper.assertFalse(record.look.isEmpty(), "Its look, to draw it in the screen when it's away");
		helper.assertFalse(record.look.contains("Attributes") || record.look.contains("Brain") || record.look.contains("ForgeData"),
				"without what doesn't show (attributes, memories, other mods' data)");
		helper.succeed();
	}
}
