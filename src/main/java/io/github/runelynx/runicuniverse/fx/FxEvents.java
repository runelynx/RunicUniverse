package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.metadata.MetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunicFX event hooks. A player's chosen effect plays when the event happens, if they still have that effect.
 * Everyone can use them (Phase 2); staff have every effect. Survival-only events are fired by RunicParadise through
 * RunicFX.event(player, key). Skipped while vanished, with "my effects off", inside a no-FX zone, for tiny teleports
 * (ender pearls, chorus fruit, under 8 blocks) and for repeats of the same event within 3 seconds.
 */
public final class FxEvents implements Listener {

	public static final LinkedHashMap<String, String> EVENTS = new LinkedHashMap<>();
	/** Events that only happen on Survival (shown with a note on other servers). */
	public static final Set<String> SURVIVAL_ONLY = new HashSet<>(Arrays.asList("RANK_UP", "FAITH_LEVEL", "FAITH_CAST",
			"EL_FIND", "MAZE_FINISH", "RAFFLE_WIN", "VOTE"));
	static {
		EVENTS.put("JOIN_NETWORK", "Join the network");
		EVENTS.put("ARRIVE_SERVER", "Arrive on a server");
		EVENTS.put("LEAVE", "Leave");
		EVENTS.put("CHANGE_WORLD", "Change world");
		EVENTS.put("PRE_TELEPORT", "Pre-teleport");
		EVENTS.put("POST_TELEPORT", "Post-teleport");
		EVENTS.put("DEATH", "Death");
		EVENTS.put("RESPAWN", "Respawn");
		EVENTS.put("LEVEL_UP", "XP level-up");
		EVENTS.put("ADVANCEMENT", "Advancement");
		EVENTS.put("FISH_CATCH", "Fishing catch");
		EVENTS.put("RANK_UP", "Rank-up");
		EVENTS.put("FAITH_LEVEL", "Faith level-up");
		EVENTS.put("FAITH_CAST", "Faith power cast");
		EVENTS.put("EL_FIND", "Explorer's League find");
		EVENTS.put("MAZE_FINISH", "Maze or parkour finish");
		EVENTS.put("RAFFLE_WIN", "Raffle win");
		EVENTS.put("VOTE", "Vote");
	}

	private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

	static boolean vanished(Player p) {
		for (MetadataValue v : p.getMetadata("vanished")) {
			if (v.asBoolean()) return true;
		}
		return false;
	}

	static void fire(Player p, String event, Location at) {
		if (p == null || vanished(p) || FxPrefs.myOff(p.getUniqueId()) || FxZones.blocked(at)) return;
		String fxKey = FxStore.bindings(p.getUniqueId()).get(event);
		if (fxKey == null) return;
		FxDef d = FxStore.get(fxKey);
		if (!FxGrants.owns(p, d)) return;
		String k = p.getUniqueId() + event;
		long now = System.currentTimeMillis();
		Long last = LAST.get(k);
		if (last != null && now - last < 3000) return;
		LAST.put(k, now);
		FxEngine.play(d, event.equals("PRE_TELEPORT") || event.equals("LEAVE") || event.equals("DEATH") ? null : p, at);
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
	public void onDeath(PlayerDeathEvent e) {
		fire(e.getEntity(), "DEATH", e.getEntity().getLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent e) {
		Player p = e.getPlayer();
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
			if (p.isOnline()) fire(p, "RESPAWN", p.getLocation());
		}, 10);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLevel(PlayerLevelChangeEvent e) {
		if (e.getNewLevel() > e.getOldLevel()) fire(e.getPlayer(), "LEVEL_UP", e.getPlayer().getLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onAdvancement(PlayerAdvancementDoneEvent e) {
		String key = e.getAdvancement().getKey().getKey();
		if (key.startsWith("recipes/")) return; // recipe unlocks count as advancements; skip them
		fire(e.getPlayer(), "ADVANCEMENT", e.getPlayer().getLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFish(PlayerFishEvent e) {
		if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) fire(e.getPlayer(), "FISH_CATCH", e.getPlayer().getLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		UUID id = p.getUniqueId();
		String name = p.getName();
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> {
			FxStore.preload(id);
			FxGrants.preload(id);
			FxPrefs.load(id);
			long since = FxStore.sinceLastQuit(id);
			String event = since >= 0 && since < 15_000 ? "ARRIVE_SERVER" : "JOIN_NETWORK";
			// give the client a moment to load the world before playing
			Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
				if (!p.isOnline()) return;
				fire(p, event, p.getLocation());
				if (event.equals("JOIN_NETWORK")) FxGrants.announceUnseen(p);
			}, 30);
			FxRewards.checkRules(id, name);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		UUID id = p.getUniqueId();
		fire(p, "LEAVE", p.getLocation());
		Bukkit.getScheduler().runTaskAsynchronously(RunicUniverse.getInstance(), () -> FxStore.markQuit(id));
		Bukkit.getScheduler().runTaskLater(RunicUniverse.getInstance(), () -> {
			if (Bukkit.getPlayer(id) != null) return; // came straight back
			FxStore.forget(id);
			FxGrants.forget(id);
			FxPrefs.forget(id);
		}, 20);
	}

	/**
	 * Votes: listens for NuVotifier's VotifierEvent by name, so RunicUniverse doesn't need Votifier to build or run.
	 * Only servers with Votifier (Survival) get the event.
	 */
	@SuppressWarnings("unchecked")
	static void hookVotifier(org.bukkit.plugin.Plugin plugin) {
		Class<? extends Event> cls;
		try {
			cls = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
		} catch (ClassNotFoundException e) {
			return;
		}
		Bukkit.getPluginManager().registerEvent(cls, new Listener() {}, EventPriority.MONITOR, (l, ev) -> {
			if (!cls.isInstance(ev)) return;
			try {
				Object vote = ev.getClass().getMethod("getVote").invoke(ev);
				String user = (String) vote.getClass().getMethod("getUsername").invoke(vote);
				Bukkit.getScheduler().runTask(plugin, () -> {
					Player p = user == null ? null : Bukkit.getPlayerExact(user);
					if (p != null) fire(p, "VOTE", p.getLocation());
				});
			} catch (ReflectiveOperationException | RuntimeException ex) {
				FxStore.warn("vote hook failed: " + ex.getMessage());
			}
		}, plugin);
	}
}
