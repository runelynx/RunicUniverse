package io.github.runelynx.runicuniverse.staff;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * One /rs action (e.g. "/rs nearall"). Plugins build these with {@link #of} and register them with
 * {@link StaffRegistry#register}. An action only exists on servers where the plugin that registers it runs,
 * so Survival-only tools simply don't appear on Hub, Creative or Skyblock.
 */
public final class StaffAction {

	public interface Exec { void run(CommandSender sender, String[] args); }
	public interface Completer { List<String> complete(CommandSender sender, String[] args); }
	public interface MenuClick { void click(Player player); }

	final String name;
	final String area;
	String description = "";
	String usage = "";
	String permission = "rp.staff";
	String icon = "PAPER";
	boolean playersOnly = false;
	boolean targetsPlayer = false; // first argument is an online player: the menu shows a player picker
	Exec exec;
	Completer completer;
	MenuClick menuClick;

	private StaffAction(String name, String area) {
		this.name = name.toLowerCase();
		this.area = area;
	}

	public static StaffAction of(String name, String area) {
		return new StaffAction(name, area);
	}

	public StaffAction desc(String d) { this.description = d; return this; }
	public StaffAction usage(String u) { this.usage = u; return this; }
	public StaffAction perm(String p) { this.permission = p; return this; }
	public StaffAction icon(String materialName) { this.icon = materialName; return this; }
	public StaffAction playersOnly() { this.playersOnly = true; return this; }
	public StaffAction targetsPlayer() { this.targetsPlayer = true; return this; }
	public StaffAction exec(Exec e) { this.exec = e; return this; }
	public StaffAction complete(Completer c) { this.completer = c; return this; }
	public StaffAction menu(MenuClick m) { this.menuClick = m; return this; }

	public String name() { return name; }
	public String area() { return area; }
	public String description() { return description; }
	public String usage() { return usage; }
	public String permission() { return permission; }

	boolean allowed(CommandSender s) {
		return s.hasPermission(permission);
	}

	List<String> tab(CommandSender s, String[] args) {
		if (completer == null) {
			return Collections.emptyList();
		}
		try {
			List<String> r = completer.complete(s, args);
			return r == null ? Collections.emptyList() : r;
		} catch (RuntimeException e) {
			return Collections.emptyList();
		}
	}
}
