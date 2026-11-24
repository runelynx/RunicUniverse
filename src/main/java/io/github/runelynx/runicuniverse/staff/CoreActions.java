package io.github.runelynx.runicuniverse.staff;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.github.runelynx.runicuniverse.RunicUniverse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

/** /rs actions that make sense on every server (Hub, Survival, Creative, Skyblock). */
public final class CoreActions {

	private CoreActions() {}

	static final List<String> STAFF_RANKS = Arrays.asList("Admin", "Director", "Architect", "Enforcer", "Helper", "None");

	public static void register() {
		// ---------------------------------------------------------------- Players
		StaffRegistry.register(StaffAction.of("player", "Players").icon("PLAYER_HEAD").targetsPlayer()
				.desc("Everything about a player on one card: where they are, rank, playtime and more")
				.usage("<player>")
				.complete((s, a) -> a.length == 1 ? onlineNames() : null)
				.exec((s, a) -> {
					if (a.length < 1) {
						Ui.error(s, "Usage: /rs player <player>");
						return;
					}
					playerCard(s, a[0]);
				}));

		StaffRegistry.register(StaffAction.of("nearall", "Players").icon("SPYGLASS")
				.desc("For every online player: their 5 nearest other players and how far away (up to 1,000 blocks)")
				.exec((s, a) -> nearAll(s)));

		StaffRegistry.register(StaffAction.of("staffrank", "Players").icon("GOLDEN_HELMET").perm("ru.staffpromotion").targetsPlayer()
				.desc("Set someone's staff rank on all servers (same as /staffrank)")
				.usage("<player> <" + String.join("|", STAFF_RANKS) + ">")
				.complete((s, a) -> a.length == 1 ? onlineNames() : a.length == 2 ? STAFF_RANKS : null)
				.exec((s, a) -> {
					if (a.length != 2 || !STAFF_RANKS.contains(a[1])) {
						Ui.error(s, "Usage: /rs staffrank <player> <" + String.join("|", STAFF_RANKS) + ">  (rank is case sensitive)");
						return;
					}
					Confirm.ask(s, "Set " + a[0] + "'s staff rank to " + a[1], () -> {
						Bukkit.dispatchCommand(s, "staffrank " + a[0] + " " + a[1]);
						Audit.log(s, "staffrank", a[0] + " -> " + a[1]);
					});
				}));

		// ---------------------------------------------------------------- Server
		StaffRegistry.register(StaffAction.of("announce", "Server").icon("BELL").perm("rp.staff.enforcer")
				.desc("This server's scheduled announcements: list, switch on/off, change text or timing")
				.usage("<list|toggle|set|delay|reload> [number] [text...]")
				.complete((s, a) -> {
					if (a.length == 1) {
						return Arrays.asList("list", "toggle", "set", "delay", "reload");
					}
					if (a.length == 2 && (a[0].equalsIgnoreCase("toggle") || a[0].equalsIgnoreCase("set"))) {
						return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9");
					}
					if (a.length == 2 && a[0].equalsIgnoreCase("delay")) {
						return Arrays.asList("15", "30", "45", "60");
					}
					return null;
				})
				.menu(p -> {
					p.closeInventory();
					Bukkit.dispatchCommand(p, "announce la");
					p.sendMessage(Component.text("   ")
							.append(Ui.suggest("Switch one on/off", NamedTextColor.YELLOW, "/rs announce toggle ", null))
							.append(Ui.space())
							.append(Ui.suggest("Change text", NamedTextColor.YELLOW, "/rs announce set ", null))
							.append(Ui.space())
							.append(Ui.suggest("Change timing", NamedTextColor.YELLOW, "/rs announce delay ", null)));
				})
				.exec((s, a) -> {
					String sub = a.length > 0 ? a[0].toLowerCase() : "list";
					switch (sub) {
						case "list":
							Bukkit.dispatchCommand(s, "announce la");
							break;
						case "toggle":
							need(a, 2, "/rs announce toggle <number>");
							Bukkit.dispatchCommand(s, "announce ta " + a[1]);
							Audit.log(s, "announce toggle", "message " + a[1]);
							break;
						case "set":
							need(a, 3, "/rs announce set <number> <text...>");
							String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
							Bukkit.dispatchCommand(s, "announce sa " + a[1] + " " + text);
							Audit.log(s, "announce set", "message " + a[1] + ": " + text);
							break;
						case "delay":
							need(a, 2, "/rs announce delay <minutes>");
							Bukkit.dispatchCommand(s, "announce cd " + a[1]);
							Audit.log(s, "announce delay", a[1] + " minutes");
							break;
						case "reload":
							Bukkit.dispatchCommand(s, "announce ra");
							break;
						default:
							Ui.error(s, "Usage: /rs announce <list|toggle|set|delay|reload>");
					}
				}));

		StaffRegistry.register(StaffAction.of("censor", "Server").icon("BARRIER").perm("rp.staff.director")
				.desc("The chat censor word list for all servers")
				.usage("<list|add|remove> [word]")
				.complete((s, a) -> a.length == 1 ? Arrays.asList("list", "add", "remove") : null)
				.menu(p -> {
					p.closeInventory();
					Bukkit.dispatchCommand(p, "censor lc");
					p.sendMessage(Component.text("   ")
							.append(Ui.suggest("Add a word", NamedTextColor.YELLOW, "/rs censor add ", null))
							.append(Ui.space())
							.append(Ui.suggest("Remove a word", NamedTextColor.YELLOW, "/rs censor remove ", null)));
				})
				.exec((s, a) -> {
					String sub = a.length > 0 ? a[0].toLowerCase() : "list";
					switch (sub) {
						case "list":
							Bukkit.dispatchCommand(s, "censor lc");
							break;
						case "add":
							need(a, 2, "/rs censor add <word>");
							Bukkit.dispatchCommand(s, "censor ac " + a[1]);
							Audit.log(s, "censor add", a[1]);
							break;
						case "remove":
							need(a, 2, "/rs censor remove <word>");
							Bukkit.dispatchCommand(s, "censor rc " + a[1]);
							Audit.log(s, "censor remove", a[1]);
							break;
						default:
							Ui.error(s, "Usage: /rs censor <list|add|remove> [word]");
					}
				}));

		if (!"Hub".equalsIgnoreCase(RunicUniverse.SERVER_NAME)) {
			StaffRegistry.register(StaffAction.of("evacuate", "Server").icon("ENDER_PEARL").perm("rp.staff.evacuate")
					.desc("Send everyone on " + RunicUniverse.SERVER_NAME + " to the Hub (e.g. before a restart)")
					.menu(p -> Confirm.menu(p, "Send everyone on " + RunicUniverse.SERVER_NAME + " to the Hub",
							Collections.singletonList("&7" + Bukkit.getOnlinePlayers().size() + " player(s) online here"),
							() -> evacuate(p), () -> StaffMenu.area(p, "Server")))
					.exec((s, a) -> Confirm.ask(s, "Send everyone on " + RunicUniverse.SERVER_NAME + " ("
							+ Bukkit.getOnlinePlayers().size() + " online) to the Hub", () -> evacuate(s))));
		}

		// ---------------------------------------------------------------- Tools
		StaffRegistry.register(StaffAction.of("pos", "Tools").icon("COMPASS").playersOnly()
				.desc("Your exact position and facing (yaw/pitch), with click-to-copy lines for commands")
				.usage("[player]")
				.complete((s, a) -> a.length == 1 ? onlineNames() : null)
				.menu(p -> {
					p.closeInventory();
					pos(p, p);
				})
				.exec((s, a) -> {
					Player target = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (Player) s;
					if (target == null) {
						Ui.error(s, a[0] + " isn't online here.");
						return;
					}
					pos(s, target);
				}));
	}

