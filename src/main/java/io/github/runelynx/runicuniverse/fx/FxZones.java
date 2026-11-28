package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * No-FX zones (Phase 2): places where players' event effects never play, e.g. Hide and Seek arenas, mazes and
 * parkours. A zone is a sphere around a point, or a whole world (radius -1). Each server keeps its own zones in
 * rp_RunicFXZones. Staff manage them in /rs → RunicFX → No-FX zones.
 */
public final class FxZones {

	private FxZones() {}

	public static final class Zone {
		public int id;
		public String world, name, createdBy;
		public double x, y, z;
		public int radius; // -1 = the whole world

		boolean contains(Location l) {
			if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
			if (radius < 0) return true;
			double dx = l.getX() - x, dy = l.getY() - y, dz = l.getZ() - z;
			return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
		}

		public String where() {
			return radius < 0 ? "all of " + world : world + " " + (int) x + ", " + (int) y + ", " + (int) z + " (radius " + radius + ")";
		}
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

	public static List<Zone> all() {
		return new ArrayList<>(ZONES);
	}

	public static boolean blocked(Location l) {
		if (l == null) return false;
		for (Zone z : ZONES) if (z.contains(l)) return true;
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
		try (Connection c = FxStore.db().openConnection();
			 PreparedStatement ps = c.prepareStatement("INSERT INTO rp_RunicFXZones (Server, World, X, Y, Z, Radius, Name, CreatedBy) VALUES (?,?,?,?,?,?,?,?)")) {
			ps.setString(1, RunicUniverse.SERVER_NAME);
			ps.setString(2, l.getWorld().getName());
			ps.setDouble(3, l.getX());
			ps.setDouble(4, l.getY());
			ps.setDouble(5, l.getZ());
			ps.setInt(6, radius);
			ps.setString(7, name);
			ps.setString(8, by);
			ps.executeUpdate();
		} catch (SQLException | RuntimeException e) {
			FxStore.warn("zone save failed: " + e.getMessage());
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
