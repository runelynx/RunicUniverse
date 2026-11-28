package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import io.github.runelynx.runicuniverse.staff.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Staff menus for Phase 2 (above helper): give / take effects, reward rules, the welcome-back reward and no-FX zones. */
final class FxStaffRewards {

	private FxStaffRewards() {}

	/** Run a database job in the background, then continue on the main thread. */
	static <T> void async(Supplier<T> job, Consumer<T> then) {
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> {
			T r = job.get();
			Bukkit.getScheduler().runTask(RunicUniverse.getInstance(), () -> then.accept(r));
		});
	}

	static void whole(Player p, String question, int min, int max, IntConsumer then) {
		FxPrompt.ask(p, question, txt -> {
			int v;
			try {
				v = Integer.parseInt(txt.trim());
			} catch (NumberFormatException e) {
				Ui.error(p, "That's not a whole number.");
				return;
			}
			if (v < min || v > max) {
				Ui.error(p, "Use " + min + " to " + max + ".");
				return;
			}
			then.accept(v);
		});
	}

	// ------------------------------------------------------------------ give / who has it

	static void give(Player p, FxDef d) {
		FxPrompt.ask(p, "Who gets " + d.display + "? (player name, online or offline)", name -> whole(p,
				"For how many days? (0 = forever)", 0, 3650, days -> async(() -> {
					Map.Entry<UUID, String> t = FxGrants.lookup(name.trim());
					if (t == null) return "?Nobody called " + name.trim() + " has played here.";
					boolean ok = FxGrants.grant(t.getKey(), t.getValue(), d.key, "staff (" + p.getName() + ")", p.getName(), days);
					return ok ? "Gave " + t.getValue() + " " + d.display + (days > 0 ? " for " + days + " days." : ".") + "|" + t.getValue()
							: "?" + t.getValue() + " already has " + d.display + ".";
				}, r -> {
					if (r.startsWith("?")) {
						Ui.error(p, r.substring(1));
					} else {
						String[] parts = r.split("\\|", 2);
						Audit.log(p, "fx grant", d.key + " to " + parts[1] + (days > 0 ? " (" + days + " days)" : ""));
						Ui.ok(p, parts[0]);
					}
					FxMenu.options(p, d);
				})));
	}

	static void owners(Player p, FxDef d, int page) {
		async(() -> FxGrants.owners(d.key), list -> {
			Gui g = new Gui(6, "&8Who has " + d.display);
			int per = 45, pages = Math.max(1, (list.size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
			for (int i = 0; i < per && pg * per + i < list.size(); i++) {
				Map.Entry<UUID, FxGrants.Grant> e = list.get(pg * per + i);
				FxGrants.Grant gr = e.getValue();
				String name = gr.name == null ? e.getKey().toString().substring(0, 8) : gr.name;
				g.set(i, Gui.head(Bukkit.getOfflinePlayer(e.getKey()), "&f" + name, Arrays.asList(
						"&7From " + (gr.source == null ? "?" : gr.source),
						"&7Since " + FxGrants.date(gr.at) + (gr.expires > 0 ? (gr.active() ? " · until " : " · &cended ") + FxGrants.date(gr.expires) : ""),
						"", "&eRight-click to take it away")), (pl, t) -> {
					if (!t.isRightClick()) return;
					Confirm.menu(pl, "Take " + d.display + " from " + name, null, () -> async(() -> FxGrants.revoke(e.getKey(), d.key), ok -> {
						if (ok) {
							Audit.log(pl, "fx revoke", d.key + " from " + name);
							Ui.ok(pl, "Took " + d.display + " from " + name + ".");
						}
						owners(pl, d, pg);
					}), () -> owners(pl, d, pg));
				});
			}
			if (list.isEmpty()) g.set(22, Gui.item("BARRIER", "&7Nobody has been given it yet"), null);
			g.set(45, Gui.item("ARROW", "&7« " + d.display), (pl, t) -> FxMenu.options(pl, d));
			if (pg > 0) g.set(48, Gui.item("ARROW", "&7« Previous page"), (pl, t) -> owners(pl, d, pg - 1));
			g.set(49, Gui.item("EMERALD", "&a&lGive to a player"), (pl, t) -> give(pl, d));
			if (pg < pages - 1) g.set(50, Gui.item("ARROW", "&7Next page »"), (pl, t) -> owners(pl, d, pg + 1));
			g.frameRow(5);
			g.openLater(p);
		});
	}

	// ------------------------------------------------------------------ rewards

	static void rewards(Player p) {
		async(() -> new Object[] { FxRewards.welcome(), FxRewards.rules() }, r -> {
			FxRewards.Welcome w = (FxRewards.Welcome) r[0];
			@SuppressWarnings("unchecked") List<FxRewards.Rule> rules = (List<FxRewards.Rule>) r[1];
			Gui g = new Gui(6, "&8RunicFX rewards");
			FxDef wfx = w.fx.isEmpty() ? null : FxStore.get(w.fx);
			String status = w.start == 0 ? "&7Not started" : w.running() ? "&aRunning until " + FxGrants.date(w.end())
					: System.currentTimeMillis() < w.start ? "&eStarts " + FxGrants.date(w.start) : "&7Ended " + FxGrants.date(w.end());
			g.set(4, Gui.item("CAKE", "&b&lWelcome-back reward", status,
					"&7Online " + w.minutes + " min within " + w.days + " days = 1 effect",
					"&7Effect: " + (wfx == null ? "&enot chosen yet (earned ones wait)" : "&b" + wfx.display),
					"&7Earned so far: &f" + w.earned + (w.waiting > 0 ? " &8(" + w.waiting + " waiting for the effect)" : ""),
					"", "&eClick to manage"), (pl, t) -> welcome(pl));
			int slot = 9;
			for (FxRewards.Rule rule : rules) {
				if (slot >= 45) break;
				FxDef d = FxStore.get(rule.fxKey);
				g.set(slot++, Gui.item(d == null ? "BARRIER" : d.icon, "&f" + FxRewards.describe(rule),
						"&7Gives: " + (d == null ? "&c" + rule.fxKey + " (deleted)" : "&b" + d.display),
						rule.createdBy == null ? null : "&8Added by " + rule.createdBy, "", "&eRight-click to remove"), (pl, t) -> {
					if (!t.isRightClick()) return;
					Confirm.menu(pl, "Remove the rule: " + FxRewards.describe(rule), Collections.singletonList("&7Players keep effects they already got."),
							() -> async(() -> FxRewards.removeRule(rule.id), ok -> {
								if (ok) Audit.log(pl, "fx rule remove", FxRewards.describe(rule) + " -> " + rule.fxKey);
								rewards(pl);
							}), () -> rewards(pl));
				});
			}
			g.set(45, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> FxMenu.main(pl));
			g.set(49, Gui.item("LIME_DYE", "&a&lAdd a reward rule", "&7e.g. reach Master → get an effect.", "&7Players who already qualify get it", "&7next time they join."),
					(pl, t) -> addRule(pl));
			g.set(51, Gui.item("BOOK", "&fOther ways to give effects", "&7Crates, votes, Tebex and raffles can", "&7run this console command:",
					"&f/rs fx grant <player> <fx> [days]", "&7and staff can use Give to a player."), null);
			g.frameRow(5);
			g.openLater(p);
		});
	}

	static void addRule(Player p) {
		Gui g = new Gui(3, "&8New rule: what earns it?");
		g.set(11, Gui.item("GOLDEN_HELMET", "&fReach a rank", "&7" + String.join(", ", FxRewards.LADDER.subList(1, FxRewards.LADDER.size()))),
				(pl, t) -> FxPrompt.ask(pl, "Which rank? (e.g. Master; reaching a higher one counts too)", rank -> pickRuleFx(pl, "RANK", rank.trim().toLowerCase(Locale.ROOT))));
		g.set(13, Gui.item("COMPASS", "&fExplorer's League finds"),
				(pl, t) -> whole(pl, "How many EL locations found?", 1, 10000, n -> pickRuleFx(pl, "EL_FINDS", String.valueOf(n))));
		g.set(15, Gui.item("LEAD", "&fMazes & parkours finished"),
				(pl, t) -> whole(pl, "How many different mazes / parkours finished?", 1, 1000, n -> pickRuleFx(pl, "MAZES", String.valueOf(n))));
		g.set(18, Gui.item("ARROW", "&7« Rewards"), (pl, t) -> rewards(pl));
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}

	static void pickRuleFx(Player p, String route, String value) {
		FxMenu.pickFx(p, "&8Which effect does it give?", d -> async(() -> FxRewards.addRule(route, value, d.key, p.getName()), ok -> {
			FxRewards.Rule r = new FxRewards.Rule();
			r.route = route;
			r.value = value;
			if (ok) {
				Audit.log(p, "fx rule add", FxRewards.describe(r) + " -> " + d.key);
				Ui.ok(p, "Added: " + FxRewards.describe(r) + " gives " + d.display + ".");
				for (Player online : Bukkit.getOnlinePlayers()) FxRewards.checkLater(online, 20);
			} else {
				Ui.error(p, "Couldn't save that rule.");
			}
			rewards(p);
		}), () -> rewards(p));
	}

	static void welcome(Player p) {
		async(FxRewards::welcome, w -> {
			Gui g = new Gui(3, "&8Welcome-back reward");
			FxDef wfx = w.fx.isEmpty() ? null : FxStore.get(w.fx);
			g.set(10, Gui.item(w.running() ? "LIME_CONCRETE" : "CLOCK", w.start == 0 ? "&a&lStart it now" : "&fStarted " + FxGrants.date(w.start),
					w.start == 0 ? "&7Start at the grand reopening." : w.running() ? "&aRunning until " + FxGrants.date(w.end()) : "&7Ends/ended " + FxGrants.date(w.end()),
					"&eClick to " + (w.start == 0 ? "start" : "restart from now"), w.start == 0 ? null : "&eRight-click to stop it"), (pl, t) -> {
				if (t.isRightClick() && w.start != 0) {
					Confirm.menu(pl, "Stop the welcome-back reward", Collections.singletonList("&7Players keep what they already earned."),
							() -> async(() -> { FxRewards.setSetting("welcome.start", "0"); return true; }, ok -> {
								Audit.log(pl, "fx welcome stop", "");
								welcome(pl);
							}), () -> welcome(pl));
				} else {
					Confirm.menu(pl, (w.start == 0 ? "Start" : "Restart") + " the welcome-back reward now",
							Arrays.asList("&7Runs for " + w.days + " days from now.", "&7Online time already counted is kept."),
							() -> async(() -> { FxRewards.setSetting("welcome.start", String.valueOf(System.currentTimeMillis())); return true; }, ok -> {
								Audit.log(pl, "fx welcome start", w.days + " days, " + w.minutes + " min");
								welcome(pl);
							}), () -> welcome(pl));
				}
			});
			g.set(12, Gui.item("CLOCK", "&fDays it runs: &e" + w.days, "&eClick to change"), (pl, t) -> whole(pl, "How many days should it run?", 1, 90,
					n -> async(() -> { FxRewards.setSetting("welcome.days", String.valueOf(n)); return true; }, ok -> welcome(pl))));
			g.set(13, Gui.item("COMPASS", "&fMinutes online needed: &e" + w.minutes, "&eClick to change"), (pl, t) -> whole(pl, "How many minutes online?", 1, 600,
					n -> async(() -> { FxRewards.setSetting("welcome.minutes", String.valueOf(n)); return true; }, ok -> welcome(pl))));
			g.set(14, Gui.item(wfx == null ? "GRAY_DYE" : wfx.icon, "&fEffect: " + (wfx == null ? "&enot chosen yet" : "&b" + wfx.display),
					"&7Earned so far: &f" + w.earned, w.waiting > 0 ? "&e" + w.waiting + " waiting: they get it the moment you pick" : null,
					"&eClick to choose"), (pl, t) -> FxMenu.pickFx(pl, "&8Welcome-back effect", d ->
					Confirm.menu(pl, "Make " + d.display + " the welcome-back reward",
							Collections.singletonList(w.waiting > 0 ? "&e" + w.waiting + " player(s) get it right away." : "&7Everyone who earns it gets this."),
							() -> async(() -> FxRewards.setWelcomeFx(d), n -> {
								Audit.log(pl, "fx welcome effect", d.key + (n > 0 ? " (given to " + n + " waiting)" : ""));
								Ui.ok(pl, "Welcome-back reward: " + d.display + (n > 0 ? ". Given to " + n + " player(s) who'd earned it." : "."));
								welcome(pl);
							}), () -> welcome(pl)), () -> welcome(pl)));
			g.set(18, Gui.item("ARROW", "&7« Rewards"), (pl, t) -> rewards(pl));
			g.frameRow(0).frameRow(2);
			g.openLater(p);
		});
	}

	// ------------------------------------------------------------------ zones

	/** First corner of a box zone being made, per staff member. */
	private static final Map<UUID, Location> CORNER = new HashMap<>();

	static void zones(Player p) {
		Gui g = new Gui(6, "&8RunicFX zones on " + RunicUniverse.SERVER_NAME);
		int slot = 0;
		for (FxZones.Zone z : FxZones.all()) {
			if (slot >= 45) break;
			g.set(slot++, Gui.item(z.allow ? "LIME_STAINED_GLASS" : z.radius < 0 ? "GRASS_BLOCK" : "BARRIER",
					(z.allow ? "&aAllow: &f" : "&cBlock: &f") + (z.name == null ? "Zone " + z.id : z.name), "&7" + z.where(),
					z.createdBy == null ? null : "&8Added by " + z.createdBy, "", "&eClick for options"), (pl, t) -> zone(pl, z));
		}
		Location c1 = CORNER.get(p.getUniqueId());
		g.set(45, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> FxMenu.main(pl));
		g.set(47, Gui.item("SLIME_BALL", "&a&lAdd a zone around me", "&7A sphere with the radius you choose"),
				(pl, t) -> pickMode(pl, allow -> FxPrompt.ask(pl, "Name for this zone (e.g. Hide and Seek arena)", name ->
						whole(pl, "Radius in blocks?", 1, 500, r -> {
							Location at = pl.getLocation();
							addZone(pl, name.trim(), at, null, r, allow);
						}))));
		g.set(48, Gui.item("STRUCTURE_VOID", c1 == null ? "&a&lAdd a box zone" : "&a&lFinish the box zone",
				c1 == null ? "&7Stand at one corner and click," : "&7Corner 1: " + c1.getBlockX() + ", " + c1.getBlockY() + ", " + c1.getBlockZ(),
				c1 == null ? "&7then walk to the opposite corner" : "&7Now stand at the opposite corner and click.",
				c1 == null ? "&7and click again." : "&eRight-click to start over"), (pl, t) -> {
			Location first = CORNER.get(pl.getUniqueId());
			if (first == null || t.isRightClick()) {
				CORNER.put(pl.getUniqueId(), pl.getLocation().getBlock().getLocation());
				Ui.ok(pl, "Corner 1 set. Walk to the opposite corner, then /rs fx → Zones → Finish the box zone. (Heights count too: use the lowest and highest points.)");
				pl.closeInventory();
				return;
			}
			if (first.getWorld() != pl.getWorld()) {
				Ui.error(pl, "Both corners must be in the same world.");
				CORNER.remove(pl.getUniqueId());
				return;
			}
			Location second = pl.getLocation().getBlock().getLocation();
			pickMode(pl, allow -> FxPrompt.ask(pl, "Name for this zone", name -> {
				CORNER.remove(pl.getUniqueId());
				addZone(pl, name.trim(), first, second, 0, allow);
			}));
		});
		g.set(50, Gui.item("GRASS_BLOCK", "&fBlock this whole world", "&7" + p.getWorld().getName()),
				(pl, t) -> Confirm.menu(pl, "Block players' effects in all of " + pl.getWorld().getName(), null,
						() -> addZone(pl, pl.getWorld().getName() + " (whole world)", pl.getLocation(), null, -1, false), () -> zones(pl)));
		FxZones.Zone here = FxZones.at(p.getLocation());
		g.set(53, Gui.item("BOOK", "&fHow zones work",
				"&cBlock&7: players' event effects never play inside.",
				"&aAllow&7: in a world with allow zones, effects",
				"&7play only inside them (the rest of that world",
				"&7is blocked). Other worlds aren't affected.",
				"&7Staff can still play effects anywhere.",
				"&7Zones are per server.", "",
				"&fRight here: " + (FxZones.blocked(p.getLocation()) ? "&ceffects blocked" : "&aeffects allowed")
						+ (here != null ? " &8(" + here.name + ")" : "")), null);
		g.frameRow(5);
		g.openLater(p);
	}

	static void pickMode(Player p, Consumer<Boolean> chosen) {
		Gui g = new Gui(3, "&8Block or allow?");
		g.set(11, Gui.item("BARRIER", "&c&lBlock zone", "&7Effects never play inside"), (pl, t) -> chosen.accept(false));
		g.set(15, Gui.item("LIME_STAINED_GLASS", "&a&lAllow zone", "&7In this world, effects play", "&7only inside allow zones"), (pl, t) -> chosen.accept(true));
		g.set(18, Gui.item("ARROW", "&7« Zones"), (pl, t) -> zones(pl));
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}

	static void addZone(Player p, String name, Location at, Location corner2, int radius, boolean allow) {
		async(() -> FxZones.add(name, at, corner2, radius, allow, p.getName()), ok -> {
			if (ok) {
				Audit.log(p, "fx zone add", (allow ? "allow " : "block ") + name + " (" + at.getWorld().getName() + " " + at.getBlockX() + ","
						+ at.getBlockY() + "," + at.getBlockZ() + (corner2 != null ? " to " + corner2.getBlockX() + "," + corner2.getBlockY() + ","
						+ corner2.getBlockZ() : radius < 0 ? " whole world" : " r" + radius) + ")");
				Ui.ok(p, "Zone added: " + name + ".");
			} else {
				Ui.error(p, "Couldn't save that zone.");
			}
			zones(p);
		});
	}

	static void zone(Player p, FxZones.Zone z) {
		String label = z.name == null ? "Zone " + z.id : z.name;
		Gui g = new Gui(3, "&8" + label);
		g.set(4, Gui.item(z.allow ? "LIME_STAINED_GLASS" : "BARRIER", (z.allow ? "&aAllow: &f" : "&cBlock: &f") + label, "&7" + z.where()), null);
		g.set(11, Gui.item("ENDER_PEARL", "&fGo there", z.radius < 0 ? "&8(whole world: goes to its spawn)" : null), (pl, t) -> {
			org.bukkit.World w = Bukkit.getWorld(z.world);
			if (w == null) {
				Ui.error(pl, z.world + " isn't loaded.");
				return;
			}
			pl.closeInventory();
			pl.teleport(z.radius < 0 ? w.getSpawnLocation() : z.box
					? new Location(w, (z.x + z.x2) / 2 + 0.5, Math.max(z.y, z.y2), (z.z + z.z2) / 2 + 0.5)
					: new Location(w, z.x, z.y, z.z));
		});
		g.set(13, Gui.item(z.allow ? "BARRIER" : "LIME_STAINED_GLASS", "&fSwitch to " + (z.allow ? "&cblock" : "&aallow"), "&eClick to switch"),
				(pl, t) -> Confirm.menu(pl, "Make " + label + " a" + (z.allow ? " block" : "n allow") + " zone", null,
						() -> async(() -> FxZones.setMode(z.id, !z.allow), ok -> {
							if (ok) Audit.log(pl, "fx zone mode", label + " -> " + (z.allow ? "block" : "allow"));
							zones(pl);
						}), () -> zone(pl, z)));
		g.set(15, Gui.item("TNT", "&cRemove"), (pl, t) -> Confirm.menu(pl, "Remove the zone " + label, null,
				() -> async(() -> FxZones.remove(z.id), ok -> {
					if (ok) Audit.log(pl, "fx zone remove", label + " (" + z.where() + ")");
					zones(pl);
				}), () -> zone(pl, z)));
		g.set(18, Gui.item("ARROW", "&7« Zones"), (pl, t) -> zones(pl));
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}
}
