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
		List<String> areas = new ArrayList<>();
		for (String area : StaffRegistry.AREAS.keySet()) {
			for (StaffAction a : StaffRegistry.inArea(area)) {
				if (a.allowed(p)) {
					areas.add(area);
					break;
				}
			}
		}
		Gui g = new Gui(4, "&8Runic Staff &7· &3" + RunicUniverse.SERVER_NAME);
		g.set(4, Gui.item("NETHER_STAR", "&b&lRunic Staff", "&7Server: &f" + RunicUniverse.SERVER_NAME,
				"&7Pick an area below.", "", "&8Tip: everything here also works as", "&8typed commands, e.g. &7/rs help"), null);
		int i = 0;
		for (String area : areas) {
			int count = 0;
			for (StaffAction a : StaffRegistry.inArea(area)) {
				if (a.allowed(p)) {
					count++;
				}
			}
			final String ar = area;
			g.set(INNER[i++], Gui.item(StaffRegistry.AREAS.get(area), "&e&l" + area, "&7" + count + " tool" + (count == 1 ? "" : "s"),
					"", "&eClick to open"), (pl, t) -> area(pl, ar));
		}
		g.set(31, Gui.item("BOOK", "&f&lHelp", "&7All /rs commands you can use", "&7here, as clickable chat lines"),
				(pl, t) -> {
					pl.closeInventory();
					Rs.help(pl, null);
				});
		g.frameRow(0).frameRow(3);
		g.openLater(p); // also reached from "Back" clicks inside other menus
	}

	public static void area(Player p, String area) {
		List<StaffAction> list = new ArrayList<>();
		for (StaffAction a : StaffRegistry.inArea(area)) {
			if (a.allowed(p)) {
				list.add(a);
			}
		}
		int rows = Math.min(6, 2 + (list.size() + 6) / 7);
		Gui g = new Gui(rows, "&8Staff &7· &0" + area);
		int i = 0;
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
		g.set(last, Gui.item("ARROW", "&7« Back"), (pl, t) -> main(pl));
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
					Rs.run(p, a, new String[] { target.getName() });
				} else {
					p.closeInventory();
					fillIn(p, "/rs " + a.name + " " + target.getName() + " ", afterPlayer);
				}
			}, () -> area(p, a.area));
			return;
		}
		p.closeInventory();
		if (a.usage.isEmpty()) {
			Rs.run(p, a, new String[0]);
		} else {
			fillIn(p, "/rs " + a.name + " ", a.usage);
		}
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
