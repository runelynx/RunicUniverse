package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.MySQL;
import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** RunicFX storage: the shared FX library, per-player event assignments and network presence (all servers share it). */
public final class FxStore {

	private FxStore() {}

	private static final Map<String, FxDef> LIBRARY = new ConcurrentHashMap<>();
	private static volatile long loadedAt = 0;
	private static final Map<UUID, Map<String, String>> BINDINGS = new ConcurrentHashMap<>();

	static MySQL db() {
		Plugin ru = RunicUniverse.getInstance();
		return new MySQL(ru, ru.getConfig().getString("dbHost"), ru.getConfig().getString("dbPort"),
				ru.getConfig().getString("dbDatabase"), ru.getConfig().getString("dbUser"), ru.getConfig().getString("dbPassword"));
	}

	static void warn(String m) {
		RunicUniverse.getInstance().getLogger().warning("[RunicFX] " + m);
	}

	// ------------------------------------------------------------------ library

	/** The library, refreshed from the database at most every 30 s (edits made on another server show up soon). */
	public static Collection<FxDef> library() {
		if (System.currentTimeMillis() - loadedAt > 30_000) reload();
		List<FxDef> l = new ArrayList<>(LIBRARY.values());
		l.sort(Comparator.comparing((FxDef d) -> d.category).thenComparing(d -> d.display.toLowerCase(Locale.ROOT)));
		return l;
	}

	public static FxDef get(String key) {
		if (key == null) return null;
		library();
		return LIBRARY.get(key.toLowerCase(Locale.ROOT));
	}

	public static synchronized void reload() {
		Map<String, FxDef> fresh = new HashMap<>();
		try (Connection c = db().openConnection(); Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT * FROM rp_RunicFX")) {
			while (rs.next()) {
				FxDef d = new FxDef();
				d.key = rs.getString("FxKey");
				d.display = rs.getString("DisplayName");
				d.icon = rs.getString("Icon");
				d.category = rs.getString("Category");
				try {
					d.type = FxDef.Type.valueOf(rs.getString("FxType"));
				} catch (IllegalArgumentException e) {
					d.type = FxDef.Type.RECIPE;
				}
				d.parse(rs.getString("Definition"));
				d.enabled = rs.getInt("Enabled") == 1;
				d.createdBy = rs.getString("CreatedBy");
				d.updatedBy = rs.getString("UpdatedBy");
				d.updatedAt = rs.getLong("UpdatedAt");
				fresh.put(d.key, d);
			}
		} catch (SQLException | RuntimeException e) {
			warn("library load failed: " + e.getMessage());
			loadedAt = System.currentTimeMillis();
			return;
		}
		if (fresh.isEmpty()) {
			FxDef seed = FxDef.confettiRain();
			seed.createdBy = "RunicFX";
			if (save(seed, "RunicFX")) fresh.put(seed.key, seed);
		}
		LIBRARY.clear();
		LIBRARY.putAll(fresh);
		loadedAt = System.currentTimeMillis();
	}

