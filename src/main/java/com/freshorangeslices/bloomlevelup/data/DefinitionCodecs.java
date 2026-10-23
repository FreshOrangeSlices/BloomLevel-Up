package com.freshorangeslices.bloomlevelup.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.HubPosition;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;

/**
 * JSON shapes for datapack-authored definitions. The file location provides the id:
 * <ul>
 *   <li>{@code data/<ns>/bloom/category/<name>.json} defines category {@code <ns>:<name>} and its stats;</li>
 *   <li>{@code data/<ns>/bloom/major/<path>.json} defines Major {@code <ns>:<path>}
 *       (e.g. {@code major/utility/vein_mining.json} becomes {@code bloomlevelup:utility/vein_mining}).</li>
 * </ul>
 * References without a namespace (e.g. {@code "utility/vein_mining"}) resolve to the file's namespace.
 */
public final class DefinitionCodecs {
	private DefinitionCodecs() {
	}

	public record RawStat(String name, boolean toggleable, int sort, Optional<String> icon) {
		public static final Codec<RawStat> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(RawStat::name),
				Codec.BOOL.optionalFieldOf("toggleable", true).forGetter(RawStat::toggleable),
				Codec.INT.optionalFieldOf("sort", 0).forGetter(RawStat::sort),
				Codec.STRING.optionalFieldOf("icon").forGetter(RawStat::icon)
		).apply(i, RawStat::new));
	}

	public record RawCategory(int sort, boolean xpInvestment, Optional<String> icon, List<RawStat> stats) {
		public static final Codec<RawCategory> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("sort", 0).forGetter(RawCategory::sort),
				Codec.BOOL.optionalFieldOf("xp_investment", false).forGetter(RawCategory::xpInvestment),
				Codec.STRING.optionalFieldOf("icon").forGetter(RawCategory::icon),
				RawStat.CODEC.listOf().optionalFieldOf("stats", List.of()).forGetter(RawCategory::stats)
		).apply(i, RawCategory::new));

		public CategoryDefinition toDefinition(Identifier id) {
			String categoryId = id.toString();
			List<StatDefinition> list = new ArrayList<>();

			for (RawStat stat : stats) {
				String statId = categoryId + "/" + stat.name();
				list.add(new StatDefinition(statId, categoryId, stat.toggleable(), stat.sort(), stat.icon()));
			}

			return new CategoryDefinition(categoryId, sort, xpInvestment, icon, list);
		}
	}

	/** {@code "hub_position": [x, y]}: pixels on the tab's background picture, from its top-left corner. */
	public static final Codec<HubPosition> HUB_POSITION = Codec.INT.listOf().comapFlatMap(
			list -> list.size() == 2 ? DataResult.success(new HubPosition(list.get(0), list.get(1)))
					: DataResult.error(() -> "hub_position needs two numbers, [x, y]"),
			position -> List.of(position.x(), position.y()));

	public record RawMajor(
			String category,
			int cost,
			boolean toggleable,
			List<String> prerequisites,
			List<String> conflicts,
			Optional<String> group,
			int sort,
			Optional<String> icon,
			Optional<String> effect,
			List<String> flags,
			List<String> waives,
			Optional<HubPosition> hubPosition
	) {
		public static final Codec<RawMajor> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("category").forGetter(RawMajor::category),
				Codec.INT.fieldOf("cost").forGetter(RawMajor::cost),
				Codec.BOOL.optionalFieldOf("toggleable", true).forGetter(RawMajor::toggleable),
				Codec.STRING.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(RawMajor::prerequisites),
				Codec.STRING.listOf().optionalFieldOf("conflicts", List.of()).forGetter(RawMajor::conflicts),
				Codec.STRING.optionalFieldOf("group").forGetter(RawMajor::group),
				Codec.INT.optionalFieldOf("sort", 0).forGetter(RawMajor::sort),
				Codec.STRING.optionalFieldOf("icon").forGetter(RawMajor::icon),
				Codec.STRING.optionalFieldOf("effect").forGetter(RawMajor::effect),
				Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(RawMajor::flags),
				Codec.STRING.listOf().optionalFieldOf("waives", List.of()).forGetter(RawMajor::waives),
				HUB_POSITION.optionalFieldOf("hub_position").forGetter(RawMajor::hubPosition)
		).apply(i, RawMajor::new));

		public MajorDefinition toDefinition(Identifier id) {
			String ns = id.getNamespace();
			return new MajorDefinition(
					id.toString(),
					qualify(ns, category),
					cost,
					toggleable,
					prerequisites.stream().map(s -> qualify(ns, s)).toList(),
					conflicts.stream().map(s -> qualify(ns, s)).toList(),
					group.map(s -> qualify(ns, s)),
					sort,
					icon,
					effect,
					Set.copyOf(flags),
					waives.stream().map(s -> qualify(ns, s)).toList(),
					hubPosition
			);
		}
	}

	/** Adds the file's namespace to a bare reference; leaves fully qualified ids alone. */
	static String qualify(String namespace, String ref) {
		if (ref.startsWith("#")) {
			return "#" + qualify(namespace, ref.substring(1));
		}

		return ref.indexOf(':') >= 0 ? ref : namespace + ":" + ref;
	}
}
