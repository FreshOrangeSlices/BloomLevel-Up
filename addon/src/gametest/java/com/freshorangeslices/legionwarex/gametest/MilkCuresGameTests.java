package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Milk takes Bad Omen off (world/MilkCures), even where milk is no longer its cure or a mod refuses the removal. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MilkCuresGameTests {
	/** Entities whose effect removals are refused here, as a mod in the pack might refuse Bad Omen's. */
	private static final Set<UUID> REFUSING = ConcurrentHashMap.newKeySet();
	private static boolean listening;

	@GameTest(template = "empty")
	public static void milkTakesBadOmenOff(GameTestHelper helper) {
		Pig plain = pig(helper, 1);
		plain.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 6000));
		drink(plain, Items.MILK_BUCKET);
		helper.assertFalse(plain.hasEffect(MobEffects.BAD_OMEN), "Plain milk takes Bad Omen off");

		// A mod took milk off Bad Omen's cures: the bucket alone leaves it, the add-on takes it off.
		Pig unlisted = pig(helper, 3);
		MobEffectInstance omen = new MobEffectInstance(MobEffects.BAD_OMEN, 6000);
		omen.setCurativeItems(new ArrayList<>());
		unlisted.addEffect(omen);
		new ItemStack(Items.MILK_BUCKET).finishUsingItem(helper.getLevel(), unlisted);
		helper.assertTrue(unlisted.hasEffect(MobEffects.BAD_OMEN), "Without milk as its cure, the bucket alone leaves Bad Omen");
		drink(unlisted, Items.MILK_BUCKET);
		helper.assertFalse(unlisted.hasEffect(MobEffects.BAD_OMEN), "The add-on takes it off when milk is no longer its cure");

		// A mod refuses the removal: taken off directly.
		Pig refused = pig(helper, 5);
		refused.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 6000));
		refuseRemovals(refused);
		helper.assertFalse(refused.removeEffect(MobEffects.BAD_OMEN), "The removal is refused here");
		drink(refused, Items.MILK_BUCKET);
		REFUSING.remove(refused.getUUID());
		helper.assertFalse(refused.hasEffect(MobEffects.BAD_OMEN), "The add-on takes it off past a mod that refuses the removal");

		// Only milk, and only Bad Omen.
		Pig other = pig(helper, 7);
		other.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 6000));
		drink(other, Items.HONEY_BOTTLE);
		helper.assertTrue(other.hasEffect(MobEffects.BAD_OMEN), "Honey doesn't take Bad Omen off");
		MobEffectInstance speed = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000);
		speed.setCurativeItems(new ArrayList<>());
		other.addEffect(speed);
		drink(other, Items.MILK_BUCKET);
		helper.assertFalse(other.hasEffect(MobEffects.BAD_OMEN), "Milk takes Bad Omen off");
		helper.assertTrue(other.hasEffect(MobEffects.MOVEMENT_SPEED), "An effect milk doesn't cure is left alone");
		helper.succeed();
	}

	private static Pig pig(GameTestHelper helper, int x) {
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(x, 2, 1));
		pig.setNoAi(true);
		return pig;
	}

	/** As the game finishes drinking: the item's own use, then Forge's finish event. */
	private static void drink(LivingEntity entity, net.minecraft.world.item.Item item) {
		ItemStack stack = new ItemStack(item);
		ItemStack drunk = stack.copy();
		ForgeEventFactory.onItemUseFinish(entity, drunk, 0, stack.finishUsingItem(entity.level(), entity));
	}

	private static void refuseRemovals(LivingEntity entity) {
		if (!listening) {
			listening = true;
			MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, MobEffectEvent.Remove.class, event -> {
				if (REFUSING.contains(event.getEntity().getUUID())) {
					event.setCanceled(true);
				}
			});
		}

		REFUSING.add(entity.getUUID());
	}
}
