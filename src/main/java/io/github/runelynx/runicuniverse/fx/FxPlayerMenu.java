package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import io.github.runelynx.runicuniverse.staff.Gui;
import io.github.runelynx.runicuniverse.staff.Ui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * /runicfx (alias /fx): every player's RunicFX menu (Phase 2).
 *   My RunicFX     effects they have: preview, or give one to an event
 *   Locked         effects staff chose to show, with how to unlock them
 *   My events      which effect plays when they join, teleport, rank up, ...
 *   Settings       hide other players' effects; switch my own effects off
 */
public final class FxPlayerMenu implements CommandExecutor {

	/** Menu slots for up to 21 icons in a 5-row menu, inside a frame. */
	static final int[] SLOTS = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };

	private static final Map<UUID, Long> PREVIEWED = new HashMap<>();

	@Override
	public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
		if (!(s instanceof Player)) {
			s.sendMessage("RunicFX is a menu for players in game. Staff: /rs fx");
			return true;
		}
		main((Player) s, 0);
		return true;
	}

	static void msg(Player p, String text) {
		p.sendMessage(Ui.legacy("&b✧ &f" + text));
	}

	// ------------------------------------------------------------------ main

	static void main(Player p, int page) {
		List<FxDef> mine = FxGrants.owned(p);
		List<FxDef> locked = new ArrayList<>();
		for (FxDef d : FxStore.library()) if (d.enabled && d.shown && !mine.contains(d)) locked.add(d);
		List<FxDef> all = new ArrayList<>(mine);
		all.addAll(locked);
		int pages = Math.max(1, (all.size() + SLOTS.length - 1) / SLOTS.length);
		int pg = Math.max(0, Math.min(page, pages - 1));

		Gui g = new Gui(6, "&8RunicFX" + (pages > 1 ? " (" + (pg + 1) + "/" + pages + ")" : ""));
		g.set(4, Gui.item("NETHER_STAR", "&b&lRunicFX", "&7Particle effects that play when you", "&7join, teleport, rank up and more.",
				"", "&fYou have &b" + mine.size() + "&f effect" + (mine.size() == 1 ? "" : "s") + ".",
				locked.isEmpty() ? null : "&8" + locked.size() + " more to unlock."), null);
		for (int i = 0; i < SLOTS.length; i++) {
			int n = pg * SLOTS.length + i;
			if (n >= all.size()) break;
			FxDef d = all.get(n);
			if (mine.contains(d)) {
				List<String> lore = new ArrayList<>();
				lore.add("&7" + d.category);
				String how = FxGrants.how(p, d);
				if (how != null) lore.add("&8" + how);
				List<String> used = usedFor(p, d);
				if (!used.isEmpty()) lore.add("&aPlays on: &f" + String.join(", ", used));
				lore.add("");
				lore.add("&eLeft-click: preview it");
				lore.add("&eRight-click: use it for an event");
				g.set(SLOTS[i], Gui.item(d.icon, "&b" + d.display, lore), (pl, t) -> {
					if (t.isRightClick()) pickEvent(pl, d);
					else preview(pl, d, () -> main(pl, pg));
				});
			} else {
				List<String> lore = new ArrayList<>();
				lore.add("&7" + d.category + " &8· locked");
				lore.add("");
				if (d.unlock != null && !d.unlock.isEmpty()) {
					lore.add("&fHow to unlock:");
					for (String line : io.github.runelynx.runicuniverse.staff.StaffMenu.wrap(d.unlock, 34)) lore.add("&7" + line);
				} else {
					lore.add("&7Keep an eye out for ways to earn it!");
				}
				g.set(SLOTS[i], Gui.item("GRAY_DYE", "&8" + d.display, lore), null);
			}
		}
		if (all.isEmpty()) {
			g.set(22, Gui.item("BARRIER", "&7No effects yet", "&7Earn RunicFX by ranking up, exploring,", "&7finishing mazes, voting and events."), null);
		}
		if (pg > 0) g.set(45, Gui.item("ARROW", "&7« Previous page"), (pl, t) -> main(pl, pg - 1));
		g.set(48, Gui.item("CLOCK", "&f&lMy events", "&7Choose which effect plays when", "&7you join, teleport, rank up and more"), (pl, t) -> events(pl));
		g.set(50, Gui.item("COMPARATOR", "&f&lSettings", "&7Hide other players' effects,", "&7or switch your own off"), (pl, t) -> settings(pl));
		if (pg < pages - 1) g.set(53, Gui.item("ARROW", "&7Next page »"), (pl, t) -> main(pl, pg + 1));
		g.frameRow(0).frameRow(5);
		g.openLater(p);
	}

	static List<String> usedFor(Player p, FxDef d) {
		List<String> l = new ArrayList<>();
		for (Map.Entry<String, String> e : FxStore.bindings(p.getUniqueId()).entrySet()) {
			if (e.getValue().equals(d.key) && FxEvents.EVENTS.containsKey(e.getKey())) l.add(FxEvents.EVENTS.get(e.getKey()));
		}
		return l;
	}

	/** Play an effect on the player (5 s apart), then bring the menu back once it's done. */
	static void preview(Player p, FxDef d, Runnable reopen) {
		Long last = PREVIEWED.get(p.getUniqueId());
		long now = System.currentTimeMillis();
		if (last != null && now - last < 5000) {
			msg(p, "One preview every 5 seconds, please.");
			return;
		}
		if (FxZones.blocked(p.getLocation())) {
			msg(p, "Effects are switched off around here. Try somewhere else!");
			return;
		}
		PREVIEWED.put(p.getUniqueId(), now);
		p.closeInventory();
		FxEngine.play(d, p);
		long wait = Math.min((long) Math.ceil(FxEngine.lengthTicks(d) / 20.0) * 20 + 20, 20L * 30);
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
			if (p.isOnline() && p.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) reopen.run();
		}, wait);
	}

	// ------------------------------------------------------------------ events

	static void events(Player p) {
		Map<String, String> b = FxStore.bindings(p.getUniqueId());
		Gui g = new Gui(5, "&8My events");
		int i = 0;
		for (Map.Entry<String, String> e : FxEvents.EVENTS.entrySet()) {
			if (i >= SLOTS.length) break;
			FxDef d = b.containsKey(e.getKey()) ? FxStore.get(b.get(e.getKey())) : null;
			boolean usable = d != null && FxGrants.owns(p, d);
			List<String> lore = new ArrayList<>();
			lore.add("&7Plays: " + (d == null ? "&8nothing" : usable ? "&b" + d.display : "&c" + d.display + " (you don't have it now)"));
			if (FxEvents.SURVIVAL_ONLY.contains(e.getKey())) lore.add("&8Happens on Survival");
			lore.add("");
			lore.add("&eClick to choose");
			if (d != null) lore.add("&eRight-click to clear");
			g.set(SLOTS[i++], Gui.item(usable ? d.icon : "GRAY_DYE", "&f" + e.getValue(), lore), (pl, t) -> {
				if (t.isRightClick()) {
					FxStore.bind(pl.getUniqueId(), e.getKey(), null);
					events(pl);
				} else {
					pickFx(pl, e.getValue(), fx -> {
						FxStore.bind(pl.getUniqueId(), e.getKey(), fx.key);
						msg(pl, e.getValue() + " now plays " + fx.display + ".");
						events(pl);
					}, () -> events(pl));
				}
			});
		}
		g.set(36, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> main(pl, 0));
		if (FxPrefs.myOff(p.getUniqueId())) {
			g.set(40, Gui.item("RED_CONCRETE", "&cYour effects are switched off", "&7Switch them back on in Settings"), (pl, t) -> settings(pl));
		}
		g.frameRow(0).frameRow(4);
		g.openLater(p);
	}

	static void pickFx(Player p, String event, Consumer<FxDef> chosen, Runnable back) {
		List<FxDef> mine = FxGrants.owned(p);
		Gui g = new Gui(6, "&8" + event + ": pick an effect");
		int slot = 0;
		for (FxDef d : mine) {
			if (slot >= 45) break;
			g.set(slot++, Gui.item(d.icon, "&b" + d.display, "&7" + d.category, "", "&eClick to choose"), (pl, t) -> chosen.accept(d));
		}
		if (mine.isEmpty()) g.set(22, Gui.item("BARRIER", "&7You don't have any effects yet"), null);
		g.set(45, Gui.item("ARROW", "&7« Back"), (pl, t) -> back.run());
		g.frameRow(5);
		g.openLater(p);
	}

	static void pickEvent(Player p, FxDef d) {
		Gui g = new Gui(5, "&8Use " + d.display + " for...");
		int i = 0;
		Map<String, String> b = FxStore.bindings(p.getUniqueId());
		for (Map.Entry<String, String> e : FxEvents.EVENTS.entrySet()) {
			if (i >= SLOTS.length) break;
			boolean already = d.key.equals(b.get(e.getKey()));
			g.set(SLOTS[i++], Gui.item(already ? d.icon : "CLOCK", "&f" + e.getValue(),
					already ? "&aAlready plays " + d.display : "&eClick to use " + d.display,
					FxEvents.SURVIVAL_ONLY.contains(e.getKey()) ? "&8Happens on Survival" : null), (pl, t) -> {
				FxStore.bind(pl.getUniqueId(), e.getKey(), d.key);
				msg(pl, e.getValue() + " now plays " + d.display + ".");
				events(pl);
			});
		}
		g.set(36, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> main(pl, 0));
		g.frameRow(0).frameRow(4);
		g.openLater(p);
	}

	// ------------------------------------------------------------------ settings

	static void settings(Player p) {
		UUID id = p.getUniqueId();
		boolean hide = FxPrefs.hidesOthers(id), off = FxPrefs.myOff(id);
		Gui g = new Gui(3, "&8RunicFX settings");
		g.set(11, Gui.item(hide ? "ENDER_EYE" : "ENDER_PEARL", "&fOther players' effects: " + (hide ? "&chidden" : "&ashown"),
				"&7Hiding them can help on slower computers.", "&7Your own effects still show for you.", "", "&eClick to switch"), (pl, t) -> {
			FxPrefs.set(pl, !hide, off);
			settings(pl);
		});
		g.set(15, Gui.item(off ? "RED_CONCRETE" : "LIME_CONCRETE", "&fMy effects: " + (off ? "&coff" : "&aon"),
				"&7Off stops your event effects playing,", "&7but keeps your choices for later.", "", "&eClick to switch"), (pl, t) -> {
			FxPrefs.set(pl, hide, !off);
			settings(pl);
		});
		g.set(18, Gui.item("ARROW", "&7« RunicFX"), (pl, t) -> main(pl, 0));
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}
}
