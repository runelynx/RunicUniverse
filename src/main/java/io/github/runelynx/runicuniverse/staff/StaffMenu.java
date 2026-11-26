package io.github.runelynx.runicuniverse.staff;

import io.github.runelynx.runicuniverse.RunicUniverse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** The /rs staff menu: areas -> actions -> (player picker) -> run, fill in, or confirm. */
public final class StaffMenu {

	private StaffMenu() {}

	private static final int[] INNER = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };

	public static void main(Player p) {
		Gui g = new Gui(4, "&8Runic Staff &7· &3" + RunicUniverse.SERVER_NAME);
		g.set(4, Gui.item("NETHER_STAR", "&b&lRunic Staff", "&7Server: &f" + RunicUniverse.SERVER_NAME,
				"&7Pick an area below.", "", "&8Tip: everything here also works as", "&8typed commands, e.g. &7/rs help"), null);
		int i = 0;
		for (String area : StaffRegistry.topLevel()) {
			if (StaffRegistry.countFor(area, p) == 0 || i >= INNER.length) {
				continue;
			}
			g.set(INNER[i++], areaIcon(p, area), (pl, t) -> openArea(pl, area));
		}
		g.set(31, Gui.item("BOOK", "&f&lHelp", "&7All /rs commands you can use", "&7here, as clickable chat lines"),
				(pl, t) -> {
					pl.closeInventory();
					Rs.help(pl, null);
				});
		g.frameRow(0).frameRow(3);
		g.openLater(p); // also reached from "Back" clicks inside other menus
	}

	/** Icon for an area or sub-menu. An area holding exactly one tool (and no sub-menus) opens that tool directly. */
	static org.bukkit.inventory.ItemStack areaIcon(Player p, String area) {
		StaffAction single = singleTool(p, area);
		List<String> lore = new ArrayList<>();
		if (single != null) {
			for (String line : wrap(single.description, 34)) {
				lore.add("&7" + line);
			}
		} else {
			int count = StaffRegistry.countFor(area, p);
			lore.add("&7" + count + " tool" + (count == 1 ? "" : "s"));
		}
		lore.add("");
		lore.add("&eClick to open");
		return Gui.item(StaffRegistry.AREAS.get(area), "&e&l" + area, lore);
	}

	static StaffAction singleTool(Player p, String area) {
		List<StaffAction> l = StaffRegistry.allowedIn(area, p);
		for (String c : StaffRegistry.children(area)) {
			if (StaffRegistry.countFor(c, p) > 0) return null;
		}
		return l.size() == 1 ? l.get(0) : null;
	}

	static void openArea(Player p, String area) {
		StaffAction single = singleTool(p, area);
		if (single != null) {
			click(p, single);
		} else {
			area(p, area);
		}
	}

	public static void area(Player p, String area) {
		List<String> subs = new ArrayList<>();
		for (String c : StaffRegistry.children(area)) {
			if (StaffRegistry.countFor(c, p) > 0) subs.add(c);
		}
		List<StaffAction> list = StaffRegistry.allowedIn(area, p);
		int n = subs.size() + list.size();
		int rows = Math.min(6, 2 + (n + 6) / 7);
		Gui g = new Gui(rows, "&8Staff &7· &0" + StaffRegistry.path(area));
		int i = 0;
		for (String c : subs) {
			if (i >= INNER.length || INNER[i] >= (rows - 1) * 9) break;
			g.set(INNER[i++], areaIcon(p, c), (pl, t) -> openArea(pl, c));
		}
		for (StaffAction a : list) {
			if (i >= INNER.length || INNER[i] >= (rows - 1) * 9) {
				break;
			}
			List<String> lore = new ArrayList<>();
			for (String line : wrap(a.description, 34)) {
				lore.add("&7" + line);
			}
			lore.add("");
			lore.add("&8/rs " + a.name + (a.usage.isEmpty() ? "" : " " + a.usage));
			lore.add(a.menuClick != null || a.usage.isEmpty() ? "&eClick to open" : a.targetsPlayer ? "&eClick to pick a player" : "&eClick to fill in");
			g.set(INNER[i++], Gui.item(a.icon, "&b" + a.name, lore), (pl, t) -> click(pl, a));
		}
		int last = (rows - 1) * 9;
		String parent = StaffRegistry.PARENT.get(area);
		g.set(last, Gui.item("ARROW", "&7« Back" + (parent == null ? "" : " to " + parent)),
				(pl, t) -> { if (parent == null) main(pl); else area(pl, parent); });
		g.frameRow(0).frameRow(rows - 1);
		g.openLater(p);
	}

	static void click(Player p, StaffAction a) {
		if (a.menuClick != null) {
			a.menuClick.click(p);
			return;
		}
		if (a.targetsPlayer) {
			pickPlayer(p, "&8" + a.name + ": pick a player", target -> {
				String afterPlayer = a.usage.replaceFirst("^\\S+\\s*", "");
				if (afterPlayer.isEmpty()) {
					p.closeInventory();
					runFromMenu(p, a, new String[] { target.getName() });
				} else {
					pickChoice(p, a, target.getName(), afterPlayer);
				}
			}, () -> area(p, a.area));
			return;
		}
		p.closeInventory();
		if (a.usage.isEmpty()) {
			runFromMenu(p, a, new String[0]);
		} else {
			fillIn(p, "/rs " + a.name + " ", a.usage);
		}
	}

	/** Run an action on behalf of a menu click: its confirmations use the Yes / No menu instead of chat. */
	static void runFromMenu(Player p, StaffAction a, String[] args) {
		Confirm.fromMenu = true;
		try {
			Rs.run(p, a, args);
		} finally {
			Confirm.fromMenu = false;
		}
	}

	/**
	 * Second step after picking a player: the action's own tab-completion choices for the next argument as icons
	 * (souls amounts, weapon codes, colours, staff ranks...), plus "Other..." to type a value in chat.
	 */
	static void pickChoice(Player p, StaffAction a, String target, String stillNeeded) {
		pickChoice(p, a, new ArrayList<>(List.of(target)), stillNeeded);
	}

	/** One menu per remaining argument (e.g. crate, then amount); the last choice runs the action. */
	static void pickChoice(Player p, StaffAction a, List<String> chosen, String stillNeeded) {
		String[] parts = stillNeeded.trim().split("\\s+", 2);
		String argName = parts[0].replaceAll("[<>\\[\\]]", "");
		String after = parts.length > 1 ? parts[1] : "";
		String[] probe = new String[chosen.size() + 1];
		for (int i = 0; i < chosen.size(); i++) probe[i] = chosen.get(i);
		probe[chosen.size()] = "";
		List<String> options = a.tab(p, probe);
		String typed = "/rs " + a.name + " " + String.join(" ", chosen) + " ";
		if (options.isEmpty()) {
			p.closeInventory();
			fillIn(p, typed, stillNeeded);
			return;
		}
		int shown = Math.min(options.size(), 44);
		int rows = Math.min(6, 2 + (shown + 1 + 8) / 9);
		Gui g = new Gui(rows, "&8" + a.name + ": pick " + argName);
		int slot = 0;
		for (String opt : options.subList(0, shown)) {
			org.bukkit.inventory.ItemStack icon = a.choiceIcon == null ? null : a.choiceIcon.icon(chosen.size(), opt);
			if (icon == null) {
				icon = Gui.item(a.icon, "&b" + opt, "&7" + typed + opt, "&eClick to choose");
			}
			g.set(slot++, icon, (pl, t) -> {
				List<String> next = new ArrayList<>(chosen);
				next.add(opt);
				if (after.isEmpty()) {
					pl.closeInventory();
					runFromMenu(pl, a, next.toArray(new String[0]));
				} else {
					pickChoice(pl, a, next, after);
				}
			});
		}
		g.set(slot, Gui.item("OAK_SIGN", "&fOther…", "&7Type your own value in chat"), (pl, t) -> {
			pl.closeInventory();
			fillIn(pl, typed, stillNeeded);
		});
		g.set((rows - 1) * 9, Gui.item("ARROW", "&7« Back"), (pl, t) -> click(pl, a));
		g.frameRow(rows - 1);
		g.openLater(p);
	}

	/** Chat line with a button that puts the command into the chat box. */
	public static void fillIn(Player p, String command, String stillNeeded) {
		Ui.send(p, Component.text("Almost there: ", NamedTextColor.GRAY)
				.append(Ui.suggest(command.trim() + " " + stillNeeded, Ui.ACCENT, command, "Click, then finish typing (Tab completes)")));
	}

	public static void pickPlayer(Player p, String title, Consumer<Player> chosen, Runnable back) {
		List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
		online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
		int rows = Math.min(6, 2 + (Math.min(online.size(), 45) + 8) / 9);
		Gui g = new Gui(rows, title);
		int slot = 0;
		for (Player o : online) {
			if (slot >= (rows - 1) * 9) {
				break;
			}
			List<String> lore = new ArrayList<>();
			lore.add("&7World: &f" + o.getWorld().getName());
			lore.add("&eClick to choose");
			g.set(slot++, Gui.head(o, "&f" + o.getName(), lore), (pl, t) -> chosen.accept(o));
		}
		int last = (rows - 1) * 9;
		if (back != null) {
			g.set(last, Gui.item("ARROW", "&7« Back"), (pl, t) -> back.run());
		}
		g.frameRow(rows - 1);
		g.openLater(p);
	}

	static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String w : text.split(" ")) {
			if (line.length() + w.length() + 1 > width && line.length() > 0) {
				out.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(w);
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}
}
