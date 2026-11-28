package io.github.runelynx.runicuniverse.fx;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunicFX player settings (Phase 2), shared across servers in rp_RunicFXPlayer:
 *   HideOthers - don't show me other players' effects (my own still show)
 *   MyOff      - don't play my event effects (keeps my event choices for later)
 */
public final class FxPrefs {

	private FxPrefs() {}

	private static final Set<UUID> HIDE = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> OFF = ConcurrentHashMap.newKeySet();

	public static boolean hidesOthers(UUID id) {
		return HIDE.contains(id);
	}

	public static boolean myOff(UUID id) {
		return OFF.contains(id);
	}

	/** Call off the main thread. */
	static void load(UUID id) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT HideOthers, MyOff FROM rp_RunicFXPlayer WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				boolean hide = false, off = false;
				if (rs.next()) {
					hide = rs.getInt(1) == 1;
					off = rs.getInt(2) == 1;
				}
				if (hide) HIDE.add(id); else HIDE.remove(id);
				if (off) OFF.add(id); else OFF.remove(id);
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("settings load failed: " + e.getMessage());
		}
	}

	static void forget(UUID id) {
		HIDE.remove(id);
		OFF.remove(id);
	}

	/** Change a setting now (in memory) and save it in the background. */
	static void set(Player p, boolean hideOthers, boolean myOff) {
		setFor(p.getUniqueId(), p.getName(), hideOthers, myOff);
	}

	/** Read a player's settings straight from the database: {hideOthers, myOff}. Call off the main thread. */
	static boolean[] read(UUID id) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT HideOthers, MyOff FROM rp_RunicFXPlayer WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) return new boolean[] { rs.getInt(1) == 1, rs.getInt(2) == 1 };
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("settings read failed: " + e.getMessage());
		}
		return new boolean[] { false, false };
	}

	/** Change anyone's settings (staff can do it for players, online or not). */
	static void setFor(UUID id, String name, boolean hideOthers, boolean myOff) {
		if (org.bukkit.Bukkit.getPlayer(id) != null) { // online here: takes effect now (other servers pick it up within a minute)
			if (hideOthers) HIDE.add(id); else HIDE.remove(id);
			if (myOff) OFF.add(id); else OFF.remove(id);
		}
		org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(io.github.runelynx.runicuniverse.RunicUniverse.getInstance(), () -> {
			try (Connection c = FxStore.db().openConnection();
				 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXPlayer (UUID, Name, HideOthers, MyOff) VALUES (?,?,?,?)"
						 + " ON DUPLICATE KEY UPDATE Name = VALUES(Name), HideOthers = VALUES(HideOthers), MyOff = VALUES(MyOff)")) {
				ps.setString(1, id.toString());
				ps.setString(2, name);
				ps.setInt(3, hideOthers ? 1 : 0);
				ps.setInt(4, myOff ? 1 : 0);
				ps.executeUpdate();
			} catch (SQLException | RuntimeException e) {
				FxStore.warn("settings save failed: " + e.getMessage());
			}
		});
	}

	/**
	 * Who should see an effect at this spot: null means "everyone" (nobody nearby hides effects, the cheap path).
	 * Otherwise the nearby players who don't hide them, plus the effect's owner, who always sees their own.
	 */
	static List<Player> viewers(Location at, Player owner) {
		if (HIDE.isEmpty() || at == null || at.getWorld() == null) return null;
		boolean anyHider = false;
		List<Player> l = new ArrayList<>();
		for (Player p : at.getWorld().getPlayers()) {
			if (p.getLocation().distanceSquared(at) > 128 * 128) continue;
			if (p != owner && HIDE.contains(p.getUniqueId())) {
				anyHider = true;
				continue;
			}
			l.add(p);
		}
		return anyHider ? l : null;
	}
}
