package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.staff.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunicFX (2026-11-28): particle effects staff (and later everyone) can play on demand or tie to events.
 * Phase 1: the FX library, the editor, staff quick-triggers and staff event FX. Runs on every server.
 * Phase 2: players (/runicfx), grants, reward rules, the welcome-back reward, no-FX zones, hide-others.
 * Helper and up can trigger; anyone above helper can build and edit.
 */
public final class RunicFX {

	private RunicFX() {}

	public static final String TRIGGER_PERM = "rp.staff";
	private static final String[] BUILD_PERMS = { "runicfx.build", "rp.staff.enforcer", "rp.staff.architect", "rp.staff.director",
			"rp.staff.mod", "rp.staff.mod+", "rp.staffplus", "rp.admin" };

	public static boolean canBuild(CommandSender s) {
		if (!(s instanceof Player) || s.isOp()) return true;
		for (String p : BUILD_PERMS) if (s.hasPermission(p)) return true;
		return false;
	}

	// ------------------------------------------------------------------ coded effects (registered by plugins)

	public interface Coded { void play(Player target, Location at); }

	private static final Map<String, Coded> CODED = new ConcurrentHashMap<>();

	/** Lets a plugin provide a Java-built effect; add it to the library (type CODED, same key) to use it. */
	public static void registerCoded(String key, Coded fx) {
		CODED.put(key.toLowerCase(Locale.ROOT), fx);
	}

	static Coded coded(String key) {
		return CODED.get(key);
	}

	/**
	 * Something happened to a player that RunicFX effects can be tied to (called by RunicParadise for Survival events:
	 * RANK_UP, FAITH_LEVEL, FAITH_CAST, EL_FIND, MAZE_FINISH, RAFFLE_WIN). Plays their chosen effect, and re-checks
	 * reward rules after rank-ups, EL finds and maze finishes.
	 */
	public static void event(Player p, String key) {
		if (p == null || key == null) return;
		if (!Bukkit.isPrimaryThread()) { // some callers run in background tasks
			Bukkit.getScheduler().runTask(io.github.runelynx.runicuniverse.RunicUniverse.getInstance(), () -> event(p, key));
			return;
		}
		String k = key.toUpperCase(Locale.ROOT);
		FxEvents.fire(p, k, p.getLocation());
		if (k.equals("RANK_UP") || k.equals("EL_FIND") || k.equals("MAZE_FINISH")) FxRewards.checkLater(p, 40);
	}

	/** Play a library effect by key on a player (for other plugins and future hooks). */
	public static boolean play(String key, Player target) {
		FxDef d = FxStore.get(key);
		return d != null && d.enabled && FxEngine.play(d, target);
	}

	// ------------------------------------------------------------------ setup

