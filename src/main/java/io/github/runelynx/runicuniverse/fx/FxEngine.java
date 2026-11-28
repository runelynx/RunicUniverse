package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays RunicFX effects. Works on Paper 1.20.2 and 26.x: particle names are looked up at runtime (with aliases for
 * names that changed in 1.20.5, e.g. REDSTONE -> DUST), never referenced as enum constants.
 */
public final class FxEngine {

	private FxEngine() {}

	/** Particles offered in the editor (resolved per server version). DUST uses the step's colours and size. */
	public static final List<String> PARTICLES = Arrays.asList("DUST", "END_ROD", "FLAME", "SOUL_FIRE_FLAME", "HAPPY_VILLAGER",
			"FIREWORK", "ENCHANT", "WITCH", "HEART", "NOTE", "CLOUD", "CRIT", "PORTAL", "SNOWFLAKE", "GLOW", "ELECTRIC_SPARK",
			"CHERRY_LEAVES", "TOTEM_OF_UNDYING", "DRAGON_BREATH", "SOUL", "ASH", "WAX_ON");

	private static final Map<String, String[]> ALIASES = new HashMap<>();
	static {
		ALIASES.put("DUST", new String[] { "DUST", "REDSTONE" });
		ALIASES.put("HAPPY_VILLAGER", new String[] { "HAPPY_VILLAGER", "VILLAGER_HAPPY" });
		ALIASES.put("FIREWORK", new String[] { "FIREWORK", "FIREWORKS_SPARK" });
		ALIASES.put("ENCHANT", new String[] { "ENCHANT", "ENCHANTMENT_TABLE" });
		ALIASES.put("WITCH", new String[] { "WITCH", "SPELL_WITCH" });
		ALIASES.put("TOTEM_OF_UNDYING", new String[] { "TOTEM_OF_UNDYING", "TOTEM" });
		ALIASES.put("CRIT", new String[] { "CRIT" });
	}

