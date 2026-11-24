package io.github.runelynx.runicuniverse.staff;

import io.github.runelynx.runicuniverse.RunicUniverse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /rs - Runic Staff. One command for staff tools on every server.
 *   /rs                 open the staff menu (players) or show help (console)
 *   /rs help [area]     clickable list of everything you can use here
 *   /rs <action> ...    run an action (tab completion everywhere)
 */
public final class Rs implements TabExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 0) {
			if (sender instanceof Player p) {
				StaffMenu.main(p);
			} else {
				help(sender, null);
			}
			return true;
		}
		String sub = args[0].toLowerCase();
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		switch (sub) {
			case "help":
				help(sender, rest.length > 0 ? String.join(" ", rest) : null);
				return true;
			case "menu":
				if (sender instanceof Player p) {
					StaffMenu.main(p);
				}
				return true;
			case "confirm":
			case "cancel":
				if (rest.length == 1) {
					Confirm.handle(sender, rest[0], sub.equals("confirm"));
				}
				return true;
			default:
				break;
		}
		StaffAction a = StaffRegistry.get(sub);
		if (a == null) {
			Ui.send(sender, Component.text("There's no /rs " + sub + " on " + RunicUniverse.SERVER_NAME + ". ", NamedTextColor.RED)
					.append(Ui.button("Show help", Ui.ACCENT, "/rs help", "List what /rs can do here")));
			return true;
		}
		run(sender, a, rest);
		return true;
	}

	static void run(CommandSender sender, StaffAction a, String[] args) {
		if (!a.allowed(sender)) {
			Ui.error(sender, "You don't have access to /rs " + a.name + ".");
			return;
		}
		if (a.playersOnly && !(sender instanceof Player)) {
			Ui.error(sender, "/rs " + a.name + " can only be used in game.");
			return;
		}
		try {
			a.exec.run(sender, args);
		} catch (RuntimeException e) {
			Ui.error(sender, "/rs " + a.name + " failed: " + e.getMessage());
			RunicUniverse.getInstance().getLogger().warning("[rs] " + a.name + " failed for " + sender.getName() + ": " + e);
		}
	}

	static void help(CommandSender s, String areaFilter) {
		Ui.send(s, Component.text("Runic Staff on " + RunicUniverse.SERVER_NAME, Ui.ACCENT, TextDecoration.BOLD)
				.append(Component.text("  · click a command to fill it in", NamedTextColor.DARK_GRAY)));
		boolean any = false;
		for (String area : StaffRegistry.AREAS.keySet()) {
			if (areaFilter != null && !area.equalsIgnoreCase(areaFilter)) {
				continue;
			}
			List<StaffAction> list = new ArrayList<>();
			for (StaffAction a : StaffRegistry.inArea(area)) {
				if (a.allowed(s)) {
					list.add(a);
				}
			}
			if (list.isEmpty()) {
				continue;
			}
			any = true;
			s.sendMessage(Component.text(" " + area, NamedTextColor.GOLD, TextDecoration.BOLD));
			for (StaffAction a : list) {
				String full = "/rs " + a.name + (a.usage.isEmpty() ? "" : " " + a.usage);
				s.sendMessage(Component.text("   ")
						.append(Ui.suggest(full, NamedTextColor.AQUA, "/rs " + a.name + " ", a.description))
						.append(Component.text(" " + a.description, NamedTextColor.GRAY)));
			}
		}
		if (!any) {
			Ui.info(s, "Nothing here for you yet.");
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
		if (args.length == 1) {
			List<String> out = new ArrayList<>();
			out.add("help");
			for (StaffAction a : StaffRegistry.all()) {
				if (a.allowed(sender)) {
					out.add(a.name);
				}
			}
			return filter(out, args[0]);
		}
		StaffAction a = StaffRegistry.get(args[0]);
		if (args[0].equalsIgnoreCase("help")) {
			return filter(new ArrayList<>(StaffRegistry.AREAS.keySet()), args[args.length - 1]);
		}
		if (a == null || !a.allowed(sender)) {
			return Collections.emptyList();
		}
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		return filter(a.tab(sender, rest), args[args.length - 1]);
	}

	static List<String> filter(Collection<String> options, String prefix) {
		List<String> r = new ArrayList<>();
		String p = prefix.toLowerCase();
		for (String o : options) {
			if (o.toLowerCase().startsWith(p)) {
				r.add(o);
			}
		}
		Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
		return r;
	}
}
