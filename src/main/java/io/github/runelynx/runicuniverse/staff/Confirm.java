package io.github.runelynx.runicuniverse.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Confirmations for anything destructive or large. Typed commands get a chat line with clickable [Confirm] / [Cancel];
 * anything started from the staff menu gets a Yes / No menu instead. Console senders are trusted and run straight away.
 * Every confirmed action answers "✔ Done: ...". Requests expire after 60 seconds and only their starter can confirm.
 */
public final class Confirm {

	private Confirm() {}

	private static final class Pending {
		final UUID who; final Runnable action; final long expires; final String what;
		Pending(UUID who, Runnable action, String what) {
			this.who = who; this.action = action; this.what = what; this.expires = System.currentTimeMillis() + 60_000;
		}
	}

	private static final Map<String, Pending> PENDING = new HashMap<>();
	/** Recently finished ids, so a second click says "already done" rather than "expired". */
	private static final Map<String, String> DONE = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 200; }
	};

	/** True while an action runs because of a staff-menu click (set by StaffMenu), so ask() uses the Yes / No menu. */
	static boolean fromMenu = false;

	/** Ask in chat, or with the Yes / No menu when the request came from the staff menu. */
	public static void ask(CommandSender sender, String what, Runnable action) {
		if (!(sender instanceof Player p)) {
			action.run();
			return;
		}
		if (fromMenu) {
			menu(p, what, null, action, null);
			return;
		}
		purge();
		String id = Long.toString(System.nanoTime() & 0xffffff, 36);
		PENDING.put(id, new Pending(p.getUniqueId(), action, what));
		Ui.send(p, Component.text("Confirm: ", NamedTextColor.GOLD).append(Component.text(what, NamedTextColor.WHITE)));
		p.sendMessage(Component.text("     ")
				.append(Ui.button("✔ Confirm", NamedTextColor.GREEN, "/rs confirm " + id, "Do it"))
				.append(Component.text("   "))
				.append(Ui.button("✘ Cancel", NamedTextColor.RED, "/rs cancel " + id, "Don't do it"))
				.append(Component.text("   (expires in 60s)", NamedTextColor.DARK_GRAY)));
	}

	/** Ask with a Yes / No menu. */
	public static void menu(Player p, String what, List<String> details, Runnable action, Runnable back) {
		Gui g = new Gui(3, "&8Confirm: &0" + (what.length() > 24 ? what.substring(0, 24) + "…" : what));
		List<String> lore = new ArrayList<>();
		lore.add("&7" + what);
		if (details != null) {
			lore.addAll(details);
		}
		g.set(4, Gui.item("PAPER", "&f" + what, lore), null);
		g.set(11, Gui.item("LIME_CONCRETE", "&a&l✔ Yes, do it", "&7" + what), (pl, t) -> {
			pl.closeInventory();
			runAndReport(pl, what, action);
		});
		g.set(15, Gui.item("RED_CONCRETE", "&c&l✘ No, go back"), (pl, t) -> {
			if (back != null) {
				back.run();
			} else {
				pl.closeInventory();
				Ui.info(pl, "Cancelled: " + what);
			}
		});
		g.frameRow(0).frameRow(1).frameRow(2);
		g.openLater(p);
	}

	private static void runAndReport(CommandSender s, String what, Runnable action) {
		boolean was = fromMenu;
		fromMenu = false; // anything the action asks for itself goes to chat
		try {
			action.run();
			Ui.ok(s, "✔ Done: " + what);
		} catch (RuntimeException e) {
			Ui.error(s, "That failed: " + e.getMessage());
		} finally {
			fromMenu = was;
		}
	}

	static void handle(CommandSender sender, String id, boolean yes) {
		purge();
		Pending pd = PENDING.get(id);
		if (pd == null || !(sender instanceof Player p) || !p.getUniqueId().equals(pd.who)) {
			String done = DONE.get(id);
			if (done != null) {
				Ui.info(sender, "Already handled: " + done);
			} else {
				Ui.error(sender, "That confirmation has expired or isn't yours.");
			}
			return;
		}
		PENDING.remove(id);
		DONE.put(id, pd.what);
		if (yes) {
			runAndReport(sender, pd.what, pd.action);
		} else {
			Ui.info(sender, "Cancelled: " + pd.what);
		}
	}

	private static void purge() {
		long now = System.currentTimeMillis();
		PENDING.values().removeIf(pd -> pd.expires < now);
	}
}
