package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.BloomEffects;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.ProfileCodecs;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.BloomDamage;

/**
 * The Forge build's first in-game checks: a real dedicated server boots with the mod, then these run on it. They
 * cover what this chunk ported (data, profile capability, stats to attributes, commands, registries).
 */
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmokeGameTests {
	private static final String EMPTY = "empty";

	private SmokeGameTests() {
	}

	private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), "bloom_" + name));
	}

	/** The datapack's four categories and their Majors are loaded and valid. */
	@GameTest(template = EMPTY)
	public static void definitionsLoad(GameTestHelper helper) {
		int categories = BloomServices.definitions().categories().size();
		int majors = BloomServices.definitions().majors().size();
		helper.assertTrue(categories == 4, "expected 4 Bloom categories, found " + categories);
		helper.assertTrue(majors > 50, "expected the Majors to load, found " + majors);
		helper.assertTrue(BloomServices.definitions().warnings().isEmpty(), "definition warnings: " + BloomServices.definitions().warnings());
		helper.succeed();
	}

	/** Bleed, the damage types and the /bloom command are registered. */
	@GameTest(template = EMPTY)
	public static void registered(GameTestHelper helper) {
		helper.assertTrue(ForgeRegistries.MOB_EFFECTS.getKey(BloomEffects.BLEED.get()) != null, "Bleed isn't registered");
		BloomDamage.bleed(helper.getLevel());
		BloomDamage.overkill(helper.getLevel(), fakePlayer(helper, "damage"));
		helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("bloom") != null, "/bloom is missing");
		helper.succeed();
	}

	/** Every player carries a profile, and it survives a save and load (the capability's NBT). */
	@GameTest(template = EMPTY)
	public static void profileSaves(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "profile");
		BloomProfile profile = BloomAttachments.profile(player);
		BloomServices.progression().addXp(profile, SkillXp.UTILITY, 500.0);
		int level = profile.category(SkillXp.UTILITY).level();
		helper.assertTrue(level > 0, "XP didn't level Utility up");

		Tag saved = ProfileCodecs.PROFILE.encodeStart(NbtOps.INSTANCE, profile).getOrThrow(false, helper::fail);
		BloomProfile loaded = ProfileCodecs.PROFILE.parse(NbtOps.INSTANCE, saved).getOrThrow(false, helper::fail);
		helper.assertTrue(loaded.category(SkillXp.UTILITY).level() == level, "level lost in a save");

		CompoundTag caps = new CompoundTag();
		player.saveWithoutId(caps);
		helper.assertTrue(caps.getCompound("ForgeCaps").contains(BloomLevelUp.id("profile").toString()), "profile not saved with the player");
		helper.succeed();
	}

	/** Vitality points turn into extra hearts, through the same rules the hub uses. */
	@GameTest(template = EMPTY)
	public static void vitalityGivesHealth(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "vitality");
		BloomProfile profile = BloomAttachments.profile(player);
		ProgressionService service = BloomServices.progression();
		double before = player.getAttributeValue(Attributes.MAX_HEALTH);

		service.addXp(profile, SkillXp.ARMOR, 100_000.0);
		helper.assertTrue(service.allocate(profile, StatEffects.VITALITY, ProgressionService.Pool.NATURAL, 10).ok(), "couldn't put points in Vitality");
		StatEffects.refresh(player);

		double after = player.getAttributeValue(Attributes.MAX_HEALTH);
		helper.assertTrue(Math.abs(after - before - 2.0) < 1.0E-6, "10 Vitality should add 2 health: " + before + " -> " + after);

		// No points (a Bloom+ reset, say) takes the hearts away again.
		profile.category(SkillXp.ARMOR).natural().remove(StatEffects.VITALITY);
		StatEffects.refresh(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MAX_HEALTH) - before) < 1.0E-6, "Vitality modifier left behind");
		helper.succeed();
	}
}
