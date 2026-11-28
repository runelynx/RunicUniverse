package io.github.runelynx.runicuniverse.fx;

import java.util.*;

/**
 * One RunicFX effect (2026-11-28). Three kinds:
 *   RECIPE  - a list of animation steps (shape, particle, colours, sizes, timings) anyone above helper can edit in /rs
 *   COMMAND - console commands with %player% %x% %y% %z% %world% (lets Croc paste his existing /particle lines)
 *   CODED   - a Java effect registered by a plugin (RunicFX.registerCoded)
 * Stored in rp_RunicFX; the definition is plain text so it stays readable in the database:
 *   step shape=rain particle=DUST colors=#FF0000,#00FF00 radius=1.4 ...
 *   cmd particle minecraft:flame %x% %y% %z% 0.3 0.3 0.3 0 20
 *   repeat=3 interval=10
 */
public final class FxDef {

	public enum Type { RECIPE, COMMAND, CODED }

	public String key;
	public String display;
	public String icon = "BLAZE_POWDER";
	public String category = "Magic";
	public Type type = Type.RECIPE;
	public boolean enabled = true;
	public String createdBy, updatedBy;
	public long updatedAt;

	public final List<FxStep> steps = new ArrayList<>();
	public final List<String> commands = new ArrayList<>();
	public int repeat = 1;       // COMMAND: times to run the commands
	public int interval = 20;    // COMMAND: ticks between runs

	public String definition() {
		StringBuilder b = new StringBuilder();
		for (FxStep s : steps) b.append("step ").append(s.serialize()).append('\n');
		for (String c : commands) b.append("cmd ").append(c).append('\n');
		b.append("repeat=").append(repeat).append(" interval=").append(interval).append('\n');
		return b.toString();
	}

	public void parse(String text) {
		steps.clear();
		commands.clear();
		if (text == null) return;
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.startsWith("step ")) {
				steps.add(FxStep.parse(line.substring(5)));
			} else if (line.startsWith("cmd ")) {
				commands.add(line.substring(4));
			} else if (line.startsWith("repeat=")) {
				for (String kv : line.split("\\s+")) {
					String[] p = kv.split("=", 2);
					if (p.length < 2) continue;
					try {
						if (p[0].equals("repeat")) repeat = Math.max(1, Integer.parseInt(p[1]));
						if (p[0].equals("interval")) interval = Math.max(1, Integer.parseInt(p[1]));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
	}

	public FxDef copy(String newKey, String newDisplay) {
		FxDef d = new FxDef();
		d.key = newKey;
		d.display = newDisplay;
		d.icon = icon;
		d.category = category;
		d.type = type;
		d.enabled = enabled;
		d.parse(definition());
		return d;
	}

	/** The built-in starter effect: multicoloured confetti drifting down around the player at varying speeds. */
	public static FxDef confettiRain() {
		FxDef d = new FxDef();
		d.key = "confetti_rain";
		d.display = "Confetti Rain";
		d.icon = "AMETHYST_SHARD";
		d.category = "Celebration";
		FxStep s = new FxStep();
		s.shape = "rain";
		s.particle = "DUST";
		s.colors = new ArrayList<>(Arrays.asList("#FF4D4D", "#FFA64D", "#FFF04D", "#5CFF5C", "#4DD2FF", "#6E6EFF", "#E066FF", "#FF66B3"));
		s.size = 1.1;
		s.radius = 1.4;     // a bit wider than the player, so pieces fall through a first-person view
		s.top = 3.2;        // starts above the head
		s.bottom = 0.1;     // ends at the feet
		s.rate = 3;         // new pieces per tick
		s.speedMin = 0.03;  // blocks per tick: slow drift ...
		s.speedMax = 0.11;  // ... to a quicker fall, mixed
		s.duration = 100;   // keeps raining for 5 seconds
		s.follow = true;
		s.sound = "entity.player.levelup";
		s.volume = 0.6;
		s.pitch = 1.6;
		d.steps.add(s);
		return d;
	}
}
