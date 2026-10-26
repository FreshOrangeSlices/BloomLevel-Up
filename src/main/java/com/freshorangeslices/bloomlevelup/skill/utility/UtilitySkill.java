package com.freshorangeslices.bloomlevelup.skill.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Utility XP (mcMMO-style, per block from data), Yield, Shovel Mastery treasure, and the six Utility Majors.
 *
 * <p>Hook order inside vanilla's block break: Forge's break event fires just before the block is removed and
 * the drops are rolled. So the placed-block flag is read and cleared there and remembered for the rest of the
 * tick ({@link #BROKEN_PLACED}) so the drop hook (a global loot modifier) can still refuse Yield on placed blocks.
 * Vein Mining and Timber are queued and run at the end of the tick, outside the original break.
 */
public final class UtilitySkill {
	/**
	 * Shovel Mastery treasure is themed by biome: the first biome tag {@code bloomlevelup:excavation/<pool>} the
	 * dug block's biome is in picks loot table {@code bloomlevelup:gameplay/excavation/<pool>}; anything else
	 * uses {@code .../default}. Modpacks add biomes to the tags (the common {@code c:} tags are already in).
	 */
	public static final List<String> TREASURE_POOLS = List.of("nether", "desert", "beach", "swamp", "mountain", "forest");
	public static final ResourceLocation EXCAVATION_DEFAULT = treasureTable("default");

	public static ResourceLocation treasureTable(String pool) {
		return BloomLevelUp.id("gameplay/excavation/" + pool);
	}

	/** The treasure table for a dug block, by its biome. */
	public static ResourceLocation treasureTableAt(ServerLevel level, BlockPos pos) {
		Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(pos);

		for (String pool : TREASURE_POOLS) {
			if (biome.is(net.minecraft.tags.TagKey.create(Registries.BIOME, BloomLevelUp.id("excavation/" + pool)))) {
				return treasureTable(pool);
			}
		}

		return EXCAVATION_DEFAULT;
	}

	private enum ChainKind {
		VEIN,
		TIMBER
	}

	private record ChainJob(ServerPlayer player, ServerLevel level, BlockPos origin, Block block, Item tool, ChainKind kind) {
	}

	private record BrokenKey(ServerLevel level, long pos) {
	}

	private record PendingShear(ServerPlayer player, InteractionHand hand, Entity entity) {
	}

	private static final Set<BrokenKey> BROKEN_PLACED = new HashSet<>();
	private static final List<ChainJob> CHAIN_QUEUE = new ArrayList<>();
	private static final Map<UUID, PendingShear> PENDING_SHEARS = new HashMap<>();
	private static boolean chainBreaking;
	private static boolean rollingExtraLoot;
	private static int tickCounter;

	private UtilitySkill() {
	}

	public static void init(IEventBus modBus) {
		PlacedBlocks.init(modBus);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent event) -> {
			if (event.getPlayer() != null) {
				beforeBreak(event.getLevel() instanceof ServerLevel level ? level : null, event.getPlayer(), event.getPos(), event.getState());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
			if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level) {
				onPlaced(level, event.getPos());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> beforeInteract(event.getEntity(), event.getHand(),
				event.getTarget()));
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				endTick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> BlockXpTable.invalidate());
		MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
			if (event.getPlayer() == null) {
				BlockXpTable.invalidate();
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			BROKEN_PLACED.clear();
			CHAIN_QUEUE.clear();
			PENDING_SHEARS.clear();
		});
	}

	private static ProgressionService service() {
		return BloomServices.progression();
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	private static boolean active(ServerPlayer player, String majorId) {
		return service().isMajorActive(BloomAttachments.profile(player), majorId);
	}

	private static int stat(ServerPlayer player, String statId) {
		return service().statValue(BloomAttachments.profile(player), statId);
	}

	// ------------------------------------------------------------------ classification

	/** Whether a block is one the anti-exploit memory needs to remember when a player places it. */
	public static boolean tracksPlacement(BlockState state) {
		if (state.is(BloomTags.MATURE_ONLY)) {
			return false; // crops have to grow, so placing them is never an exploit
		}

		return BlockXpTable.get().lookup(state).isPresent() || state.is(BloomTags.YIELD_ELIGIBLE)
				|| state.is(BloomTags.EXCAVATION_TREASURE) || state.is(BloomTags.TIMBER_LOGS);
	}

	/** Crops count only when fully grown: any integer "age" property must be at its maximum. */
	public static boolean isMature(BlockState state) {
		IntegerProperty age = ageOf(state);
		return age == null || state.getValue(age) >= Collections.max(age.getPossibleValues());
	}

	/** A block's integer "age" property (crops, berries, wart), or null. */
	private static IntegerProperty ageOf(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
				return age;
			}
		}

		return null;
	}

	/** Shovel Mastery applies while a shovel is in the main hand. */
	public static boolean shovelBoost(ServerPlayer player) {
		return shovelBoost(player, player.getMainHandItem());
	}

	/** Whether Shovel Mastery would apply with {@code held} in the main hand. */
	public static boolean shovelBoost(ServerPlayer player, ItemStack held) {
		return held.is(ItemTags.SHOVELS) && active(player, UtilityIds.SHOVEL_MASTERY);
	}

	/** Efficiency, including Shovel Mastery's bonus. Used by StatEffects. */
	public static int efficiency(ServerPlayer player) {
		return efficiency(player, player.getMainHandItem());
	}

	/** Efficiency with {@code held} in the main hand (the stat sheet asks about other tools too). */
	public static int efficiency(ServerPlayer player, ItemStack held) {
		int points = stat(player, UtilityIds.EFFICIENCY);
		return shovelBoost(player, held) ? StatFormulas.boosted(points, config().shovelMasteryBonus) : points;
	}

	/** {@link #efficiency(ServerPlayer, ItemStack)} on either side (the client mines with its synced copy). */
	public static int efficiencyAnySide(Player player, ItemStack held) {
		if (player instanceof ServerPlayer serverPlayer) {
			return efficiency(serverPlayer, held);
		}

		int points = SideBridge.statValue(player, UtilityIds.EFFICIENCY);
		boolean shovel = held.is(ItemTags.SHOVELS) && SideBridge.majorActive(player, UtilityIds.SHOVEL_MASTERY);
		return shovel ? StatFormulas.boosted(points, SideBridge.config(player).shovelMasteryBonus) : points;
	}

	// ------------------------------------------------------------------ placing (Forge's place event)

	public static void onPlaced(ServerLevel level, BlockPos pos) {
		if (tracksPlacement(level.getBlockState(pos))) {
			PlacedBlocks.mark(level, pos);
		}
	}

	// ------------------------------------------------------------------ breaking

	/** A player is breaking a block (Forge's break event, lowest priority, so only breaks nothing cancelled). */
	public static void beforeBreak(ServerLevel serverLevel, Player breaker, BlockPos pos, BlockState state) {
		if (serverLevel == null || !(breaker instanceof ServerPlayer player)) {
			return;
		}

		boolean placed = PlacedBlocks.clear(serverLevel, pos);

		if (placed) {
			BROKEN_PLACED.add(new BrokenKey(serverLevel, pos.asLong()));
		}

		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		BlockXpTable table = BlockXpTable.get();

		// XP like mcMMO: natural blocks only, with a tool that actually harvests the block.
		if (!placed && (!state.is(BloomTags.MATURE_ONLY) || isMature(state)) && player.hasCorrectToolForDrops(state)) {
			table.lookup(state).ifPresent(entry -> SkillXp.award(player, entry.category(), entry.xp()));
		}

		// Sugar cane, cactus...: the segments above pop off without a player break, so count them now.
		if (state.is(BloomTags.XP_COLUMN)) {
			BlockPos above = pos.above();

			for (int i = 0; i < 32 && serverLevel.getBlockState(above).is(state.getBlock()); i++, above = above.above()) {
				if (!PlacedBlocks.clear(serverLevel, above)) {
					table.lookup(state).ifPresent(entry -> SkillXp.award(player, entry.category(), entry.xp()));
				}
			}
		}

		if (!chainBreaking) {
			queueChain(player, serverLevel, pos, state, placed);
		}
	}

	private static void queueChain(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, boolean placed) {
		if (config().chainBreakSneakBypass && player.isShiftKeyDown()) {
			return;
		}

		ItemStack tool = player.getMainHandItem();

		if (state.is(BloomTags.VEIN_MINEABLE) && tool.is(ItemTags.PICKAXES) && player.hasCorrectToolForDrops(state)
				&& active(player, UtilityIds.VEIN_MINING)) {
			CHAIN_QUEUE.add(new ChainJob(player, level, pos.immutable(), state.getBlock(), tool.getItem(), ChainKind.VEIN));
		} else if (!placed && state.is(BloomTags.TIMBER_LOGS) && tool.is(ItemTags.AXES) && active(player, UtilityIds.TIMBER)) {
			CHAIN_QUEUE.add(new ChainJob(player, level, pos.immutable(), state.getBlock(), tool.getItem(), ChainKind.TIMBER));
		}
	}

	/** Runs queued Vein Mining / Timber jobs. Public for game tests. */
	public static void runChains() {
		if (CHAIN_QUEUE.isEmpty()) {
			return;
		}

		List<ChainJob> jobs = new ArrayList<>(CHAIN_QUEUE);
		CHAIN_QUEUE.clear();

		for (ChainJob job : jobs) {
			ServerPlayer player = job.player();

			if (player.isRemoved() || player.level() != job.level()) {
				continue;
			}

			List<BlockPos> targets = job.kind() == ChainKind.VEIN
					? ChainBreaker.vein(job.level(), job.origin(), job.block(), config().veinMiningMaxBlocks)
					: ChainBreaker.tree(job.level(), job.origin(), config().timberMaxLogs);

			for (BlockPos target : targets) {
				ItemStack tool = player.getMainHandItem();

				// Stop with the tool's last point of durability left rather than destroying it.
				if (tool.isEmpty() || tool.getItem() != job.tool()
						|| (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1)) {
					break;
				}

				BlockState current = job.level().getBlockState(target);
				boolean stillValid = job.kind() == ChainKind.VEIN ? current.is(job.block()) : current.is(BloomTags.TIMBER_LOGS);

				if (!stillValid) {
					continue;
				}

				chainBreaking = true;

				try {
					player.gameMode.destroyBlock(target);
				} finally {
					chainBreaking = false;
				}
			}
		}
	}

	// ------------------------------------------------------------------ drops

	/** Every loot roll's drops (Bloom's global loot modifier): blocks, fishing. */
	public static void modifyDrops(ResourceLocation table, LootContext context, List<ItemStack> drops) {
		if (rollingExtraLoot) {
			return;
		}

		if (table.equals(BuiltInLootTables.FISHING)) {
			fishingDrops(table, context, drops);
			return;
		}

		if (table.getPath().startsWith("blocks/") && context.hasParam(LootContextParams.BLOCK_STATE)) {
			blockDrops(context, drops);
		}
	}

	private static void blockDrops(LootContext context, List<ItemStack> drops) {
		if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) || player.isCreative()) {
			return;
		}

		BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
		Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);

		if (state == null || origin == null) {
			return;
		}

		ServerLevel level = context.getLevel();
		BlockPos pos = BlockPos.containing(origin);
		boolean crop = state.is(BloomTags.MATURE_ONLY);

		if (crop ? !isMature(state) : (BROKEN_PLACED.contains(new BrokenKey(level, pos.asLong())) || PlacedBlocks.isPlaced(level, pos))) {
			return;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();

		if (state.is(BloomTags.YIELD_ELIGIBLE) && !drops.isEmpty()) {
			addCopies(drops, StatFormulas.yieldExtraCopies(stat(player, UtilityIds.YIELD), random));
		}

		ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);

		if (state.is(BloomTags.EXCAVATION_TREASURE) && tool != null && tool.is(ItemTags.SHOVELS)
				&& active(player, UtilityIds.SHOVEL_MASTERY)) {
			int yield = StatFormulas.boosted(stat(player, UtilityIds.YIELD), config().shovelMasteryBonus);
			double chance = config().excavationTreasureBaseChance + yield / 100.0 * config().excavationTreasureChancePer100Yield;
			int rolls = StatFormulas.rollsFromChance(chance, random);

			for (int i = 0; i < rolls; i++) {
				List<ItemStack> treasure = rollTreasure(level, player, origin);

				if (!treasure.isEmpty()) {
					drops.addAll(treasure);
					level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
				}
			}
		}
	}

	private static void addCopies(List<ItemStack> drops, int copies) {
		if (copies <= 0) {
			return;
		}

		List<ItemStack> extra = new ArrayList<>(drops.size() * copies);

		for (ItemStack drop : drops) {
			for (int i = 0; i < copies; i++) {
				extra.add(drop.copy());
			}
		}

		drops.addAll(extra);
	}

	private static List<ItemStack> rollTreasure(ServerLevel level, ServerPlayer player, Vec3 origin) {
		LootTable table = level.getServer().getLootData().getLootTable(treasureTableAt(level, BlockPos.containing(origin)));

		if (table == LootTable.EMPTY) {
			return List.of();
		}

		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, origin)
				.withParameter(LootContextParams.THIS_ENTITY, player)
				.withLuck(player.getLuck())
				.create(LootContextParamSets.CHEST);
		return table.getRandomItems(params);
	}

	private static void fishingDrops(ResourceLocation id, LootContext context, List<ItemStack> drops) {
		if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof FishingHook hook)
				|| !(hook.getPlayerOwner() instanceof ServerPlayer player)) {
			return;
		}

		SkillXp.award(player, SkillXp.UTILITY, config().utilityFishingXp);

		if (!active(player, UtilityIds.TRIPLE_CATCH) || config().tripleCatchRolls <= 1) {
			return;
		}

		// Independent extra rolls of the whole fishing table (fish, junk and treasure all participate).
		rollingExtraLoot = true;

		try {
			for (int i = 1; i < config().tripleCatchRolls; i++) {
				// The raw roll: no loot modifiers (this one included) run on it again.
				context.getLevel().getServer().getLootData().getLootTable(id).getRandomItemsRaw(context, drops::add);
			}
		} finally {
			rollingExtraLoot = false;
		}

		ItemStack rod = context.getParamOrNull(LootContextParams.TOOL);

		if (rod != null && rod.isDamageableItem() && config().tripleCatchExtraDurability > 0) {
			InteractionHand hand = player.getMainHandItem() == rod ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			rod.hurtAndBreak(config().tripleCatchExtraDurability, player, p -> p.broadcastBreakEvent(hand));
		}
	}

	/** Yield on the wool (or other drops) of a mob sheared with shears right now (ShearsItem mixin). */
	public static List<ItemStack> shearingDrops(Player player, List<ItemStack> drops) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative() || drops.isEmpty()) {
			return drops;
		}

		List<ItemStack> out = new ArrayList<>(drops);
		addCopies(out, StatFormulas.yieldExtraCopies(stat(serverPlayer, UtilityIds.YIELD), ThreadLocalRandom.current()));
		return out;
	}

	// ------------------------------------------------------------------ shearing

	private static void beforeInteract(Player player, InteractionHand hand, Entity entity) {
		if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()
				&& entity instanceof Shearable shearable && shearable.readyForShearing() && !serverPlayer.getItemInHand(hand).isEmpty()) {
			PENDING_SHEARS.put(entity.getUUID(), new PendingShear(serverPlayer, hand, entity));
		}
	}

	/** Records that a player is about to interact with a shearable mob. Public for game tests. */
	public static void expectShear(ServerPlayer player, InteractionHand hand, Entity entity) {
		PENDING_SHEARS.put(entity.getUUID(), new PendingShear(player, hand, entity));
	}

	/** Settles this tick's shear attempts: XP, Master Shearing's coin flip and its durability cost. Public for game tests. */
	public static void settleShears() {
		if (PENDING_SHEARS.isEmpty()) {
			return;
		}

		List<PendingShear> pending = new ArrayList<>(PENDING_SHEARS.values());
		PENDING_SHEARS.clear();

		for (PendingShear shear : pending) {
			Entity entity = shear.entity();
			boolean sheared = entity.isRemoved() || (entity instanceof Shearable shearable && !shearable.readyForShearing());
			ServerPlayer player = shear.player();

			if (!sheared || player.isRemoved()) {
				continue;
			}

			SkillXp.award(player, SkillXp.UTILITY, config().utilityShearXp);

			if (!active(player, UtilityIds.MASTER_SHEARING)) {
				continue;
			}

			ItemStack tool = player.getItemInHand(shear.hand());

			if (!tool.isEmpty() && tool.isDamageableItem() && config().masterShearingExtraDurability > 0) {
				tool.hurtAndBreak(config().masterShearingExtraDurability, player, p -> p.broadcastBreakEvent(shear.hand()));
			}

			if (!entity.isRemoved() && ThreadLocalRandom.current().nextDouble() < config().masterShearingChance && restoreShearable(entity)
					&& entity.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
						6, 0.4, 0.3, 0.4, 0.0);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
			}
		}
	}

	/** Puts a vanilla shearable back into its shearable state. Modded shearables are left alone. */
	private static boolean restoreShearable(Entity entity) {
		if (entity instanceof Sheep sheep) {
			sheep.setSheared(false);
			return true;
		} else if (entity instanceof SnowGolem golem) {
			golem.setPumpkin(true);
			return true;
		}

		return false;
	}

	// ------------------------------------------------------------------ ticking

	private static void endTick(MinecraftServer server) {
		runChains();
		settleShears();
		BROKEN_PLACED.clear();
		tickCounter++;

		boolean pulse = tickCounter % config().growthAuraIntervalTicks == 0;

		if (pulse) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				growthAura(player);
			}
		}
	}

	/**
	 * Growth Aura: while holding a hoe, every pulse gives each nearby plant a chance to grow. Crops jump a
	 * whole growth stage; other plants (cactus, cane, berries, saplings...) get a burst of growth ticks.
	 * Public for game tests.
	 */
	public static int growthAura(ServerPlayer player) {
		if (player.isSpectator() || !(player.getMainHandItem().is(ItemTags.HOES) || player.getOffhandItem().is(ItemTags.HOES))
				|| !active(player, UtilityIds.GROWTH_AURA) || !(player.level() instanceof ServerLevel level)) {
			return 0;
		}

		int radius = config().growthAuraRadius;
		int height = config().growthAuraHeight;
		double chance = config().growthAuraChance;
		BlockPos center = player.blockPosition();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int grown = 0;

		for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -height, -radius), center.offset(radius, height, radius))) {
			BlockState state = level.getBlockState(cursor);

			if (!state.is(BloomTags.GROWTH_AURA_CROPS) || !state.isRandomlyTicking() || random.nextDouble() >= chance) {
				continue;
			}

			BlockPos pos = cursor.immutable();

			IntegerProperty age = state.getBlock() instanceof net.minecraft.world.level.block.CropBlock ? ageOf(state) : null;

			if (age != null) {
				int max = Collections.max(age.getPossibleValues());

				if (state.getValue(age) < max) {
					level.setBlock(pos, state.setValue(age, state.getValue(age) + 1), Block.UPDATE_CLIENTS);
				}
			} else {
				for (int i = 0; i < config().growthAuraRandomTicks && level.getBlockState(pos).isRandomlyTicking(); i++) {
					level.getBlockState(pos).randomTick(level, pos, level.getRandom());
				}
			}

			if (level.getBlockState(pos) != state) {
				grown++;
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
			}
		}

		return grown;
	}
}
