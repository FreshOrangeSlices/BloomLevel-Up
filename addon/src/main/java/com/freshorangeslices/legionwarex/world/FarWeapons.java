package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.common.base.Suppliers;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Weapons found far out hit harder (FreshOrangeSlices, 2026-11-25: "is there a mod that scales weapons dmg from
 * chest/drops with the same distance situation? so let the player try other weapons"; no Forge 1.20.1 mod did, so the
 * add-on does). A weapon from a chest (or brushed from suspicious sand) or a mob's drops is marked with how far from spawn
 * it was found ({@link #FOUND}), and gets +{@code farWeaponPerStep} (10%) of its own damage per step past 1000 blocks,
 * up to +50% at 6000; a staff, wand or other spell weapon gets half that as Spell Power instead. Never wooden, stone or
 * golden weapons, nor {@code #legionwarex:rare_mob_gear} (Epic Knights' rust series); crafted ones aren't marked.
 * <p>
 * Boss and legendary weapons ("is it possible we limit what can get those effects and possibly scale the player's latest
 * boss kill distance ... for specifically boss weapons/legendary weapons"): {@code #legionwarex:boss_weapons}, and any
 * one-of-a-kind weapon (not a material's version) hitting {@code legendaryDamage} (9) or more. They grow the same way,
 * but by their holder's farthest boss kill ({@link #FARTHEST}, kept with the player; a boss in Bloom's bosses tag,
 * killed with a player's hand in it, counts for every player within 48 blocks), however they were got; the add-on
 * writes the holder's number on the weapon ({@link #BOSS}) so its tooltip and damage agree for everyone.
 */
public final class FarWeapons {
	/** On a found weapon: blocks from spawn where it was found. */
	public static final String FOUND = "legionwarex_found_far";
	/** On a boss or legendary weapon: its holder's farthest boss kill, in blocks. */
	public static final String BOSS = "legionwarex_boss_far";
	/** In a player's kept data: their farthest boss kill, in blocks from spawn. */
	public static final String FARTHEST = "legionwarex.farthestBossKill";
	public static final TagKey<Item> BOSS_WEAPONS = ItemTags.create(new ResourceLocation(LegionWareX.MOD_ID, "boss_weapons"));
	public static final TagKey<Item> RARE = ItemTags.create(new ResourceLocation(LegionWareX.MOD_ID, "rare_mob_gear"));
	static final UUID SPELL_ID = UUID.fromString("a4d27f19-5c3e-4b8a-8e61-0f9b3c2d7e54");
	static final int BOSS_PARTY_RANGE = 48;
	/** The pack test's gear report uses the same: a material's version of a weapon starts (or ends) with its material. */
	static final Pattern MATERIAL = Pattern.compile("(^|_)(wood(en)?|stone|iron|gold(en)?|diamond|netherite|copper|bronze|steel|silver|tin|lead|leather|"
			+ "chainmail|flint|bone|obsidian|emerald|amethyst|quartz|platinum)(_|$)");
	private static final Map<Item, Kind> KINDS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> ASKING = ThreadLocal.withInitial(() -> false);
	/** For the game tests: items to count as legendary whatever they are (null: the tag and the rule). */
	public static Set<Item> testLegendary;

	/** What a weapon is to these rules. */
	public record Kind(boolean weapon, boolean spell, boolean legendary) {
		static final Kind NONE = new Kind(false, false, false);

		public boolean any() {
			return weapon || spell;
		}
	}

	private FarWeapons() {
	}

	/** The bonus share at a distance: damage for a weapon, Spell Power for a spell weapon. */
	public static double bonus(double distance, boolean spell) {
		return FarFromSpawn.steps(distance) * AddonConfig.farWeaponPerStep() * (spell ? AddonConfig.farStaffShare() : 1.0);
	}

	static boolean spellAttribute(Attribute attribute) {
		ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
		return id != null && (id.getNamespace().equals("irons_spellbooks") || id.getNamespace().equals("spell_power"))
				&& !id.getPath().contains("resist");
	}

	static Attribute rangedDamage() {
		return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("ranged_weapon", "damage"));
	}

	/** What an item is, from a plain one's main-hand modifiers (cached; the add-on's own changes left out). */
	public static Kind kind(ItemStack stack) {
		Item item = stack.getItem();

		if (stack.isEmpty()) {
			return Kind.NONE;
		}

		Kind known = KINDS.get(item);

		if (known != null) {
			return known;
		}

		if (ASKING.get()) {
			return Kind.NONE;
		}

		ASKING.set(true);

		try {
			Kind kind;

			try {
				kind = work(item);
			} catch (RuntimeException e) {
				// An item whose own modifiers can't be read on a plain stack: not one of these.
				kind = Kind.NONE;
			}

			KINDS.put(item, kind);
			return kind;
		} finally {
			ASKING.set(false);
		}
	}

	private static Kind work(Item item) {
		ItemStack plain = new ItemStack(item);

		if (plain.is(RARE) || item instanceof TieredItem tiered && (tiered.getTier() == Tiers.WOOD || tiered.getTier() == Tiers.STONE || tiered.getTier() == Tiers.GOLD)) {
			return Kind.NONE;
		}

		Multimap<Attribute, AttributeModifier> modifiers = plain.getAttributeModifiers(EquipmentSlot.MAINHAND);
		boolean spell = modifiers.keySet().stream().anyMatch(FarWeapons::spellAttribute);
		double damage = 0.0;

		for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				damage += modifier.getAmount();
			}
		}

		Attribute ranged = rangedDamage();
		boolean shoots = ranged != null && !modifiers.get(ranged).isEmpty();
		boolean weaponish = item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
				|| plain.getTags().anyMatch(tag -> tag.location().getNamespace().equals("bloomlevelup") && tag.location().getPath().startsWith("weapon/")
						&& !tag.location().getPath().contains("/not_"));
		boolean weapon = !spell && ((weaponish && damage > 0.0) || shoots);
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
		boolean legendary = testLegendary != null ? testLegendary.contains(item)
				: plain.is(BOSS_WEAPONS) || (weapon && id != null && !MATERIAL.matcher(id.getPath()).find() && damage + 1.0 >= AddonConfig.legendaryDamage());

		if (plain.is(BOSS_WEAPONS) && !spell && !weapon) {
			weapon = damage > 0.0;
		}

		return weapon || spell ? new Kind(weapon, spell, legendary) : Kind.NONE;
	}

	/** Marks a found weapon with where it was found (once; legendary weapons and others aren't marked). */
	public static boolean mark(ItemStack stack, double distance) {
		Kind kind = kind(stack);

		if (!kind.any() || kind.legendary() || stack.hasTag() && stack.getTag().contains(FOUND)) {
			return false;
		}

		stack.getOrCreateTag().putInt(FOUND, (int) Math.round(distance));
		return true;
	}

	/** How far this stack's bonus reaches, in blocks, or -1 for none. */
	public static int reach(ItemStack stack, Kind kind) {
		CompoundTag tag = stack.getTag();

		if (tag == null) {
			return -1;
		}

		String key = kind.legendary() ? BOSS : FOUND;
		return tag.contains(key) ? tag.getInt(key) : -1;
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onModifiers(ItemAttributeModifierEvent event) {
		if (event.getSlotType() != EquipmentSlot.MAINHAND || ASKING.get() || !event.getItemStack().hasTag()) {
			return;
		}

		ItemStack stack = event.getItemStack();
		Kind kind = kind(stack);
		int reach = kind.any() ? reach(stack, kind) : -1;

		if (reach < 0) {
			return;
		}

		double bonus = bonus(reach, kind.spell());

		if (bonus <= 0.0) {
			return;
		}

		if (kind.spell()) {
			Attribute power = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", "spell_power"));

			if (power == null) {
				power = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("spell_power", "generic"));
			}

			if (power != null) {
				event.addModifier(power, new AttributeModifier(SPELL_ID, "LegionWareX found far", bonus, AttributeModifier.Operation.MULTIPLY_BASE));
			}

			return;
		}

		scale(event, Attributes.ATTACK_DAMAGE, bonus, true);
		Attribute ranged = rangedDamage();

		if (ranged != null) {
			scale(event, ranged, bonus, false);
		}
	}

	/**
	 * Scales a weapon's own additions so the total grows by the bonus: for attack damage the hand's 1 counts as the weapon's
	 * (the tooltip's number is 1 + the additions), so a 6 shown becomes 6 x (1 + bonus).
	 */
	private static void scale(ItemAttributeModifierEvent event, Attribute attribute, double bonus, boolean hand) {
		List<AttributeModifier> own = new ArrayList<>();
		double sum = 0.0;

		for (AttributeModifier modifier : event.getModifiers().get(attribute)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION && modifier.getAmount() > 0.0) {
				own.add(modifier);
				sum += modifier.getAmount();
			}
		}

		if (sum <= 0.0) {
			return;
		}

		double base = hand ? sum + 1.0 : sum;
		double factor = (base * (1.0 + bonus) - (hand ? 1.0 : 0.0)) / sum;

		for (AttributeModifier modifier : own) {
			event.removeModifier(attribute, modifier);
			event.addModifier(attribute, new AttributeModifier(modifier.getId(), modifier.getName(), modifier.getAmount() * factor, modifier.getOperation()));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		Kind kind = kind(stack);

		if (!kind.any()) {
			return;
		}

		int reach = reach(stack, kind);
		String what = kind.spell() ? "Spell Power" : "damage";

		if (kind.legendary()) {
			String now = reach <= 0 ? "no boss kill yet" : String.format("%,d blocks out: +%d%% %s", reach, Math.round(bonus(reach, kind.spell()) * 100), what);
			event.getToolTip().add(Component.literal("Legendary: grows with your farthest boss kill (" + now + ")").withStyle(ChatFormatting.DARK_PURPLE));
		} else if (reach >= 0) {
			event.getToolTip().add(Component.literal(String.format("Found %,d blocks from spawn: +%d%% %s", reach, Math.round(bonus(reach, kind.spell()) * 100), what))
					.withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	/** Chest and archaeology loot: marked where the chest is. Other loot (blocks, mobs, fishing) is left alone here. */
	public static final class Loot extends LootModifier {
		public static final Supplier<Codec<Loot>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(instance -> codecStart(instance)
				.apply(instance, Loot::new)));

		public Loot(LootItemCondition[] conditions) {
			super(conditions);
		}

		@Override
		protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
			ResourceLocation table = context.getQueriedLootTableId();
			Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
			boolean chest = table != null && (table.getPath().contains("chest") || table.getPath().startsWith("archaeology/"));

			if (chest && origin != null && !context.hasParam(LootContextParams.BLOCK_STATE) && !context.hasParam(LootContextParams.DAMAGE_SOURCE)) {
				double distance = FarFromSpawn.distance(context.getLevel(), origin.x, origin.z);

				for (ItemStack stack : loot) {
					mark(stack, distance);
				}
			}

			return loot;
		}

		@Override
		public Codec<? extends IGlobalLootModifier> codec() {
			return CODEC.get();
		}
	}

	/** A mob's drops: marked where it died, unless a player owns it or it picked up its gear (a player's, likely). */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity dead = event.getEntity();

		if (!(dead instanceof Mob mob) || !(dead.level() instanceof ServerLevel level) || FarHealth.owned(mob) || Despawning.pickedUpGear(mob)) {
			return;
		}

		double distance = FarFromSpawn.distance(level, dead.getX(), dead.getZ());

		for (ItemEntity drop : event.getDrops()) {
			mark(drop.getItem(), distance);
		}
	}

	public static int farthest(Player player) {
		return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(FARTHEST);
	}

	public static void setFarthest(Player player, int blocks) {
		CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
		kept.putInt(FARTHEST, blocks);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
	}

	/** A boss down with a player's hand in it: every player near it remembers how far out it was. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity dead = event.getEntity();

		if (!(dead instanceof Mob mob) || !(dead.level() instanceof ServerLevel level) || !FarHealth.boss(mob)) {
			return;
		}

		boolean player = event.getSource().getEntity() instanceof Player || mob.getLastHurtByMob() instanceof Player
				|| event.getSource().getEntity() instanceof OwnableEntity owned && owned.getOwnerUUID() != null;

		if (!player) {
			return;
		}

		int distance = (int) Math.round(FarFromSpawn.distance(level, dead.getX(), dead.getZ()));

		for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(dead) <= BOSS_PARTY_RANGE * BOSS_PARTY_RANGE)) {
			if (distance > farthest(near)) {
				int before = Math.round((float) (bonus(farthest(near), false) * 100));
				setFarthest(near, distance);
				int after = Math.round((float) (bonus(distance, false) * 100));

				if (after > before) {
					near.displayClientMessage(Component.literal(String.format("Farthest boss kill: %,d blocks out. Legendary weapons: +%d%% damage", distance, after))
							.withStyle(ChatFormatting.DARK_PURPLE), true);
				}

				LegionWareX.LOGGER.info("LegionWareX far weapons: {} beat {} {} blocks out (+{}% for legendary weapons)", near.getGameProfile().getName(),
						ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()), distance, after);
			}
		}
	}

	/** Once a second, a player's boss and legendary weapons take their farthest boss kill. */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		Player player = event.player;

		if (event.phase != TickEvent.Phase.END || player.level().isClientSide() || player.tickCount % 20 != 11) {
			return;
		}

		int farthest = farthest(player);

		for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
			for (ItemStack stack : list) {
				if (stack.isEmpty() || !kind(stack).legendary()) {
					continue;
				}

				boolean has = stack.hasTag() && stack.getTag().contains(BOSS);

				if (has ? stack.getTag().getInt(BOSS) != farthest : farthest > 0) {
					stack.getOrCreateTag().putInt(BOSS, farthest);
				}
			}
		}
	}

	/** Tags change what counts (a world's data packs, a client joining a server). */
	@SubscribeEvent
	public static void onTags(net.minecraftforge.event.TagsUpdatedEvent event) {
		KINDS.clear();
	}

	/** For the boot test: the legendary weapons this layer has. */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		KINDS.clear();
		List<String> legendary = new ArrayList<>();
		int found = 0;
		int spells = 0;

		for (Item item : ForgeRegistries.ITEMS) {
			Kind kind = kind(new ItemStack(item));

			if (kind.legendary()) {
				legendary.add(String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
			} else if (kind.spell()) {
				spells++;
			} else if (kind.weapon()) {
				found++;
			}
		}

		legendary.sort(null);
		LegionWareX.LOGGER.info("LegionWareX far weapons: {} weapons and {} spell weapons grow by where they're found, {} legendary by the farthest boss kill: {}",
				found, spells, legendary.size(), legendary);
	}
}