	public static boolean save(FxDef d, String by) {
		d.updatedBy = by;
		d.updatedAt = System.currentTimeMillis();
		if (d.createdBy == null) d.createdBy = by;
		try (Connection c = db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFX (FxKey, DisplayName, Icon, Category, FxType, Definition, Enabled, CreatedBy, UpdatedBy, UpdatedAt)"
					 + " VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE DisplayName=VALUES(DisplayName), Icon=VALUES(Icon), Category=VALUES(Category),"
					 + " FxType=VALUES(FxType), Definition=VALUES(Definition), Enabled=VALUES(Enabled), UpdatedBy=VALUES(UpdatedBy), UpdatedAt=VALUES(UpdatedAt)")) {
			ps.setString(1, d.key);
			ps.setString(2, d.display);
			ps.setString(3, d.icon);
			ps.setString(4, d.category);
			ps.setString(5, d.type.name());
			ps.setString(6, d.definition());
			ps.setInt(7, d.enabled ? 1 : 0);
			ps.setString(8, d.createdBy);
			ps.setString(9, by);
			ps.setLong(10, d.updatedAt);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			warn("save failed: " + e.getMessage());
			return false;
		}
		LIBRARY.put(d.key, d);
		return true;
	}

	public static boolean delete(String key) {
		try (Connection c = db().openConnection()) {
			try (PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFX WHERE FxKey = ?")) {
				ps.setString(1, key);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXBindings WHERE FxKey = ?")) {
				ps.setString(1, key);
				ps.executeUpdate();
			}
		} catch (SQLException | RuntimeException e) {
			warn("delete failed: " + e.getMessage());
			return false;
		}
		LIBRARY.remove(key);
		for (Map<String, String> m : BINDINGS.values()) m.values().removeIf(k -> k.equals(key));
		return true;
	}

	/** A free key derived from a display name, e.g. "Ember Burst" -> ember_burst (ember_burst_2 if taken). */
	public static String newKey(String display) {
		String base = display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		if (base.isEmpty()) base = "fx";
		if (base.length() > 34) base = base.substring(0, 34);
		String k = base;
		int i = 2;
		while (get(k) != null) k = base + "_" + i++;
		return k;
	}

	// ------------------------------------------------------------------ event assignments

	public static Map<String, String> bindings(UUID id) {
		return BINDINGS.computeIfAbsent(id, FxStore::loadBindings);
	}

	private static Map<String, String> loadBindings(UUID id) {
		Map<String, String> m = new ConcurrentHashMap<>();
		try (Connection c = db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT EventKey, FxKey FROM rp_RunicFXBindings WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) m.put(rs.getString(1), rs.getString(2));
			}
		} catch (SQLException | RuntimeException e) {
			warn("bindings load failed: " + e.getMessage());
		}
		return m;
	}

	public static void preload(UUID id) {
		BINDINGS.put(id, loadBindings(id));
	}

	public static void forget(UUID id) {
		BINDINGS.remove(id);
	}

	public static boolean bind(UUID id, String event, String fxKey) {
		try (Connection c = db().openConnection()) {
			if (fxKey == null) {
				try (PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXBindings WHERE UUID = ? AND EventKey = ?")) {
					ps.setString(1, id.toString());
					ps.setString(2, event);
					ps.executeUpdate();
				}
			} else {
				try (PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXBindings (UUID, EventKey, FxKey) VALUES (?,?,?) ON DUPLICATE KEY UPDATE FxKey = VALUES(FxKey)")) {
					ps.setString(1, id.toString());
					ps.setString(2, event);
					ps.setString(3, fxKey);
					ps.executeUpdate();
				}
			}
		} catch (SQLException | RuntimeException e) {
			warn("bind failed: " + e.getMessage());
			return false;
		}
		Map<String, String> m = bindings(id);
		if (fxKey == null) m.remove(event);
		else m.put(event, fxKey);
		return true;
	}

	// ------------------------------------------------------------------ presence (network join vs server switch)

	/** Milliseconds since this player last left any server, or -1 if unknown. */
	public static long sinceLastQuit(UUID id) {
		try (Connection c = db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT LastQuit, Server FROM rp_RunicFXPresence WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				// A server switch always lands on a different server; coming back to the one you just left is a reconnect.
				if (rs.next() && !RunicUniverse.SERVER_NAME.equalsIgnoreCase(rs.getString(2))) return System.currentTimeMillis() - rs.getLong(1);
			}
		} catch (SQLException | RuntimeException e) {
			warn("presence read failed: " + e.getMessage());
		}
		return -1;
	}

	public static void markQuit(UUID id) {
		try (Connection c = db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXPresence (UUID, LastQuit, Server) VALUES (?,?,?) ON DUPLICATE KEY UPDATE LastQuit = VALUES(LastQuit), Server = VALUES(Server)")) {
			ps.setString(1, id.toString());
			ps.setLong(2, System.currentTimeMillis());
			ps.setString(3, RunicUniverse.SERVER_NAME);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			warn("presence write failed: " + e.getMessage());
		}
	}
}