	// ------------------------------------------------------------------ implementations

	static void need(String[] a, int n, String usage) {
		if (a.length < n) {
			throw new IllegalArgumentException("usage: " + usage);
		}
	}

	public static List<String> onlineNames() {
		List<String> l = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			l.add(p.getName());
		}
		return l;
	}

	static void pos(CommandSender viewer, Player p) {
		Location l = p.getLocation();
		String xyz = String.format(Locale.ROOT, "%.2f %.2f %.2f", l.getX(), l.getY(), l.getZ());
		String facing = String.format(Locale.ROOT, "%.1f %.1f", l.getYaw(), l.getPitch());
		String dim = l.getWorld().getKey().toString();
		Ui.send(viewer, Component.text(p.getName() + " in " + l.getWorld().getName(), NamedTextColor.WHITE, TextDecoration.BOLD));
		viewer.sendMessage(Component.text("   Position: ", NamedTextColor.GRAY).append(Component.text(xyz, NamedTextColor.WHITE))
				.append(Component.text("   Facing (yaw pitch): ", NamedTextColor.GRAY)).append(Component.text(facing, NamedTextColor.WHITE))
				.append(Component.text("  " + compass(l.getYaw()), NamedTextColor.DARK_GRAY)));
		viewer.sendMessage(Component.text("   Copy: ", NamedTextColor.GRAY)
				.append(Ui.copy("position", xyz)).append(Ui.space())
				.append(Ui.copy("position + facing", xyz + " " + facing)).append(Ui.space())
				.append(Ui.copy("facing", facing)).append(Ui.space())
				.append(Ui.copy("tp command", "/minecraft:execute in " + dim + " run tp @s " + xyz + " " + facing)));
	}

	static String compass(float yaw) {
		String[] dirs = { "south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east" };
		int i = Math.round(((yaw % 360) + 360) % 360 / 45f) % 8;
		return "facing " + dirs[i];
	}

	static void nearAll(CommandSender s) {
		List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
		players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
		Ui.send(s, Component.text("Players near players", NamedTextColor.WHITE, TextDecoration.BOLD)
				.append(Component.text("  (5 nearest, within 1,000 blocks, same world)", NamedTextColor.DARK_GRAY)));
		if (players.isEmpty()) {
			Ui.info(s, "Nobody is online.");
			return;
		}
		for (Player p : players) {
			List<Object[]> near = new ArrayList<>();
			for (Player o : players) {
				if (o == p || o.getWorld() != p.getWorld()) {
					continue;
				}
				double d = o.getLocation().distance(p.getLocation());
				if (d <= 1000) {
					near.add(new Object[] { o.getName(), d });
				}
			}
			near.sort(Comparator.comparingDouble(x -> (Double) x[1]));
			Component line = Component.text("   ").append(Ui.link(p.getName(), NamedTextColor.AQUA, "/rs player " + p.getName(), "Open " + p.getName() + "'s card"))
					.append(Component.text(": ", NamedTextColor.GRAY));
			if (near.isEmpty()) {
				line = line.append(Component.text("nobody within 1,000", NamedTextColor.DARK_GRAY));
			}
			for (int i = 0; i < Math.min(5, near.size()); i++) {
				String n = (String) near.get(i)[0];
				int d = (int) Math.round((Double) near.get(i)[1]);
				if (i > 0) {
					line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
				}
				line = line.append(Ui.link(n, NamedTextColor.WHITE, "/rs player " + n, "Open " + n + "'s card"))
						.append(Component.text(" (" + d + ")", d < 50 ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
			}
			s.sendMessage(line);
		}
	}

	static void evacuate(CommandSender s) {
		int n = 0;
		for (Player p : Bukkit.getOnlinePlayers()) {
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("Connect");
			out.writeUTF("Hub");
			p.sendPluginMessage(RunicUniverse.getInstance(), "BungeeCord", out.toByteArray());
			n++;
		}
		Ui.ok(s, "Sent " + n + " player(s) to the Hub.");
		Audit.log(s, "evacuate", n + " player(s) sent to Hub");
	}

	static void playerCard(CommandSender s, String name) {
		Player p = Bukkit.getPlayerExact(name);
		OfflinePlayer op = p != null ? p : Bukkit.getOfflinePlayerIfCached(name);
		if (op == null || (p == null && !op.hasPlayedBefore())) {
			Ui.error(s, "I don't know a player called " + name + " on " + RunicUniverse.SERVER_NAME + ".");
			return;
		}
		SimpleDateFormat df = new SimpleDateFormat("d MMM yyyy");
		String shown = op.getName() == null ? name : op.getName();
		Component head = Component.text(shown, p != null ? NamedTextColor.GREEN : NamedTextColor.GRAY, TextDecoration.BOLD)
				.append(Component.text(p != null ? "  online" : "  offline", p != null ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
		if (p != null) {
			head = head.append(Component.text("   ")).append(Ui.button("Teleport to", NamedTextColor.AQUA, "/tp " + shown, "Teleport to " + shown))
					.append(Ui.space()).append(Ui.button("Position", NamedTextColor.AQUA, "/rs pos " + shown, "Exact position and facing"));
		}
		Ui.send(s, head);
		if (p != null) {
			Location l = p.getLocation();
			row(s, "Where", RunicUniverse.SERVER_NAME + " · " + l.getWorld().getName() + " "
					+ l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
			String rank = primaryGroup(p);
			if (rank != null) {
				row(s, "Rank", rank);
			}
			row(s, "Game mode", p.getGameMode().name().toLowerCase() + (p.isFlying() ? " (flying)" : ""));
			s.sendMessage(Component.text("   ").append(fields("Health", (int) Math.ceil(p.getHealth()), "Food", p.getFoodLevel(),
					"Level", p.getLevel())));
			row(s, "Ping", p.getPing() + " ms");
			long hours = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 3600;
			row(s, "Playtime here", hours + " h");
		}
		if (op.getFirstPlayed() > 0) {
			row(s, "First joined", df.format(new Date(op.getFirstPlayed())));
		}
		if (p == null && op.getLastPlayed() > 0) {
			row(s, "Last seen", df.format(new Date(op.getLastPlayed())));
		}
		for (java.util.function.Function<OfflinePlayer, List<Component>> f : StaffRegistry.playerInfo()) {
			try {
				List<Component> extra = f.apply(op);
				if (extra != null) {
					for (Component c : extra) {
						s.sendMessage(Component.text("   ").append(c));
					}
				}
			} catch (RuntimeException e) {
				RunicUniverse.getInstance().getLogger().warning("[rs] player info provider failed: " + e);
			}
		}
	}

	/** "Label: value   Label: value" with grey labels and white values (a Component value keeps its own colour). */
	public static Component fields(Object... labelValue) {
		Component c = Component.empty();
		for (int i = 0; i + 1 < labelValue.length; i += 2) {
			if (i > 0) {
				c = c.append(Component.text("   "));
			}
			c = c.append(Component.text(labelValue[i] + ": ", NamedTextColor.GRAY));
			Object v = labelValue[i + 1];
			c = c.append(v instanceof Component comp ? comp : Component.text(String.valueOf(v), NamedTextColor.WHITE));
		}
		return c;
	}

	public static void row(CommandSender s, String label, String value) {
		s.sendMessage(Component.text("   " + label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
	}

	static String primaryGroup(Player p) {
		try {
			org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> rsp =
					Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
			return rsp == null ? null : rsp.getProvider().getPrimaryGroup(p);
		} catch (Throwable t) {
			return null; // Vault not present on this server
		}
	}

	@SuppressWarnings("unused")
	private static World unused() { return null; }
}
