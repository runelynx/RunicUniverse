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

	// ------------------------------------------------------------------ no-FX zones

	static void zones(Player p) {
		Gui g = new Gui(6, "&8No-FX zones on " + RunicUniverse.SERVER_NAME);
		int slot = 0;
		for (FxZones.Zone z : FxZones.all()) {
			if (slot >= 45) break;
			g.set(slot++, Gui.item(z.radius < 0 ? "GRASS_BLOCK" : "BARRIER", "&f" + (z.name == null ? "Zone " + z.id : z.name), "&7" + z.where(),
					z.createdBy == null ? null : "&8Added by " + z.createdBy, "", "&eLeft-click: go there", "&eRight-click: remove"), (pl, t) -> {
				if (t.isRightClick()) {
					Confirm.menu(pl, "Remove the no-FX zone " + (z.name == null ? z.id : z.name), null,
							() -> async(() -> FxZones.remove(z.id), ok -> {
								if (ok) Audit.log(pl, "fx zone remove", z.name + " (" + z.where() + ")");
								zones(pl);
							}), () -> zones(pl));
				} else if (z.radius >= 0 && Bukkit.getWorld(z.world) != null) {
					pl.closeInventory();
					pl.teleport(new Location(Bukkit.getWorld(z.world), z.x, z.y, z.z));
				}
			});
		}
		FxZones.Zone here = FxZones.at(p.getLocation());
		g.set(45, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> FxMenu.main(pl));
		g.set(48, Gui.item("LIME_DYE", "&a&lAdd a zone around me", "&7Players' event effects won't play", "&7within the radius you choose.",
				here != null ? "&8You're already inside: " + here.name : null), (pl, t) -> FxPrompt.ask(pl, "Name for this zone (e.g. Hide and Seek arena)", name ->
				whole(pl, "Radius in blocks?", 1, 500, r -> {
					Location at = pl.getLocation();
					async(() -> FxZones.add(name.trim(), at, r, pl.getName()), ok -> {
						if (ok) Audit.log(pl, "fx zone add", name.trim() + " (" + at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " r" + r + ")");
						zones(pl);
					});
				})));
		g.set(50, Gui.item("GRASS_BLOCK", "&fBlock this whole world", "&7" + p.getWorld().getName()),
				(pl, t) -> Confirm.menu(pl, "Block players' effects in all of " + pl.getWorld().getName(), null, () -> {
					Location at = pl.getLocation();
					async(() -> FxZones.add(at.getWorld().getName() + " (whole world)", at, -1, pl.getName()), ok -> {
						if (ok) Audit.log(pl, "fx zone add", at.getWorld().getName() + " (whole world)");
						zones(pl);
					});
				}, () -> zones(pl)));
		g.set(53, Gui.item("BOOK", "&fAbout zones", "&7Only players' event effects are blocked.", "&7Staff can still play effects anywhere.",
				"&7Zones are per server."), null);
		g.frameRow(5);
		g.openLater(p);
	}
}
