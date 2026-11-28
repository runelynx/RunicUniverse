package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunicFX event hooks (Phase 1: staff). A player's assigned FX plays when the event happens.
 *   PRE_TELEPORT  where you were, as you leave        POST_TELEPORT  where you land, a moment after
 *   JOIN_NETWORK  logging in to Runic Paradise         ARRIVE_SERVER  arriving from another of our servers
 *   LEAVE         leaving this server                  CHANGE_WORLD   entering another world
 * Skipped for vanished players, tiny teleports (ender pearls, chorus fruit, under 8 blocks) and repeats within 3 s.
 */
public final class FxEvents implements Listener {

	public static final LinkedHashMap<String, String> EVENTS = new LinkedHashMap<>();
	static {
		EVENTS.put("PRE_TELEPORT", "Pre-teleport");
		EVENTS.put("POST_TELEPORT", "Post-teleport");
		EVENTS.put("JOIN_NETWORK", "Join the network");
		EVENTS.put("ARRIVE_SERVER", "Arrive on a server");
		EVENTS.put("LEAVE", "Leave");
		EVENTS.put("CHANGE_WORLD", "Change world");
	}

	private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

	static boolean vanished(Player p) {
		for (MetadataValue v : p.getMetadata("vanished")) {
			if (v.asBoolean()) return true;
		}
		return false;
	}

	/** Phase 1: event FX are a staff feature. */
	static boolean eligible(Player p) {
		return p.hasPermission(RunicFX.TRIGGER_PERM) && !vanished(p);
	}

	static void fire(Player p, String event, Location at) {
		if (!eligible(p)) return;
		String fxKey = FxStore.bindings(p.getUniqueId()).get(event);
		if (fxKey == null) return;
		FxDef d = FxStore.get(fxKey);
		if (d == null || !d.enabled) return;
		String k = p.getUniqueId() + event;
		long now = System.currentTimeMillis();
		Long last = LAST.get(k);
		if (last != null && now - last < 3000) return;
		LAST.put(k, now);
		FxEngine.play(d, event.equals("PRE_TELEPORT") || event.equals("LEAVE") ? null : p, at);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent e) {
		String cause = e.getCause().name();
		if (cause.equals("ENDER_PEARL") || cause.equals("CHORUS_FRUIT") || cause.equals("CONSUMABLE_EFFECT")) return;
		Location from = e.getFrom(), to = e.getTo();
		if (to == null) return;
		if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < 64) return;
		Player p = e.getPlayer();
		fire(p, "PRE_TELEPORT", from.clone());
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
			if (p.isOnline()) fire(p, "POST_TELEPORT", p.getLocation());
		}, 3);
	}

	/** RunicFX fireworks are for show: they never hurt anyone or anything. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFireworkDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
		if (e.getDamager() instanceof org.bukkit.entity.Firework && e.getDamager().getScoreboardTags().contains(FxEngine.FIREWORK_TAG)) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorld(PlayerChangedWorldEvent e) {
		Player p = e.getPlayer();
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
			if (p.isOnline()) fire(p, "CHANGE_WORLD", p.getLocation());
		}, 5);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if (!p.hasPermission(RunicFX.TRIGGER_PERM)) return;
		UUID id = p.getUniqueId();
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> {
			FxStore.preload(id);
			long since = FxStore.sinceLastQuit(id);
			String event = since >= 0 && since < 15_000 ? "ARRIVE_SERVER" : "JOIN_NETWORK";
			// give the client a moment to load the world before playing
			Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
				if (p.isOnline()) fire(p, event, p.getLocation());
			}, 30);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		UUID id = p.getUniqueId();
		fire(p, "LEAVE", p.getLocation());
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> FxStore.markQuit(id));
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> FxStore.forget(id), 20);
	}
}
