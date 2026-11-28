package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RunicFX zones (Phase 2), per server in rp_RunicFXZones. Staff manage them in /rs → RunicFX → Zones.
 *   BLOCK zones: players' event effects never play inside (Hide and Seek arenas, mazes, parkours, whole worlds).
 *   ALLOW zones: in a world that has any, effects play ONLY inside them (e.g. RunicSky: just the spawn island).
 * A zone is a sphere (point + radius), a box (two corners), or a whole world (radius -1). Block beats allow.
 */
public final class FxZones {

	private FxZones() {}

	public static final class Zone {
		public int id;
		public String world, name, createdBy;
		public double x, y, z;
		public int radius; // -1 = the whole world
		public boolean allow;   // ALLOW zone (effects only inside allow zones on this server)
		public boolean box;     // box from (x,y,z) to (x2,y2,z2)
		public double x2, y2, z2;

		boolean contains(Location l) {
			if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
			if (radius < 0) return true;
			if (box) {
				return l.getX() >= Math.min(x, x2) && l.getX() <= Math.max(x, x2) + 1 && l.getY() >= Math.min(y, y2) && l.getY() <= Math.max(y, y2) + 1
						&& l.getZ() >= Math.min(z, z2) && l.getZ() <= Math.max(z, z2) + 1;
			}
			double dx = l.getX() - x, dy = l.getY() - y, dz = l.getZ() - z;
			return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
		}

		public String where() {
			if (radius < 0) return "all of " + world;
			if (box) return world + " box " + (int) x + "," + (int) y + "," + (int) z + " to " + (int) x2 + "," + (int) y2 + "," + (int) z2;
			return world + " " + (int) x + ", " + (int) y + ", " + (int) z + " (radius " + radius + ")";
		}
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

	public static List<Zone> all() {
		return new ArrayList<>(ZONES);
	}

	/**
	 * True when players' effects may not play here: inside a block zone, or in a world that has allow zones but
	 * outside all of them. Worlds without allow zones are unaffected.
	 */
	public static boolean blocked(Location l) {
		if (l == null || l.getWorld() == null) return false;
		String world = l.getWorld().getName();
		boolean anyAllow = false, inAllow = false;
		for (Zone z : ZONES) {
			boolean in = z.contains(l);
			if (!z.allow && in) return true;
			if (z.allow && z.world.equals(world)) {
				anyAllow = true;
				if (in) inAllow = true;
			}
		}
		return anyAllow && !inAllow;
	}

	public static boolean hasAllowZones() {
		for (Zone z : ZONES) if (z.allow) return true;
		return false;
	}

	public static Zone at(Location l) {
		for (Zone z : ZONES) if (z.contains(l)) return z;
		return null;
	}

	/** Call off the main thread. */
	static void reload() {
		List<Zone> fresh = new ArrayList<>();
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT * FROM rp_RunicFXZones WHERE Server = ? ORDER BY Id")) {
			ps.setString(1, RunicUniverse.SERVER_NAME);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Zone z = new Zone();
					z.id = rs.getInt("Id");
					z.world = rs.getString("World");
					z.name = rs.getString("Name");
					z.createdBy = rs.getString("CreatedBy");
					z.x = rs.getDouble("X");
					z.y = rs.getDouble("Y");
					z.z = rs.getDouble("Z");
					z.radius = rs.getInt("Radius");
					z.allow = "ALLOW".equalsIgnoreCase(rs.getString("Mode"));
					double x2 = rs.getDouble("X2");
					z.box = !rs.wasNull();
					z.x2 = x2;
					z.y2 = rs.getDouble("Y2");
					z.z2 = rs.getDouble("Z2");
					fresh.add(z);
				}
			}
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("zones load failed: " + e.getMessage());
			return;
		}
		ZONES.clear();
		ZONES.addAll(fresh);
	}

	static boolean add(String name, Location l, int radius, String by) {
		return add(name, l, null, radius, false, by);
	}

	/** Add a zone: a sphere (corner2 null, radius >= 0), a box (corner2 set) or a whole world (radius -1). */
	static boolean add(String name, Location l, Location corner2, int radius, boolean allow, String by) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXZones (Server, World, X, Y, Z, Radius, Name, CreatedBy, Mode, X2, Y2, Z2) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
			ps.setString(1, RunicUniverse.SERVER_NAME);
			ps.setString(2, l.getWorld().getName());
			ps.setDouble(3, l.getX());
			ps.setDouble(4, l.getY());
			ps.setDouble(5, l.getZ());
			ps.setInt(6, radius);
			ps.setString(7, name);
			ps.setString(8, by);
			ps.setString(9, allow ? "ALLOW" : "BLOCK");
			if (corner2 == null) {
				ps.setNull(10, Types.DOUBLE);
				ps.setNull(11, Types.DOUBLE);
				ps.setNull(12, Types.DOUBLE);
			} else {
				ps.setDouble(10, corner2.getBlockX());
				ps.setDouble(11, corner2.getBlockY());
				ps.setDouble(12, corner2.getBlockZ());
			}
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("zone save failed: " + e.getMessage());
			return false;
		}
		reload();
		return true;
	}

	static boolean setMode(int id, boolean allow) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("UPDATE rp_RunicFXZones SET Mode = ? WHERE Id = ?")) {
			ps.setString(1, allow ? "ALLOW" : "BLOCK");
			ps.setInt(2, id);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("zone update failed: " + e.getMessage());
			return false;
		}
		reload();
		return true;
	}

	static boolean remove(int id) {
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("DELETE FROM rp_RunicFXZones WHERE Id = ?")) {
			ps.setInt(1, id);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("zone delete failed: " + e.getMessage());
			return false;
		}
		reload();
		return true;
	}
}
