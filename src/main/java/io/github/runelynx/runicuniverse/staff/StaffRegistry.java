package io.github.runelynx.runicuniverse.staff;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.function.Function;

/** All /rs actions known on this server, grouped into areas (and sub-areas), plus extra lines for the player info card. */
public final class StaffRegistry {

	private StaffRegistry() {}

	/** Menu order of areas, with their icons. Areas without any actions on this server are hidden. */
	static final LinkedHashMap<String, String> AREAS = new LinkedHashMap<>();
	/** Sub-area -> parent area (e.g. "Ranks" lives inside "Players"). Top-level areas have no entry. */
	static final Map<String, String> PARENT = new HashMap<>();
	static {
		AREAS.put("Players", "PLAYER_HEAD");
		AREAS.put("Rewards", "EMERALD");
		AREAS.put("Explorer's League", "COMPASS");
		AREAS.put("Events & Games", "FIREWORK_ROCKET");
		AREAS.put("Server", "COMMAND_BLOCK");
		AREAS.put("Tools", "SPYGLASS");
	}

	private static final LinkedHashMap<String, StaffAction> ACTIONS = new LinkedHashMap<>();
	private static final List<Function<OfflinePlayer, List<Component>>> PLAYER_INFO = new ArrayList<>();

	public static void register(StaffAction a) {
		StaffAction old = ACTIONS.put(a.name, a);
		if (old != null && old != a) {
			// Two plugins used the same /rs name: the later one wins. Say so loudly (this hid RunicFX on Survival once).
			io.github.runelynx.runicuniverse.RunicUniverse.getInstance().getLogger().warning("[rs] /rs " + a.name
					+ " was registered twice (" + old.area + " and " + a.area + "); the " + a.area + " one replaced the other.");
		}
		AREAS.putIfAbsent(a.area, "BOOK");
	}

	/** Declare a top-level area with its icon, placed just before another area (or at the end if that isn't there). */
	public static void area(String name, String icon, String before) {
		if (AREAS.containsKey(name) || !AREAS.containsKey(before)) {
			AREAS.put(name, icon);
			return;
		}
		LinkedHashMap<String, String> copy = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : AREAS.entrySet()) {
			if (e.getKey().equals(before)) copy.put(name, icon);
			copy.put(e.getKey(), e.getValue());
		}
		AREAS.clear();
		AREAS.putAll(copy);
	}

	/** Declare a sub-menu inside another area, e.g. subArea("Ranks", "Players", "LADDER"). */
	public static void subArea(String name, String parent, String icon) {
		AREAS.put(name, icon);
		PARENT.put(name, parent);
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

	static List<String> topLevel() {
		List<String> l = new ArrayList<>();
		for (String a : AREAS.keySet()) {
			if (!PARENT.containsKey(a)) l.add(a);
		}
		return l;
	}

	static List<String> children(String area) {
		List<String> l = new ArrayList<>();
		for (String a : AREAS.keySet()) {
			if (area.equals(PARENT.get(a))) l.add(a);
		}
		return l;
	}

	static List<StaffAction> allowedIn(String area, CommandSender s) {
		List<StaffAction> l = new ArrayList<>();
		for (StaffAction a : inArea(area)) {
			if (a.allowed(s)) l.add(a);
		}
		return l;
	}

	/** Number of things this sender can use in an area, counting sub-menus that have something in them. */
	static int countFor(String area, CommandSender s) {
		int n = allowedIn(area, s).size();
		for (String c : children(area)) {
			if (countFor(c, s) > 0) n++;
		}
		return n;
	}

	/** "Players › Ranks" for a sub-area, the plain name otherwise. */
	static String path(String area) {
		String p = PARENT.get(area);
		return p == null ? area : path(p) + " › " + area;
	}
}
