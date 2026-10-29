package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.CompanionEvents;
import com.freshorangeslices.legionwarex.companion.Companions;
import com.freshorangeslices.legionwarex.companion.Keepsakes;
import com.freshorangeslices.legionwarex.companion.PendingKeepsakes;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** The add-on with Migamigos loaded (the dev runs carry it): keepsakes, bringing a companion back, wild despawning. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeepsakeGameTests {
	private static final ResourceLocation CLAYMORE = new ResourceLocation("migamigos", "claymore");

	@SuppressWarnings("unchecked")
	private static Mob amigo(GameTestHelper helper, BlockPos pos, UUID owner, int level) {
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CLAYMORE), "Migamigos is loaded in the dev run");
		Mob mob = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(CLAYMORE), pos);
		CompoundTag data = new CompoundTag();
		mob.saveWithoutId(data);

		if (owner != null) {
			data.putUUID("FollowingPlayerUUID", owner);
		}

		data.putInt("AmigoLevel", level);
		data.putFloat("AmigoExtraHealth", 4.0F);
		// A diamond in its backpack: dropped when it dies, so not in the keepsake.
		ListTag items = new ListTag();
		CompoundTag diamond = new ItemStack(Items.DIAMOND).save(new CompoundTag());
		diamond.putByte("Slot", (byte) 0);
		items.add(diamond);
		data.put("Items", items);
		mob.load(data);
		return mob;
	}

	@GameTest(template = "empty")
	public static void aFallenCompanionLeavesItsOwnerAKeepsake(GameTestHelper helper) {
		UUID owner = UUID.randomUUID();
		Mob amigo = amigo(helper, new BlockPos(1, 2, 1), owner, 7);
		amigo.setCustomName(Component.literal("Clay"));
		helper.assertTrue(Companions.isCompanion(amigo) && owner.equals(Companions.owner(amigo)), "The claymore is a companion with its owner");

		amigo.kill();

		// The owner isn't online (a test has no players), so the keepsake waits for them.
		PendingKeepsakes pending = PendingKeepsakes.get(helper.getLevel().getServer());
		List<ItemStack> waiting = pending.peek(owner);
		helper.assertTrue(waiting.size() == 1, "One keepsake waits for the owner, not " + waiting.size());
		ItemStack keepsake = waiting.get(0);
		CompoundTag kept = keepsake.getOrCreateTag().getCompound(Keepsakes.COMPANION);
		helper.assertTrue(kept.getInt("AmigoLevel") == 7, "The keepsake keeps its level: " + kept);
		helper.assertTrue(owner.equals(kept.getUUID("FollowingPlayerUUID")), "The keepsake keeps its owner");
		helper.assertFalse(kept.contains("Items") || kept.contains("Health") || kept.contains("UUID") || kept.contains("Pos"),
				"The keepsake leaves out the backpack, health, UUID and place: " + kept.getAllKeys());
		helper.assertTrue(Keepsakes.name(keepsake).getString().equals("Clay"), "The keepsake is named for it: " + Keepsakes.name(keepsake).getString());
		helper.assertTrue(Keepsakes.ticksLeft(helper.getLevel(), keepsake) > 20000, "It rests about a day first");
		helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(1, 2, 1), 3.0);

		pending.take(owner);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aKeepsakeRestsThenBringsTheSameCompanionBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID owner = UUID.randomUUID();
		Mob amigo = amigo(helper, new BlockPos(1, 2, 1), owner, 5);
		amigo.setCustomName(Component.literal("Clay"));
		ItemStack keepsake = Keepsakes.of(amigo, owner, level.getGameTime() + 100);
		amigo.discard();
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));

		helper.assertTrue(Keepsakes.revive(level, at, 0, keepsake) == Keepsakes.Revival.RESTING, "Not before it has rested");
		helper.assertTrue(Keepsakes.revive(level, at, 0, new ItemStack(LegionWareX.COMPANION_KEEPSAKE.get())) == Keepsakes.Revival.EMPTY,
				"An empty keepsake brings no one");

		keepsake.getOrCreateTag().putLong(Keepsakes.READY_AT, level.getGameTime());
		helper.assertTrue(Keepsakes.revive(level, at, 0, keepsake) == Keepsakes.Revival.REVIVED, "Back once it has rested");

		List<Entity> back = level.getEntities((Entity) null, new AABB(at, at).inflate(2), Companions::isCompanion);
		helper.assertTrue(back.size() == 1, "One companion is back: " + back);
		LivingEntity revived = (LivingEntity) back.get(0);
		CompoundTag data = new CompoundTag();
		revived.saveWithoutId(data);
		helper.assertTrue(data.getInt("AmigoLevel") == 5, "Its level came back: " + data.getInt("AmigoLevel"));
		helper.assertTrue(owner.equals(Companions.owner(revived)), "It's still the owner's");
		helper.assertTrue("Clay".equals(revived.getName().getString()), "Its name came back");
		helper.assertTrue(revived.getHealth() == revived.getMaxHealth(), "At full health: " + revived.getHealth() + " of " + revived.getMaxHealth());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void onlyWildCompanionsDespawn(GameTestHelper helper) {
		Mob wild = amigo(helper, new BlockPos(1, 2, 1), null, 1);
		Mob befriended = amigo(helper, new BlockPos(3, 2, 1), UUID.randomUUID(), 1);
		Mob named = amigo(helper, new BlockPos(1, 2, 3), null, 1);
		named.setCustomName(Component.literal("Tagged"));

		helper.assertTrue(Companions.isWild(wild), "A companion no one befriended is wild");
		helper.assertFalse(Companions.isWild(befriended), "A befriended one never despawns, wherever its owner is");
		helper.assertFalse(Companions.isWild(named), "A name-tagged one is kept");
		helper.assertFalse(Companions.isWild(helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3))), "A pig isn't a companion");

		RandomSource random = RandomSource.create(1);
		helper.assertTrue(CompanionEvents.despawns(129.0 * 129.0, 0, random), "Gone past 128 blocks");
		helper.assertFalse(CompanionEvents.despawns(20.0 * 20.0, 100000, random), "Never within 32");

		boolean anyNear = false;
		for (int i = 0; i < 400; i++) {
			anyNear |= CompanionEvents.despawns(40.0 * 40.0, 700, random);
			helper.assertFalse(CompanionEvents.despawns(40.0 * 40.0, 100, random), "Not before 30 seconds away from players");
		}
		helper.assertTrue(anyNear, "Now and then between 32 and 128");
		helper.succeed();
	}
}
