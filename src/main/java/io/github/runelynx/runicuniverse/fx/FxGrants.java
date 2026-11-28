package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import io.github.runelynx.runicuniverse.staff.Ui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has which RunicFX (Phase 2). A player can use an effect when it's switched on and any of these is true:
 *   - they're staff (helper and up have every effect)
 *   - they have the permission runicfx.fx.<key> (or runicfx.fx.*), e.g. for a whole rank or donor group in LuckPerms
 *   - they were granted it (rp_RunicFXGrants): by staff, a reward rule, the welcome-back reward, or a console
 *     command from crates / votes / Tebex (/rs fx grant <player> <fx> [days]). Grants can expire.
 */
public final class FxGrants {

	private FxGrants() {}

	public static final class Grant {
		public String key, name, source, by;
		public long at, expires; // expires 0 = never
		public boolean seen;

		public boolean active() {
			return expires == 0 || expires > System.currentTimeMillis();
		}
	}

	private static final Map<UUID, Map<String, Grant>> CACHE = new ConcurrentHashMap<>();

	// ------------------------------------------------------------------ tables

	/** Creates the Phase 2 tables if they're missing. Call off the main thread. */
	static void createTables() {
		String[] ddl = {
				"CREATE TABLE IF NOT EXISTS rp_RunicFXGrants (UUID VARCHAR(36) NOT NULL, FxKey VARCHAR(40) NOT NULL, Name VARCHAR(16),"
						+ " Source VARCHAR(80), GrantedBy VARCHAR(45), GrantedAt BIGINT NOT NULL, ExpiresAt BIGINT NOT NULL DEFAULT 0,"
						+ " Seen TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (UUID, FxKey), KEY (FxKey))",
				"CREATE TABLE IF NOT EXISTS rp_RunicFXPlayer (UUID VARCHAR(36) NOT NULL PRIMARY KEY, Name VARCHAR(16),"
						+ " HideOthers TINYINT NOT NULL DEFAULT 0, MyOff TINYINT NOT NULL DEFAULT 0)",
				"CREATE TABLE IF NOT EXISTS rp_RunicFXZones (Id INT AUTO_INCREMENT PRIMARY KEY, Server VARCHAR(30) NOT NULL, World VARCHAR(64) NOT NULL,"
						+ " X DOUBLE, Y DOUBLE, Z DOUBLE, Radius INT NOT NULL, Name VARCHAR(60), CreatedBy VARCHAR(45), Mode VARCHAR(5) NOT NULL DEFAULT 'BLOCK', X2 DOUBLE NULL, Y2 DOUBLE NULL, Z2 DOUBLE NULL)",
				"CREATE TABLE IF NOT EXISTS rp_RunicFXRules (Id INT AUTO_INCREMENT PRIMARY KEY, Route VARCHAR(20) NOT NULL, Value VARCHAR(40) NOT NULL,"
						+ " FxKey VARCHAR(40) NOT NULL, CreatedBy VARCHAR(45), CreatedAt BIGINT)",
				"CREATE TABLE IF NOT EXISTS rp_RunicFXSettings (K VARCHAR(40) NOT NULL PRIMARY KEY, V VARCHAR(255))",
				"CREATE TABLE IF NOT EXISTS rp_RunicFXWelcome (UUID VARCHAR(36) NOT NULL PRIMARY KEY, Name VARCHAR(16),"
						+ " Seconds INT NOT NULL DEFAULT 0, Earned TINYINT NOT NULL DEFAULT 0, Granted TINYINT NOT NULL DEFAULT 0)" };
		try (Connection c = FxStore.db().openConnection(); Statement st = c.createStatement()) {
			for (String s : ddl) st.executeUpdate(s);
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("table setup failed: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ access

	public static boolean owns(Player p, FxDef d) {
		if (d == null || !d.enabled) return false;
		if (p.hasPermission(RunicFX.TRIGGER_PERM) || p.hasPermission("runicfx.fx." + d.key) || p.hasPermission("runicfx.fx.*")) return true;
		Grant g = of(p.getUniqueId()).get(d.key);
		return g != null && g.active();
	}

	/** How the player has it, for the menus ("staff", "permission", "granted: ...", "until ..."). */
	public static String how(Player p, FxDef d) {
		if (p.hasPermission(RunicFX.TRIGGER_PERM)) return "Staff have every effect";
		Grant g = of(p.getUniqueId()).get(d.key);
		if (g != null && g.active()) return (g.source == null ? "Unlocked" : "From " + g.source) + (g.expires > 0 ? " · until " + date(g.expires) : "");
		if (p.hasPermission("runicfx.fx." + d.key) || p.hasPermission("runicfx.fx.*")) return "From your rank";
		return null;
	}

	static String date(long ms) {
		return new java.text.SimpleDateFormat("MMM d", Locale.ENGLISH).format(new java.util.Date(ms));
	}

	/** Effects this player can use right now. */
	public static List<FxDef> owned(Player p) {
		List<FxDef> l = new ArrayList<>();
		for (FxDef d : FxStore.library()) if (owns(p, d)) l.add(d);
		return l;
	}

	public static Map<String, Grant> of(UUID id) {
		Map<String, Grant> m = CACHE.get(id);
		if (m != null) return m;
		m = load(id); // rare: not preloaded yet
		CACHE.put(id, m);
		return m;
	}

	private static Map<String, Grant> load(UUID id) {
		Map<String, Grant> m = new ConcurrentHashMap<>();
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT * FROM rp_RunicFXGrants WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Grant g = read(rs);
					m.put(g.key, g);
				}
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("grants load failed: " + e.getMessage());
		}
		return m;
	}

	private static Grant read(ResultSet rs) throws SQLException {
		Grant g = new Grant();
		g.key = rs.getString("FxKey");
		g.name = rs.getString("Name");
		g.source = rs.getString("Source");
		g.by = rs.getString("GrantedBy");
		g.at = rs.getLong("GrantedAt");
		g.expires = rs.getLong("ExpiresAt");
		g.seen = rs.getInt("Seen") == 1;
		return g;
	}

	/** Call off the main thread. */
	static void preload(UUID id) {
		CACHE.put(id, load(id));
	}

	static void forget(UUID id) {
		CACHE.remove(id);
	}

	// ------------------------------------------------------------------ grant / revoke

	/**
	 * Give a player an effect (days 0 = forever). Returns true when it's new for them (or extends a temporary one).
	 * Online players are told straight away; offline players are told next time they join. Safe off the main thread.
	 */
	public static boolean grant(UUID id, String name, String key, String source, String by, int days) {
		long now = System.currentTimeMillis();
		long expires = days <= 0 ? 0 : now + days * 86_400_000L;
		Map<String, Grant> m = CACHE.containsKey(id) ? CACHE.get(id) : load(id);
		Grant old = m.get(key);
		if (old != null && old.active() && (old.expires == 0 || (expires != 0 && expires <= old.expires))) return false;
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXGrants (UUID, FxKey, Name, Source, GrantedBy, GrantedAt, ExpiresAt, Seen)"
					 + " VALUES (?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE Name=VALUES(Name), Source=VALUES(Source), GrantedBy=VALUES(GrantedBy),"
					 + " GrantedAt=VALUES(GrantedAt), ExpiresAt=VALUES(ExpiresAt), Seen=0")) {
			ps.setString(1, id.toString());
			ps.setString(2, key);
			ps.setString(3, name);
			ps.setString(4, source);
			ps.setString(5, by);
			ps.setLong(6, now);
			ps.setLong(7, expires);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("grant failed: " + e.getMessage());
			return false;
		}
		Grant g = new Grant();
		g.key = key;
		g.name = name;
		g.source = source;
		g.by = by;
		g.at = now;
		g.expires = expires;
		m.put(key, g);
		if (CACHE.containsKey(id)) CACHE.put(id, m);
		Bukkit.getScheduler().runTask(RunicUniverse.getInstance(), () -> {
			Player p = Bukkit.getPlayer(id);
			if (p != null) announceUnseen(p);
		});
		return true;
	}

	public static boolean revoke(UUID id, String key) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXGrants WHERE UUID = ? AND FxKey = ?")) {
			ps.setString(1, id.toString());
			ps.setString(2, key);
			if (ps.executeUpdate() == 0) return false;
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("revoke failed: " + e.getMessage());
			return false;
		}
		Map<String, Grant> m = CACHE.get(id);
		if (m != null) m.remove(key);
		return true;
	}

	/** Everyone granted this effect (newest first). Permission-based access isn't listed. */
	static List<Map.Entry<UUID, Grant>> owners(String key) {
		List<Map.Entry<UUID, Grant>> l = new ArrayList<>();
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT * FROM rp_RunicFXGrants WHERE FxKey = ? ORDER BY GrantedAt DESC")) {
			ps.setString(1, key);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) l.add(new AbstractMap.SimpleEntry<>(UUID.fromString(rs.getString("UUID")), read(rs)));
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("owners load failed: " + e.getMessage());
		}
		return l;
	}

	static void forgetKey(String key) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXGrants WHERE FxKey = ?")) {
			ps.setString(1, key);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("grant cleanup failed: " + e.getMessage());
		}
		for (Map<String, Grant> m : CACHE.values()) m.remove(key);
	}

	/** Tell a player about effects they've unlocked since they last looked, then mark them seen. */
	static void announceUnseen(Player p) {
		Map<String, Grant> m = of(p.getUniqueId());
		List<String> names = new ArrayList<>();
		for (Grant g : m.values()) {
			if (g.seen || !g.active()) continue;
			FxDef d = FxStore.get(g.key);
			if (d == null || !d.enabled) continue;
			names.add(d.display);
			g.seen = true;
		}
		if (names.isEmpty()) return;
		p.sendMessage(Ui.legacy("&b✧ &fYou unlocked RunicFX: &b" + String.join("&f, &b", names) + "&f!"));
		p.sendMessage(Ui.link("   Open /runicfx to preview it and give it to one of your events",
				net.kyori.adventure.text.format.NamedTextColor.GRAY, "/runicfx", "Open RunicFX"));
		UUID id = p.getUniqueId();
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> {
			try (Connection c = FxStore.db().openConnection();
				 PreparedStatement ps = c.prepareStatement("UPDATE rp_RunicFXGrants SET Seen = 1 WHERE UUID = ?")) {
				ps.setString(1, id.toString());
				ps.executeUpdate();
			} catch (SQLException | RuntimeException e) {
				FxStore.warn("seen update failed: " + e.getMessage());
			}
		});
	}

	/**
	 * A player we know by name, as (UUID, name): online here, or anyone who has ever joined the network (from the
	 * LuckPerms tables, no web lookups). Call off the main thread. Null if nobody by that name has played.
	 */
	static Map.Entry<UUID, String> lookup(String name) {
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) return new AbstractMap.SimpleEntry<>(online.getUniqueId(), online.getName());
		for (String t : new String[] { "luckpermssur_players", "luckpermshub_players", "luckpermscre_players", "luckpermssky_players" }) {
			try (Connection c = FxStore.db().openConnection();
				 PreparedStatement ps = c.prepareStatement("SELECT uuid, username FROM " + t + " WHERE username = ? LIMIT 1")) {
				ps.setString(1, name.toLowerCase(Locale.ROOT));
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) return new AbstractMap.SimpleEntry<>(UUID.fromString(rs.getString(1)), name);
				}
			} catch (SQLException | RuntimeException ignored) {
			}
		}
		return null;
	}
}
