package io.github.runelynx.runicuniverse.fx;

import java.util.*;

/** One animation step of a RECIPE effect. Every field is editable in the RunicFX editor. */
public final class FxStep {

	public static final List<String> SHAPES = Arrays.asList("rain", "burst", "ring", "helix", "column", "sphere", "firework");
	public static final List<String> FIREWORK_TYPES = Arrays.asList("BALL", "BALL_LARGE", "STAR", "BURST", "CREEPER");

	public String fireworkType = "BALL"; // firework: explosion shape
	public boolean flicker = false;      // firework: twinkle
	public boolean trail = false;        // firework: sparkle trail

	public String shape = "burst";
	public String particle = "DUST";
	public List<String> colors = new ArrayList<>(Collections.singletonList("#FFFFFF"));
	public double size = 1.0;      // dust size
	public int count = 30;         // particles per pulse (burst, ring, column, sphere) or per strand step (helix)
	public double radius = 1.0;
	public double height = 1.0;    // centre height above the feet (burst, ring, sphere)
	public double top = 3.0;       // rain / helix / column: upper height
	public double bottom = 0.1;    // rain / helix / column: lower height
	public int rate = 2;           // rain: new pieces per tick
	public double speedMin = 0.03; // rain: fall speed range, blocks per tick
	public double speedMax = 0.10;
	public double spacing = 0.35;  // rain: blocks a piece falls between redraws (particles linger, so bigger = lighter)
	public int duration = 40;      // ticks the step keeps animating
	public int interval = 10;      // ticks between pulses (burst, ring, column, sphere)
	public int delay = 0;          // ticks before the step starts
	public boolean follow = true;  // move with the player (or stay where it started)
	public String sound = "";      // minecraft sound key, e.g. entity.player.levelup ("" = none)
	public double volume = 1.0;
	public double pitch = 1.0;

	public String serialize() {
		return "shape=" + shape + " particle=" + particle + " colors=" + String.join(",", colors) + " size=" + fmt(size)
				+ " count=" + count + " radius=" + fmt(radius) + " height=" + fmt(height) + " top=" + fmt(top)
				+ " bottom=" + fmt(bottom) + " rate=" + rate + " speedMin=" + fmt(speedMin) + " speedMax=" + fmt(speedMax) + " spacing=" + fmt(spacing)
				+ " duration=" + duration + " interval=" + interval + " delay=" + delay + " follow=" + follow
				+ " sound=" + (sound == null || sound.isEmpty() ? "-" : sound) + " volume=" + fmt(volume) + " pitch=" + fmt(pitch)
				+ " fireworkType=" + fireworkType + " flicker=" + flicker + " trail=" + trail;
	}

	public static FxStep parse(String s) {
		FxStep st = new FxStep();
		for (String kv : s.trim().split("\\s+")) {
			String[] p = kv.split("=", 2);
			if (p.length < 2) continue;
			String v = p[1];
			try {
				switch (p[0]) {
					case "shape": st.shape = v; break;
					case "particle": st.particle = v.toUpperCase(Locale.ROOT); break;
					case "colors": st.colors = new ArrayList<>(Arrays.asList(v.split(","))); break;
					case "size": st.size = Double.parseDouble(v); break;
					case "count": st.count = Integer.parseInt(v); break;
					case "radius": st.radius = Double.parseDouble(v); break;
					case "height": st.height = Double.parseDouble(v); break;
					case "top": st.top = Double.parseDouble(v); break;
					case "bottom": st.bottom = Double.parseDouble(v); break;
					case "rate": st.rate = Integer.parseInt(v); break;
					case "speedMin": st.speedMin = Double.parseDouble(v); break;
					case "speedMax": st.speedMax = Double.parseDouble(v); break;
					case "spacing": st.spacing = Double.parseDouble(v); break;
					case "fireworkType": st.fireworkType = v.toUpperCase(Locale.ROOT); break;
					case "flicker": st.flicker = Boolean.parseBoolean(v); break;
					case "trail": st.trail = Boolean.parseBoolean(v); break;
					case "duration": st.duration = Integer.parseInt(v); break;
					case "interval": st.interval = Integer.parseInt(v); break;
					case "delay": st.delay = Integer.parseInt(v); break;
					case "follow": st.follow = Boolean.parseBoolean(v); break;
					case "sound": st.sound = v.equals("-") ? "" : v; break;
					case "volume": st.volume = Double.parseDouble(v); break;
					case "pitch": st.pitch = Double.parseDouble(v); break;
					default: break;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return st;
	}

	static String fmt(double d) {
		return d == Math.floor(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(Math.round(d * 1000) / 1000.0);
	}
}