	public static void init(Plugin plugin) {
		Bukkit.getPluginManager().registerEvents(new FxEvents(), plugin);
		Bukkit.getPluginManager().registerEvents(new FxPrompt(), plugin);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			FxGrants.createTables();
			FxZones.reload();
			for (Player p : Bukkit.getOnlinePlayers()) {
				FxGrants.preload(p.getUniqueId());
				FxPrefs.load(p.getUniqueId());
			}
		});
		Bukkit.getScheduler().runTaskAsynchronously(plugin, FxStore::reload); // creates Confetti Rain on first run
		FxEvents.hookVotifier(plugin);
		FxRewards.startTicker();
		// every minute: re-read online players' settings, event choices and given effects (staff changes from other servers)
		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			List<UUID> ids = new ArrayList<>();
			for (Player p : Bukkit.getOnlinePlayers()) ids.add(p.getUniqueId());
			if (ids.isEmpty()) return;
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				for (UUID id : ids) {
					FxPrefs.load(id);
					FxStore.preload(id);
					FxGrants.preload(id);
				}
			});
		}, 20L * 60, 20L * 60);
		org.bukkit.command.PluginCommand pc = ((org.bukkit.plugin.java.JavaPlugin) plugin).getCommand("runicfx");
		if (pc != null) pc.setExecutor(new FxPlayerMenu());
		StaffRegistry.area("RunicFX", "BLAZE_POWDER", "Server");
		// Players area: manage one player's RunicFX (settings, event choices, given effects)
		StaffRegistry.register(StaffAction.of("runicfx", "Players").icon("BLAZE_POWDER").perm(TRIGGER_PERM).targetsPlayer().playersOnly()
				.desc("A player's RunicFX: switch their two settings, clear their event effects, give or take effects")
				.usage("<player>")
				.menu(p -> StaffMenu.pickPlayer(p, "&8RunicFX: pick a player", t -> FxStaffRewards.manage(p, t.getUniqueId(), t.getName()),
						() -> StaffMenu.area(p, "Players")))
				.complete((s, a) -> a.length == 1 ? CoreActions.onlineNames() : null)
				.exec((s, a) -> {
					if (a.length < 1) {
						Ui.error(s, "Usage: /rs runicfx <player>  (online or offline)");
						return;
					}
					FxStaffRewards.manageByName((Player) s, a[0]);
				}));
		StaffRegistry.register(StaffAction.of("fx", "RunicFX").icon("BLAZE_POWDER").perm(TRIGGER_PERM)
				.desc("RunicFX: play effects on yourself or others, give your teleports and joins an effect, build new ones")
				.usage("[menu|play <fx> [player]|list|bind <event> <fx|none>|grant <player> <fx> [days]|revoke <player> <fx>|player <name>|reload]")
				.menu(FxMenu::main)
				.complete((s, a) -> {
					if (a.length == 1) return Arrays.asList("menu", "play", "list", "bind", "grant", "revoke", "player", "reload");
					List<String> keys = new ArrayList<>();
					for (FxDef d : FxStore.library()) if (d.enabled) keys.add(d.key);
					if (a[0].equalsIgnoreCase("player")) return a.length == 2 ? CoreActions.onlineNames() : null;
					if (a[0].equalsIgnoreCase("grant") || a[0].equalsIgnoreCase("revoke")) return a.length == 2 ? CoreActions.onlineNames() : a.length == 3 ? keys : null;
					if (a[0].equalsIgnoreCase("play")) return a.length == 2 ? keys : a.length == 3 ? CoreActions.onlineNames() : null;
					if (a[0].equalsIgnoreCase("bind")) {
						if (a.length == 2) return new ArrayList<>(FxEvents.EVENTS.keySet());
						if (a.length == 3) {
							keys.add("none");
							return keys;
						}
					}
					return null;
				})
				.exec(RunicFX::command));
	}

	static void command(CommandSender s, String[] a) {
		String sub = a.length == 0 ? (s instanceof Player ? "menu" : "list") : a[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "menu":
				if (s instanceof Player) FxMenu.main((Player) s);
				return;
			case "list":
				Ui.send(s, Component.text("RunicFX library", NamedTextColor.WHITE));
				for (FxDef d : FxStore.library()) {
					s.sendMessage(Component.text("   ")
							.append(Ui.link(d.display, d.enabled ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY, "/rs fx play " + d.key, "Play it on yourself"))
							.append(Component.text("  " + d.key + " · " + d.category + " · " + d.type.name().toLowerCase(Locale.ROOT)
									+ (d.enabled ? "" : " · off"), NamedTextColor.DARK_GRAY)));
				}
				return;
			case "player":
				if (!(s instanceof Player) || a.length < 2) {
					Ui.error(s, "Usage (in game): /rs fx player <name>");
					return;
				}
				FxStaffRewards.manageByName((Player) s, a[1]);
				return;
			case "reload":
				FxStore.reload();
				Bukkit.getScheduler().runTaskAsynchronously(io.github.runelynx.runicuniverse.RunicUniverse.getInstance(), FxZones::reload);
				Ui.ok(s, "RunicFX library reloaded (" + FxStore.library().size() + " effects).");
				return;
			case "play": {
				if (a.length < 2) {
					Ui.error(s, "Usage: /rs fx play <fx> [player]");
					return;
				}
				FxDef d = FxStore.get(a[1]);
				if (d == null || (!d.enabled && !canBuild(s))) {
					Ui.error(s, "No effect called " + a[1] + ". Try /rs fx list");
					return;
				}
				Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player ? (Player) s : null;
				if (target == null) {
					Ui.error(s, a.length > 2 ? a[2] + " isn't online here." : "Name a player.");
					return;
				}
				FxEngine.play(d, target);
				if (target != s) {
					Audit.log(s, "fx play", d.key + " on " + target.getName());
					Ui.ok(s, "Played " + d.display + " on " + target.getName() + ".");
				}
				return;
			}
			case "bind": {
				if (!(s instanceof Player) || a.length < 3 || !FxEvents.EVENTS.containsKey(a[1].toUpperCase(Locale.ROOT))) {
					Ui.error(s, "Usage: /rs fx bind <" + String.join("|", FxEvents.EVENTS.keySet()) + "> <fx|none>");
					return;
				}
				Player p = (Player) s;
				String ev = a[1].toUpperCase(Locale.ROOT);
				if (a[2].equalsIgnoreCase("none")) {
					FxStore.bind(p.getUniqueId(), ev, null);
					Ui.ok(p, FxEvents.EVENTS.get(ev) + ": no effect.");
					return;
				}
				FxDef d = FxStore.get(a[2]);
				if (d == null || !d.enabled) {
					Ui.error(p, "No effect called " + a[2] + ".");
					return;
				}
				FxStore.bind(p.getUniqueId(), ev, d.key);
				Ui.ok(p, FxEvents.EVENTS.get(ev) + ": " + d.display + ".");
				return;
			}
			case "grant":
			case "revoke": {
				boolean grant = sub.equals("grant");
				if (!canBuild(s)) {
					Ui.error(s, "Only staff above helper can give or take effects.");
					return;
				}
				if (a.length < 3) {
					Ui.error(s, grant ? "Usage: /rs fx grant <player> <fx> [days]  (days: leave out for forever)" : "Usage: /rs fx revoke <player> <fx>");
					return;
				}
				FxDef d = FxStore.get(a[2]);
				if (d == null) {
					Ui.error(s, "No effect called " + a[2] + ". Try /rs fx list");
					return;
				}
				int days = 0;
				if (grant && a.length > 3) {
					try {
						days = Math.max(0, Integer.parseInt(a[3]));
					} catch (NumberFormatException e) {
						Ui.error(s, "Days must be a number (0 or leave it out for forever).");
						return;
					}
				}
				final int fDays = days;
				final String who = a[1], by = s.getName(), source = s instanceof Player ? "staff (" + s.getName() + ")" : "a reward";
				Bukkit.getScheduler().runTaskAsynchronously(io.github.runelynx.runicuniverse.RunicUniverse.getInstance(), () -> {
					Map.Entry<UUID, String> target = FxGrants.lookup(who);
					String result;
					boolean ok;
					if (target == null) {
						ok = false;
						result = "Nobody called " + who + " has played here.";
					} else if (grant) {
						ok = FxGrants.grant(target.getKey(), target.getValue(), d.key, source, by, fDays);
						result = ok ? "Gave " + target.getValue() + " " + d.display + (fDays > 0 ? " for " + fDays + " days." : ".") : target.getValue() + " already has " + d.display + ".";
					} else {
						ok = FxGrants.revoke(target.getKey(), d.key);
						result = ok ? "Took " + d.display + " from " + target.getValue() + "." : target.getValue() + " wasn't given " + d.display + " (rank permissions aren't changed here).";
					}
					Bukkit.getScheduler().runTask(io.github.runelynx.runicuniverse.RunicUniverse.getInstance(), () -> {
						if (ok) {
							Audit.log(s, grant ? "fx grant" : "fx revoke", d.key + (grant ? " to " : " from ") + target.getValue() + (fDays > 0 ? " (" + fDays + " days)" : ""));
							Ui.ok(s, result);
						} else {
							Ui.error(s, result);
						}
					});
				});
				return;
			}
			default:
				Ui.error(s, "Usage: /rs fx [menu|play <fx> [player]|list|bind <event> <fx|none>|grant <player> <fx> [days]|revoke <player> <fx>|player <name>|reload]");
		}
	}

	/** Where a player is looking (up to 40 blocks), for "play it over there". */
	static Location lookingAt(Player p) {
		Block b = p.getTargetBlockExact(40);
		return b == null ? null : b.getLocation().add(0.5, 1, 0.5);
	}
}