	public static Particle particle(String name) {
		String[] tries = ALIASES.getOrDefault(name, new String[] { name });
		for (String t : tries) {
			try {
				return Particle.valueOf(t);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return null;
	}

	static boolean isDust(Particle p) {
		return p != null && (p.name().equals("DUST") || p.name().equals("REDSTONE"));
	}

	static Color color(String hex) {
		try {
			return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF);
		} catch (RuntimeException e) {
			return Color.WHITE;
		}
	}

	// ------------------------------------------------------------------ playing

	/** Particles spawned this tick across all running effects (keeps a crowd of effects from flooding clients). */
	private static int tickBudget = 0;
	private static long budgetTick = -1;
	static final int MAX_PER_TICK = 900;

	private static boolean spend(World w, int n) {
		long now = w.getFullTime();
		if (now != budgetTick) {
			budgetTick = now;
			tickBudget = 0;
		}
		if (tickBudget + n > MAX_PER_TICK) return false;
		tickBudget += n;
		return true;
	}

	/** Play on a player (follows them when a step says so). */
	public static boolean play(FxDef d, Player target) {
		return play(d, target, target.getLocation());
	}

	/** Play at a location, optionally tied to an entity it follows. */
	public static boolean play(FxDef d, Entity follow, Location at) {
		if (d == null || at == null || at.getWorld() == null) return false;
		switch (d.type) {
			case COMMAND:
				runCommands(d, follow, at);
				return true;
			case CODED:
				RunicFX.Coded c = RunicFX.coded(d.key);
				if (c != null) c.play(follow instanceof Player ? (Player) follow : null, at.clone());
				return c != null;
			default:
				for (FxStep s : d.steps) start(s, follow, at.clone());
				return true;
		}
	}

	private static void runCommands(FxDef d, Entity follow, Location start) {
		final int runs = Math.max(1, Math.min(50, d.repeat));
		new BukkitRunnable() {
			int done = 0;

			@Override
			public void run() {
				Location l = follow != null && follow.isValid() ? follow.getLocation() : start;
				for (String cmd : d.commands) {
					String c = cmd.replace("%player%", follow instanceof Player ? follow.getName() : "")
							.replace("%x%", fmt(l.getX())).replace("%y%", fmt(l.getY())).replace("%z%", fmt(l.getZ()))
							.replace("%world%", l.getWorld().getName());
					if (c.startsWith("/")) c = c.substring(1);
					try {
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
					} catch (RuntimeException e) {
						FxStore.warn(d.key + " command failed: " + e.getMessage());
					}
				}
				if (++done >= runs) cancel();
			}
		}.runTaskTimer(RunicUniverse.getInstance(), 0, Math.max(1, d.interval));
	}

	private static String fmt(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static final class Drop {
		double dx, y, dz, speed; Object data;
	}

	private static void start(FxStep s, Entity follow, Location origin) {
		final Particle particle = particle(s.particle);
		if (particle == null) {
			FxStore.warn("unknown particle " + s.particle + " on this server");
			return;
		}
		final boolean dust = isDust(particle);
		final List<Object> dustData = new ArrayList<>();
		if (dust) {
			for (String c : s.colors.isEmpty() ? Collections.singletonList("#FFFFFF") : s.colors) {
				dustData.add(new Particle.DustOptions(color(c), (float) Math.max(0.1, Math.min(4, s.size))));
			}
		}
		final int duration = Math.max(1, Math.min(20 * 60, s.duration));
		final int interval = Math.max(1, s.interval);
		final int count = Math.max(1, Math.min(400, s.count));
		final ThreadLocalRandom rnd = ThreadLocalRandom.current();

		new BukkitRunnable() {
			int t = 0;
			final List<Drop> drops = new ArrayList<>();

			Location base() {
				return s.follow && follow != null && follow.isValid() ? follow.getLocation() : origin;
			}

			Object data() {
				return dust ? dustData.get(rnd.nextInt(dustData.size())) : null;
			}

			void spawn(World w, double x, double y, double z, Object data) {
				if (data != null) w.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, data);
				else w.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
			}

			@Override
			public void run() {
				if (t == 0 && s.sound != null && !s.sound.isEmpty()) {
					Location l = base();
					l.getWorld().playSound(l, s.sound, (float) s.volume, (float) s.pitch);
				}
				Location b = base();
				World w = b.getWorld();
				double bx = b.getX(), by = b.getY(), bz = b.getZ();
				switch (s.shape) {
					case "rain": {
						if (t < duration) {
							for (int i = 0; i < Math.max(1, Math.min(20, s.rate)); i++) {
								Drop d = new Drop();
								double ang = rnd.nextDouble() * Math.PI * 2;
								double r = s.radius * Math.sqrt(rnd.nextDouble()); // even spread over the disc
								d.dx = Math.cos(ang) * r;
								d.dz = Math.sin(ang) * r;
								d.y = s.top;
								d.speed = s.speedMin + rnd.nextDouble() * Math.max(0, s.speedMax - s.speedMin);
								d.data = data();
								drops.add(d);
							}
						}
						if (spend(w, drops.size())) {
							for (Drop d : drops) spawn(w, bx + d.dx, by + d.y, bz + d.dz, d.data);
						}
						for (Iterator<Drop> it = drops.iterator(); it.hasNext();) {
							Drop d = it.next();
							d.y -= d.speed;
							if (d.y < s.bottom) it.remove();
						}
						if (t >= duration && drops.isEmpty()) cancel();
						break;
					}
					case "helix": {
						if (t >= duration) { cancel(); break; }
						double frac = (double) t / duration;
						double y = s.bottom + (s.top - s.bottom) * frac;
						double ang = frac * Math.PI * 2 * Math.max(1, count / 10.0);
						if (spend(w, 2)) {
							for (int k = 0; k < 2; k++) {
								double a = ang + Math.PI * k;
								spawn(w, bx + Math.cos(a) * s.radius, by + y, bz + Math.sin(a) * s.radius, data());
							}
						}
						break;
					}
					default: {
						if (t >= duration) { cancel(); break; }
						if (t % interval != 0) break;
						if (!spend(w, count)) break;
						for (int i = 0; i < count; i++) {
							double x, y, z;
							switch (s.shape) {
								case "ring": {
									double a = Math.PI * 2 * i / count;
									x = Math.cos(a) * s.radius; z = Math.sin(a) * s.radius; y = s.height;
									break;
								}
								case "column": {
									x = 0; z = 0; y = s.bottom + (s.top - s.bottom) * i / Math.max(1, count - 1);
									break;
								}
								case "sphere": {
									double u = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
									x = q * Math.cos(a) * s.radius; z = q * Math.sin(a) * s.radius; y = s.height + u * s.radius;
									break;
								}
								default: { // burst: random points inside a ball
									double u = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
									double r = s.radius * Math.cbrt(rnd.nextDouble());
									x = q * Math.cos(a) * r; z = q * Math.sin(a) * r; y = s.height + u * r;
								}
							}
							spawn(w, bx + x, by + y, bz + z, data());
						}
					}
				}
				t++;
			}
		}.runTaskTimer(RunicUniverse.getInstance(), Math.max(0, s.delay), 1);
	}
}
