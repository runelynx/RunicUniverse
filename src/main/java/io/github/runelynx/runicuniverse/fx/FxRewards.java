package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import io.github.runelynx.runicuniverse.staff.Ui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

/**
 * Earning RunicFX (Phase 2).
 *
 * Reward rules (rp_RunicFXRules): "reach X → get this effect". Checked when a player joins and right after a
 * rank-up, an EL find or a maze/parkour finish, so players who already qualify get it too.
 *   RANK      reached this rank on the main ladder (Seeker ... Master), or holds this exact rank otherwise
 *   EL_FINDS  found at least this many Explorer's League locations
 *   MAZES     finished at least this many different mazes / parkours / puzzles
 * Crates, votes, Tebex and the raffle can give effects with the console command /rs fx grant <player> <fx> [days].
 *
 * Welcome-back reward (rp_RunicFXSettings + rp_RunicFXWelcome): once staff start it, anyone online for at least
 * `minutes` (any server, added up) within `days` earns one effect. The effect can be chosen later: everyone who
 * earned it gets it the moment staff pick one.
 */
public final class FxRewards {

	private FxRewards() {}

	public static final LinkedHashMap<String, String> ROUTES = new LinkedHashMap<>();
	static {
		ROUTES.put("RANK", "Reach a rank");
		ROUTES.put("EL_FINDS", "Explorer's League finds");
		ROUTES.put("MAZES", "Mazes & parkours finished");
	}

	/** The main rank ladder, lowest first (from RunicParadise's Ranks). */
	public static final List<String> LADDER = Arrays.asList("ghost", "seeker", "runner", "singer", "brawler", "keeper", "guard",
			"hunter", "slayer", "warder", "champion", "master");

	public static final class Rule {
		public int id;
		public String route, value, fxKey, createdBy;
	}

	// ------------------------------------------------------------------ rules

