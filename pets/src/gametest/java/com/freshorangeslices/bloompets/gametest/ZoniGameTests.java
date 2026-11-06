package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetGoals;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetModes;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetSkills;
import com.freshorangeslices.bloompets.PetStorage;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.Release;
import com.freshorangeslices.bloompets.Spirits;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bloom Pets with a real Zoni's Companions companion (Bocchi; Zoni's Companions is in this test run only), after
 * FreshOrangeSlices' playtest (2026-11-05): it stays put when told to, its food pouch feeds it, a second wind keeps
 * its gear on (Zoni's would drop it as it dies), a fallen one's level and Spirit come back with the next of its kind,
 * and a released one leaves the list for good.
 */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoniGameTests {
	private static final String ZONI = "zonis_companions";

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<Mob> bocchiType(GameTestHelper helper) {
		for (ResourceLocation id : ForgeRegistries.ENTITY_TYPES.getKeys()) {
			if (ZONI.equals(id.getNamespace()) && id.getPath().contains("bocchi")) {
				return (EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(id);
			}
		}

		String theirs = ForgeRegistries.ENTITY_TYPES.getKeys().stream().filter(id -> id.getNamespace().startsWith("zoni"))
				.map(ResourceLocation::toString).collect(Collectors.joining(", "));
		helper.fail("Zoni's Companions' Bocchi is in the test run (its entities: " + theirs + ")");
		return null;
	}

	/** A Bocchi tamed by the owner and on their list, as Zoni's Companions' own taming leaves it. */
	private static Mob bocchi(GameTestHelper helper, ServerPlayer owner, boolean ai) {
		Mob mob = helper.spawn(bocchiType(helper), new BlockPos(2, 2, 2));
		mob.setNoAi(!ai);
		helper.assertTrue(mob instanceof TamableAnimal, "Bocchi is a tamable animal");
		TamableAnimal tamable = (TamableAnimal) mob;
		tamable.setTame(true);
		tamable.setOwnerUUID(owner.getUUID());
		PetEvents.observe(mob);
		PetGoals.ensure(mob);
		helper.assertTrue(Pets.kind(mob) == Pets.Kind.MODDED && Pets.listed(mob), "Bocchi is a listed companion");
		return mob;
	}

	private static PetRegistry.Record record(GameTestHelper helper, Mob pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet.getUUID());
	}

	@GameTest(template = "empty", timeoutTicks = 240)
	public static void aCompanionOnStayHoldsItsSpot(GameTestHelper helper) {
		Mob mob = bocchi(helper, player(helper), true);
		helper.assertTrue(PetModes.mode(mob) == PetModes.Mode.FOLLOW, "A tame companion follows to begin with (" + PetModes.mode(mob) + ")");
		helper.assertTrue(!PetGoals.sits(mob), "Bocchi doesn't sit");

		helper.runAfterDelay(20, () -> {
			PetModes.set(mob, PetModes.Mode.STAY);
			helper.assertTrue(((TamableAnimal) mob).isOrderedToSit() && !((TamableAnimal) mob).isInSittingPose(),
					"Held (its own follow lets go) but not sitting");
			BlockPos home = Pets.home(mob);
			BlockPos away = helper.absolutePos(new BlockPos(6, 2, 6));
			mob.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.succeedWhen(() -> helper.assertTrue(mob.blockPosition().distSqr(home) <= 2.0,
					"Bocchi walks back to its spot (" + mob.blockPosition() + ", its spot " + home + ")"));
		});
	}

	@GameTest(template = "empty")
	public static void aCompanionEatsFromItsFoodPouch(GameTestHelper helper) {
		Mob mob = bocchi(helper, player(helper), false);
		PetRegistry.Record record = record(helper, mob);
		helper.assertTrue(record.slots == PetStorage.POUCH, "No backpack of its own: a food pouch of " + PetStorage.POUCH + " (" + record.slots + ")");
		SimpleContainer pouch = new SimpleContainer(PetStorage.POUCH);
		pouch.setItem(0, new ItemStack(Items.BEEF, 2));
		pouch.setItem(1, new ItemStack(Items.COOKED_BEEF, 2));
		record.items = pouch.createTag();

		PetMood.setHunger(mob, 14.0);
		PetMood.update(mob, 1, true);
		SimpleContainer after = new SimpleContainer(PetStorage.POUCH);
		after.fromTag(record.items);
		helper.assertTrue(after.countItem(Items.COOKED_BEEF) == 1 && after.countItem(Items.BEEF) == 2,
				"Hungry and out with you: it eats the cooked beef, not the raw");
		helper.assertTrue(PetMood.hunger(mob) > 19.9, "A meal: +6 (" + PetMood.hunger(mob) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSecondWindKeepsItsGearOn(GameTestHelper helper) {
		Mob mob = bocchi(helper, player(helper), false);
		PetLevels.setLevel(mob, PetLevels.MAX);
		mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		mob.hurt(mob.damageSources().generic(), 1000.0F);
		helper.assertTrue(mob.isAlive() && Math.abs(mob.getHealth() - mob.getMaxHealth() * 0.5F) < 0.01F,
				"A second wind as the blow lands: half its health (" + mob.getHealth() + ")");
		helper.assertTrue(mob.getMainHandItem().is(Items.IRON_SWORD), "It never died, so nothing was dropped: it still holds its sword");
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8)));
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).isEmpty(), "and no gear on the ground");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFallenCompanionComesBack(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Mob first = bocchi(helper, owner, false);
		PetLevels.setLevel(first, 30);
		Spirits.set(first, PetSkills.Kind.WOLF);
		Spirits.keep(owner, record(helper, first));
		first.setCustomName(Component.literal("Sir Bocchi"));
		PetEvents.observe(first);
		first.hurt(first.damageSources().generic(), 10000.0F);
		helper.assertTrue(!first.isAlive() && record(helper, first) == null, "It died, and left its owner's list");

		Mob second = bocchi(helper, owner, false);
		helper.assertTrue(PetLevels.level(second) == 30, "The next Bocchi picks up its level (" + PetLevels.level(second) + ")");
		helper.assertTrue(Spirits.of(second) == PetSkills.Kind.WOLF && Spirits.settled(second), "its Spirit, kept (" + Spirits.of(second) + ")");
		helper.assertTrue("Sir Bocchi".equals(second.getName().getString()), "and its name (" + second.getName().getString() + ")");

		Mob third = bocchi(helper, owner, false);
		helper.assertTrue(PetLevels.level(third) == 1, "Only one comes back for each that fell (" + PetLevels.level(third) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aReleasedCompanionGoesBackIntoItsDevice(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Mob mob = bocchi(helper, owner, false);
		PetRegistry.Record record = record(helper, mob);
		net.minecraft.world.item.Item summon = Release.summonItem(record.type);
		ResourceLocation emptyId = new ResourceLocation(ZONI, "empty_summon_item");
		String items = ForgeRegistries.ITEMS.getKeys().stream().filter(id -> id.getNamespace().equals(ZONI))
				.map(ResourceLocation::getPath).collect(Collectors.joining(", "));
		helper.assertTrue(summon != null && ForgeRegistries.ITEMS.containsKey(emptyId), "Bocchi's summon item and the empty one are found (" + items + ")");
		owner.getInventory().add(new ItemStack(ForgeRegistries.ITEMS.getValue(emptyId)));
		mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		SimpleContainer pouch = new SimpleContainer(PetStorage.POUCH);
		pouch.setItem(0, new ItemStack(Items.BREAD, 4));
		record.items = pouch.createTag();

		Release.release(owner, record);
		helper.assertTrue(mob.isRemoved() && record(helper, mob) == null, "Back into its device: gone, and off the list");
		helper.assertTrue(owner.getInventory().countItem(summon) == 1 && owner.getInventory().countItem(ForgeRegistries.ITEMS.getValue(emptyId)) == 0,
				"Its summon item is its owner's, in place of the empty one");
		helper.assertTrue(owner.getInventory().countItem(Items.BREAD) == 4, "and its pouch's food");
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8)));
		var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
		helper.assertTrue(dropped.stream().anyMatch(i -> i.getItem().is(Items.IRON_HELMET)) && dropped.stream().anyMatch(i -> i.getItem().is(Items.IRON_SWORD)),
				"Its gear dropped where it stood");
		helper.succeed();
	}
}
