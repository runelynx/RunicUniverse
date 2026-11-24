package io.github.runelynx.runicuniverse.staff;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.function.Function;

/** All /rs actions known on this server, grouped into areas, plus extra lines for the player info card. */
public final class StaffRegistry {

	private StaffRegistry() {}

	/** Menu order of areas, with their icons. Areas without any actions on this server are hidden. */
	static final LinkedHashMap<String, String> AREAS = new LinkedHashMap<>();
	static {
		AREAS.put("Players", "PLAYER_HEAD");
		AREAS.put("Rewards", "EMERALD");
		AREAS.put("Faith", "NETHER_STAR");
		AREAS.put("Explorer's League", "COMPASS");
		AREAS.put("Events & Games", "FIREWORK_ROCKET");
		AREAS.put("Server", "COMMAND_BLOCK");
		AREAS.put("Tools", "SPYGLASS");
	}

	private static final LinkedHashMap<String, StaffAction> ACTIONS = new LinkedHashMap<>();
	private static final List<Function<OfflinePlayer, List<Component>>> PLAYER_INFO = new ArrayList<>();

	public static void register(StaffAction a) {
		ACTIONS.put(a.name, a);
		AREAS.putIfAbsent(a.area, "BOOK");
	}

	/** Another plugin can add lines to /rs player (e.g. faith and tokens on Survival). */
	public static void addPlayerInfo(Function<OfflinePlayer, List<Component>> provider) {
		PLAYER_INFO.add(provider);
	}

	static List<Function<OfflinePlayer, List<Component>>> playerInfo() {
		return PLAYER_INFO;
	}

	static StaffAction get(String name) {
		return ACTIONS.get(name.toLowerCase());
	}

	static Collection<StaffAction> all() {
		return ACTIONS.values();
	}

	static List<StaffAction> inArea(String area) {
		List<StaffAction> l = new ArrayList<>();
		for (StaffAction a : ACTIONS.values()) {
			if (a.area.equals(area)) {
				l.add(a);
			}
		}
		return l;
	}
}