	static List<Rule> rules() {
		List<Rule> l = new ArrayList<>();
		try (Connection c = FxStore.db().openConnection(); Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT * FROM rp_RunicFXRules ORDER BY Route, Id")) {
			while (rs.next()) {
				Rule r = new Rule();
				r.id = rs.getInt("Id");
				r.route = rs.getString("Route");
				r.value = rs.getString("Value");
				r.fxKey = rs.getString("FxKey");
				r.createdBy = rs.getString("CreatedBy");
				l.add(r);
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("rules load failed: " + e.getMessage());
		}
		return l;
	}

	static boolean addRule(String route, String value, String fxKey, String by) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXRules (Route, Value, FxKey, CreatedBy, CreatedAt) VALUES (?,?,?,?,?)")) {
			ps.setString(1, route);
			ps.setString(2, value);
			ps.setString(3, fxKey);
			ps.setString(4, by);
			ps.setLong(5, System.currentTimeMillis());
			ps.executeUpdate();
			return true;
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("rule save failed: " + e.getMessage());
			return false;
		}
	}

	static boolean removeRule(int id) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXRules WHERE Id = ?")) {
			ps.setInt(1, id);
			return ps.executeUpdate() > 0;
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("rule delete failed: " + e.getMessage());
			return false;
		}
	}

	static String describe(Rule r) {
		switch (r.route) {
			case "RANK": return "Reach " + cap(r.value);
			case "EL_FINDS": return "Find " + r.value + " Explorer's League locations";
			case "MAZES": return "Finish " + r.value + " different mazes or parkours";
			default: return r.route + " " + r.value;
		}
	}

	static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	/** Check every rule for this player and grant what they qualify for. Call off the main thread. */
	static void checkRules(UUID id, String name) {
		List<Rule> rules = rules();
		if (rules.isEmpty()) return;
		String rank = "";
		int el = 0, mazes = 0;
		try (Connection c = FxStore.db().openConnection()) {
			try (PreparedStatement ps = c.prepareStatement("SELECT primary_group FROM luckpermssur_players WHERE uuid = ?")) {
				ps.setString(1, id.toString());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) rank = rs.getString(1).toLowerCase(Locale.ROOT);
				}
			}
			try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(ID) FROM rp_ExplorerCompletions WHERE UUID = ?")) {
				ps.setString(1, id.toString());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) el = rs.getInt(1);
				}
			}
			try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(DISTINCT GameID) FROM rp_RunicGameCompletions WHERE UUID = ?")) {
				ps.setString(1, id.toString());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) mazes = rs.getInt(1);
				}
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("reward check failed: " + e.getMessage());
			return;
		}
		for (Rule r : rules) {
			FxDef d = FxStore.get(r.fxKey);
			if (d == null) continue;
			if (qualifies(r, rank, el, mazes)) FxGrants.grant(id, name, d.key, describe(r), "reward", 0);
		}
	}

	static boolean qualifies(Rule r, String rank, int el, int mazes) {
		try {
			switch (r.route) {
				case "RANK": {
					String want = r.value.toLowerCase(Locale.ROOT);
					int w = LADDER.indexOf(want), have = LADDER.indexOf(rank);
					return w >= 0 ? have >= w : want.equals(rank);
				}
				case "EL_FINDS": return el >= Integer.parseInt(r.value);
				case "MAZES": return mazes >= Integer.parseInt(r.value);
				default: return false;
			}
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Run the rule check for an online player in the background (after a rank-up, EL find, maze finish, or join). */
	static void checkLater(Player p, long ticks) {
		UUID id = p.getUniqueId();
		String name = p.getName();
		Bukkit.getScheduler().runTaskLaterAsynchronously(RunicUniverse.getInstance(), () -> checkRules(id, name), ticks);
	}

	// ------------------------------------------------------------------ settings

	static String setting(String k, String def) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT V FROM rp_RunicFXSettings WHERE K = ?")) {
			ps.setString(1, k);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && rs.getString(1) != null) return rs.getString(1);
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("setting read failed: " + e.getMessage());
		}
		return def;
	}

	static void setSetting(String k, String v) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXSettings (K, V) VALUES (?,?) ON DUPLICATE KEY UPDATE V = VALUES(V)")) {
			ps.setString(1, k);
			ps.setString(2, v);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("setting save failed: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ welcome-back reward

	public static final class Welcome {
		public long start;   // 0 = not started
		public int days = 14, minutes = 10;
		public String fx = "";
		public int earned, waiting;

		public boolean running() {
			long now = System.currentTimeMillis();
			return start > 0 && now >= start && now < end();
		}

		public long end() {
			return start + days * 86_400_000L;
		}
	}

	/** Current welcome-back settings and counts. Call off the main thread. */
	static Welcome welcome() {
		Welcome w = new Welcome();
		try {
			w.start = Long.parseLong(setting("welcome.start", "0"));
			w.days = Integer.parseInt(setting("welcome.days", "14"));
			w.minutes = Integer.parseInt(setting("welcome.minutes", "10"));
		} catch (NumberFormatException ignored) {
		}
		w.fx = setting("welcome.fx", "");
		try (Connection c = FxStore.db().openConnection(); Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT SUM(Earned), SUM(Earned = 1 AND Granted = 0) FROM rp_RunicFXWelcome")) {
			if (rs.next()) {
				w.earned = rs.getInt(1);
				w.waiting = rs.getInt(2);
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("welcome counts failed: " + e.getMessage());
		}
		return w;
	}

	/** Every minute: add a minute for everyone online while the reward runs, and hand out what's been earned. */
	static void startTicker() {
		Bukkit.getScheduler().runTaskTimer(RunicUniverse.getInstance(), () -> {
			Map<UUID, String> online = new HashMap<>();
			for (Player p : Bukkit.getOnlinePlayers()) online.put(p.getUniqueId(), p.getName());
			if (online.isEmpty()) return;
			Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> tick(online));
		}, 20L * 60, 20L * 60);
	}

	private static void tick(Map<UUID, String> online) {
		Welcome w = welcome();
		if (!w.running()) return;
		List<UUID> newlyEarned = new ArrayList<>();
		try (Connection c = FxStore.db().openConnection()) {
			try (PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXWelcome (UUID, Name, Seconds) VALUES (?,?,60)"
					+ " ON DUPLICATE KEY UPDATE Name = VALUES(Name), Seconds = Seconds + 60")) {
				for (Map.Entry<UUID, String> e : online.entrySet()) {
					ps.setString(1, e.getKey().toString());
					ps.setString(2, e.getValue());
					ps.addBatch();
				}
				ps.executeBatch();
			}
			try (PreparedStatement ps = c.prepareStatement("SELECT UUID FROM rp_RunicFXWelcome WHERE Earned = 0 AND Seconds >= ?")) {
				ps.setInt(1, w.minutes * 60);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						UUID id = UUID.fromString(rs.getString(1));
						if (online.containsKey(id)) newlyEarned.add(id); // only this server's players: no double handling
					}
				}
			}
			try (PreparedStatement ps = c.prepareStatement("UPDATE rp_RunicFXWelcome SET Earned = 1 WHERE UUID = ?")) {
				for (UUID id : newlyEarned) {
					ps.setString(1, id.toString());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("welcome tick failed: " + e.getMessage());
			return;
		}
		FxDef d = w.fx.isEmpty() ? null : FxStore.get(w.fx);
		for (UUID id : newlyEarned) {
			if (d != null) {
				grantWelcome(id, online.get(id), d);
			} else {
				Bukkit.getScheduler().runTask(RunicUniverse.getInstance(), () -> {
					Player p = Bukkit.getPlayer(id);
					if (p != null) p.sendMessage(Ui.legacy("&b✧ &fWelcome back! You've earned a free &bRunicFX&f effect. It unlocks as soon as staff reveal it."));
				});
			}
		}
	}

	private static void grantWelcome(UUID id, String name, FxDef d) {
		FxGrants.grant(id, name, d.key, "the welcome-back reward", "welcome", 0);
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("UPDATE rp_RunicFXWelcome SET Granted = 1 WHERE UUID = ?")) {
			ps.setString(1, id.toString());
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("welcome grant mark failed: " + e.getMessage());
		}
	}

	/** Staff picked the welcome-back effect: everyone who already earned it gets it now. Returns how many. Off the main thread. */
	static int setWelcomeFx(FxDef d) {
		setSetting("welcome.fx", d.key);
		Map<UUID, String> waiting = new LinkedHashMap<>();
		try (Connection c = FxStore.db().openConnection(); Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT UUID, Name FROM rp_RunicFXWelcome WHERE Earned = 1 AND Granted = 0")) {
			while (rs.next()) waiting.put(UUID.fromString(rs.getString(1)), rs.getString(2));
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("welcome waiting list failed: " + e.getMessage());
		}
		for (Map.Entry<UUID, String> e : waiting.entrySet()) grantWelcome(e.getKey(), e.getValue(), d);
		return waiting.size();
	}
}
