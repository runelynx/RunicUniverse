package io.github.runelynx.runicuniverse.staff;

import io.github.runelynx.runicuniverse.MySQL;
import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-staff /rs menu preferences, stored in rp_StaffMenuPrefs so they follow the staff member across all servers.
 * "Show commands": whether menus show the equivalent typed command (e.g. "/rs souls <player> <amount>",
 * "(same as /staff gs)"). Off by default; each staff member can turn it on for themselves from the main /rs menu.
 */
public final class StaffPrefs {

	private StaffPrefs() {}

	private static final Map<UUID, Boolean> SHOW = new ConcurrentHashMap<>();

	public static void init(Plugin plugin) {
		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onQuit(PlayerQuitEvent e) {
				SHOW.remove(e.getPlayer().getUniqueId());
			}
		}, plugin);
	}

	/** Console always sees commands; players see them only if they turned the option on. */
	public static boolean showCommands(CommandSender s) {
		if (!(s instanceof Player)) {
			return true;
		}
		return SHOW.computeIfAbsent(((Player) s).getUniqueId(), StaffPrefs::load);
	}

	/** The line itself when commands are shown, otherwise null (menu item builders skip null lines). */
	public static String cmd(CommandSender s, String line) {
		return showCommands(s) ? line : null;
	}

	/** Removes "(same as /...)" and "(runs ... /...)" notes from a description when commands are hidden. */
	public static String text(CommandSender s, String description) {
		if (description == null || showCommands(s)) {
			return description;
		}
		return description.replaceAll("\\s*\\((?:same as|runs)[^)]*\\)", "").trim();
	}

	public static void setShowCommands(Player p, boolean show) {
		SHOW.put(p.getUniqueId(), show);
		UUID id = p.getUniqueId();
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> {
			try (Connection c = db().openConnection();
				 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_StaffMenuPrefs (UUID, ShowCommands, UpdatedAt) VALUES (?, ?, ?)"
						 + " ON DUPLICATE KEY UPDATE ShowCommands = VALUES(ShowCommands), UpdatedAt = VALUES(UpdatedAt)")) {
				ps.setString(1, id.toString());
				ps.setInt(2, show ? 1 : 0);
				ps.setLong(3, System.currentTimeMillis());
				ps.executeUpdate();
			} catch (SQLException | RuntimeException e) {
				RunicUniverse.getInstance().getLogger().warning("[rs] couldn't save menu preference: " + e.getMessage());
			}
		});
	}

	private static boolean load(UUID id) {
		try (Connection c = db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT ShowCommands FROM rp_StaffMenuPrefs WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() && rs.getInt(1) == 1;
			}
		} catch (SQLException | RuntimeException e) {
			return false; // default: hidden
		}
	}

	private static MySQL db() {
		org.bukkit.plugin.Plugin ru = RunicUniverse.getInstance();
		return new MySQL(ru, ru.getConfig().getString("dbHost"), ru.getConfig().getString("dbPort"),
				ru.getConfig().getString("dbDatabase"), ru.getConfig().getString("dbUser"), ru.getConfig().getString("dbPassword"));
	}
}
