package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.staff.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/** The RunicFX menus: library, quick-trigger, my event FX, and the editor (for staff above helper). */
final class FxMenu {

	private FxMenu() {}

	static final List<String> CATEGORIES = Arrays.asList("Celebration", "Magic", "Nature", "Spooky", "Seasonal", "Elemental", "Fancy");

	/** Palette offered in the colour picker (name, hex, wool for the icon). */
	static final String[][] PALETTE = {
			{ "Red", "#FF4D4D", "RED_WOOL" }, { "Orange", "#FFA64D", "ORANGE_WOOL" }, { "Yellow", "#FFF04D", "YELLOW_WOOL" },
			{ "Lime", "#5CFF5C", "LIME_WOOL" }, { "Green", "#2E9E3E", "GREEN_WOOL" }, { "Cyan", "#2EC4C4", "CYAN_WOOL" },
			{ "Light blue", "#4DD2FF", "LIGHT_BLUE_WOOL" }, { "Blue", "#6E6EFF", "BLUE_WOOL" }, { "Purple", "#9B4DFF", "PURPLE_WOOL" },
			{ "Magenta", "#E066FF", "MAGENTA_WOOL" }, { "Pink", "#FF66B3", "PINK_WOOL" }, { "White", "#FFFFFF", "WHITE_WOOL" },
			{ "Light grey", "#C8C8C8", "LIGHT_GRAY_WOOL" }, { "Gold", "#FFC828", "GOLD_BLOCK" }, { "Black", "#202020", "BLACK_WOOL" },
			{ "Brown", "#8B5A2B", "BROWN_WOOL" } };

	static String desc(FxDef d) {
		switch (d.type) {
			case COMMAND: return d.commands.size() + " command(s)";
			case CODED: return "built in code";
			default:
				Set<String> shapes = new LinkedHashSet<>();
				for (FxStep s : d.steps) shapes.add(s.shape);
				return d.steps.size() + " step(s): " + String.join(", ", shapes);
		}
	}

	static ItemStack icon(FxDef d, String... extra) {
		List<String> lore = new ArrayList<>();
		lore.add("&7" + d.category + " &8· " + desc(d));
		if (!d.enabled) lore.add("&c● Switched off");
		if (d.updatedBy != null) lore.add("&8Last edited by " + d.updatedBy);
		lore.add("");
		for (String e : extra) if (e != null) lore.add(e);
		return Gui.item(d.icon, (d.enabled ? "&b" : "&8") + d.display, lore);
	}

	// ------------------------------------------------------------------ main / library

	static void main(Player p) {
		boolean build = RunicFX.canBuild(p);
		Gui g = new Gui(6, "&8RunicFX");
		int slot = 0;
		for (FxDef d : FxStore.library()) {
			if (slot >= 45) break;
			if (!d.enabled && !build) continue;
			g.set(slot++, icon(d, "&eLeft-click: play on yourself", "&eRight-click: more options"), (pl, t) -> {
				if (t.isRightClick()) options(pl, d);
				else {
					pl.closeInventory();
					FxEngine.play(d, pl);
				}
			});
		}
		g.set(45, Gui.item("ARROW", "&7« Staff menu"), (pl, t) -> StaffMenu.main(pl));
		g.set(48, Gui.item("CLOCK", "&f&lMy event FX", "&7Give your teleports, logins,", "&7leaving and world changes an effect"), (pl, t) -> events(pl));
		g.set(49, Gui.item("BOOK", "&fHow RunicFX works", "&7Left-click an effect to try it.", "&7Right-click for: play on someone,",
				"&7play where you're looking, or use", "&7it for one of your events.", build ? "&7You can also build and edit effects." : null), null);
		if (build) {
			g.set(50, Gui.item("EMERALD", "&a&lCreate a new effect", "&7Build from steps (shapes, colours,", "&7timing) or from commands"),
					(pl, t) -> FxPrompt.ask(pl, "Name for the new effect", name -> createType(pl, name.trim())));
		}
		g.set(53, Gui.item("SUNFLOWER", "&fRefresh", "&7Picks up edits made on other servers"), (pl, t) -> {
			FxStore.reload();
			main(pl);
		});
		g.frameRow(5);
		g.openLater(p);
	}

	static void options(Player p, FxDef d) {
		boolean build = RunicFX.canBuild(p);
		Gui g = new Gui(4, "&8" + d.display);
		g.set(4, icon(d), null);
		g.set(10, Gui.item("PLAYER_HEAD", "&fPlay on yourself"), (pl, t) -> {
			pl.closeInventory();
			FxEngine.play(d, pl);
		});
		g.set(11, Gui.item("NAME_TAG", "&fPlay on a player"), (pl, t) -> StaffMenu.pickPlayer(pl, "&8Play " + d.display + " on", target -> {
			pl.closeInventory();
			FxEngine.play(d, target);
			Audit.log(pl, "fx play", d.key + " on " + target.getName());
			Ui.ok(pl, "Played " + d.display + " on " + target.getName() + ".");
		}, () -> options(pl, d)));
		g.set(12, Gui.item("SPYGLASS", "&fPlay where you're looking", "&7Up to 40 blocks away"), (pl, t) -> {
			pl.closeInventory();
			Location at = RunicFX.lookingAt(pl);
			if (at == null) Ui.error(pl, "Look at a block first (up to 40 blocks away).");
			else FxEngine.play(d, null, at);
		});
		g.set(13, Gui.item("CLOCK", "&fUse for one of my events", "&7e.g. play it every time you teleport"), (pl, t) -> pickEvent(pl, ev -> {
			FxStore.bind(pl.getUniqueId(), ev, d.key);
			Ui.ok(pl, FxEvents.EVENTS.get(ev) + " now plays " + d.display + ".");
			events(pl);
		}, () -> options(pl, d)));
		if (build) {
			g.set(19, Gui.item("WRITABLE_BOOK", "&e&lEdit", "&7Change its shape, colours, timing,", "&7name, icon or commands"), (pl, t) -> edit(pl, d));
			g.set(20, Gui.item("PAPER", "&fDuplicate", "&7Make a copy to build on"), (pl, t) -> FxPrompt.ask(pl, "Name for the copy of " + d.display, name -> {
				FxDef c = d.copy(FxStore.newKey(name.trim()), name.trim());
				c.createdBy = pl.getName();
				if (FxStore.save(c, pl.getName())) {
					Audit.log(pl, "fx create", c.key + " (copy of " + d.key + ")");
					edit(pl, c);
				}
			}));
			g.set(21, Gui.item(d.enabled ? "LIME_CONCRETE" : "RED_CONCRETE", d.enabled ? "&a&lOn" : "&c&lSwitched off",
					d.enabled ? "&7Everyone with access can use it." : "&7Hidden from the library and events.", "&eClick to switch"),
					(pl, t) -> Confirm.menu(pl, (d.enabled ? "Switch off " : "Switch on ") + d.display, null, () -> {
						d.enabled = !d.enabled;
						FxStore.save(d, pl.getName());
						Audit.log(pl, d.enabled ? "fx on" : "fx off", d.key);
						options(pl, d);
					}, () -> options(pl, d)));
			g.set(22, Gui.item("TNT", "&cDelete", "&7Removes it and anyone's event use of it"),
					(pl, t) -> Confirm.menu(pl, "Delete " + d.display, Collections.singletonList("&cThis can't be undone."), () -> {
						if (FxStore.delete(d.key)) Audit.log(pl, "fx delete", d.key + " (" + d.display + ")");
						main(pl);
					}, () -> options(pl, d)));
		}
		g.set(27, Gui.item("ARROW", "&7« Library"), (pl, t) -> main(pl));
		g.frameRow(0).frameRow(3);
		g.openLater(p);
	}

	// ------------------------------------------------------------------ events

	static void events(Player p) {
		Map<String, String> b = FxStore.bindings(p.getUniqueId());
		Gui g = new Gui(3, "&8My event FX");
		int slot = 10;
		for (Map.Entry<String, String> e : FxEvents.EVENTS.entrySet()) {
			FxDef d = b.containsKey(e.getKey()) ? FxStore.get(b.get(e.getKey())) : null;
			g.set(slot++, Gui.item(d != null ? d.icon : "GRAY_DYE", "&f" + e.getValue(), "&7Plays: " + (d != null ? "&b" + d.display : "&8nothing"),
					"", "&eClick to choose", d != null ? "&eRight-click to clear" : null), (pl, t) -> {
				if (t.isRightClick()) {
					FxStore.bind(pl.getUniqueId(), e.getKey(), null);
					events(pl);
				} else {
					pickFx(pl, "&8" + e.getValue() + ": pick an effect", fx -> {
						FxStore.bind(pl.getUniqueId(), e.getKey(), fx.key);
						events(pl);
					}, () -> events(pl));
				}
			});
		}
		g.set(18, Gui.item("ARROW", "&7« Library"), (pl, t) -> main(pl));
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}

	static void pickEvent(Player p, Consumer<String> chosen, Runnable back) {
		Gui g = new Gui(3, "&8Use it for which event?");
		int slot = 10;
		for (Map.Entry<String, String> e : FxEvents.EVENTS.entrySet()) {
			g.set(slot++, Gui.item("CLOCK", "&f" + e.getValue()), (pl, t) -> chosen.accept(e.getKey()));
		}
		g.set(18, Gui.item("ARROW", "&7« Back"), (pl, t) -> back.run());
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}

	static void pickFx(Player p, String title, Consumer<FxDef> chosen, Runnable back) {
		Gui g = new Gui(6, title);
		int slot = 0;
		for (FxDef d : FxStore.library()) {
			if (!d.enabled || slot >= 45) continue;
			g.set(slot++, icon(d, "&eClick to choose"), (pl, t) -> chosen.accept(d));
		}
		g.set(45, Gui.item("ARROW", "&7« Back"), (pl, t) -> back.run());
		g.frameRow(5);
		g.openLater(p);
	}

	// ------------------------------------------------------------------ editor

	static void createType(Player p, String name) {
		if (name.isEmpty() || name.length() > 40) {
			Ui.error(p, "Names need 1-40 characters.");
			return;
		}
		Gui g = new Gui(3, "&8New effect: how to build it?");
		g.set(11, Gui.item("BLAZE_POWDER", "&b&lFrom steps", "&7Pick shapes, particles, colours", "&7and timing in menus"), (pl, t) -> {
			FxDef d = new FxDef();
			d.key = FxStore.newKey(name);
			d.display = name;
			d.steps.add(new FxStep());
			create(pl, d);
		});
		g.set(15, Gui.item("COMMAND_BLOCK", "&b&lFrom commands", "&7Paste /particle or other commands,", "&7with %player% %x% %y% %z% %world%"), (pl, t) -> {
			FxDef d = new FxDef();
			d.key = FxStore.newKey(name);
			d.display = name;
			d.type = FxDef.Type.COMMAND;
			d.commands.add("particle minecraft:end_rod %x% %y% %z% 0.5 1 0.5 0.02 40");
			create(pl, d);
		});
		g.frameRow(0).frameRow(2);
		g.openLater(p);
	}

	private static void create(Player p, FxDef d) {
		d.createdBy = p.getName();
		if (FxStore.save(d, p.getName())) {
			Audit.log(p, "fx create", d.key + " (" + d.display + ", " + d.type + ")");
			Ui.ok(p, "Created " + d.display + ". It starts with a simple example; edit it from here.");
			edit(p, d);
		}
	}

	static void save(Player p, FxDef d, String what) {
		if (FxStore.save(d, p.getName())) Audit.log(p, "fx edit", d.key + " " + what);
		else Ui.error(p, "Couldn't save that change.");
	}

	static void edit(Player p, FxDef d) {
		Gui g = new Gui(6, "&8Edit · " + d.display);
		g.set(4, icon(d), null);
		g.set(9, Gui.item("NAME_TAG", "&fName", "&7Now: &f" + d.display, "&eClick to rename"), (pl, t) -> FxPrompt.ask(pl, "New name for " + d.display, txt -> {
			d.display = txt.trim().length() > 40 ? txt.trim().substring(0, 40) : txt.trim();
			save(pl, d, "name");
			edit(pl, d);
		}));
		g.set(10, Gui.item(d.icon, "&fIcon", "&7Hold any item and click here", "&7to use it as this effect's icon"), (pl, t) -> {
			ItemStack hand = pl.getInventory().getItemInMainHand();
			if (hand == null || hand.getType() == Material.AIR) {
				Ui.error(pl, "Hold the item you want as the icon, then click.");
				return;
			}
			d.icon = hand.getType().name();
			save(pl, d, "icon");
			edit(pl, d);
		});
		g.set(11, Gui.item("BOOKSHELF", "&fCategory: &b" + d.category, "&eClick to change"), (pl, t) -> {
			d.category = CATEGORIES.get((CATEGORIES.indexOf(d.category) + 1) % CATEGORIES.size());
			save(pl, d, "category");
			edit(pl, d);
		});
		g.set(13, Gui.item("ENDER_EYE", "&a&lPreview", "&7Plays it on you"), (pl, t) -> {
			pl.closeInventory();
			FxEngine.play(d, pl);
		});
		if (d.type == FxDef.Type.RECIPE) {
			int slot = 18;
			for (int i = 0; i < d.steps.size() && slot < 36; i++) {
				final int idx = i;
				FxStep s = d.steps.get(i);
				g.set(slot++, Gui.item(shapeIcon(s.shape), "&bStep " + (i + 1) + ": " + s.shape,
						"&7" + s.particle + (s.particle.equals("DUST") ? " · " + s.colors.size() + " colour(s)" : ""),
						"&7radius " + FxStep.fmt(s.radius) + " · " + s.duration + " ticks" + (s.delay > 0 ? " · starts after " + s.delay : ""),
						"", "&eClick to edit", "&eRight-click to remove"), (pl, t) -> {
					if (t.isRightClick()) {
						if (d.steps.size() == 1) {
							Ui.error(pl, "An effect needs at least one step.");
							return;
						}
						Confirm.menu(pl, "Remove step " + (idx + 1), null, () -> {
							d.steps.remove(idx);
							save(pl, d, "remove step");
							edit(pl, d);
						}, () -> edit(pl, d));
					} else {
						step(pl, d, idx);
					}
				});
			}
			if (d.steps.size() < 8) {
				g.set(40, Gui.item("LIME_DYE", "&a&lAdd a step", "&7Steps play together; use a step's", "&7delay to make them happen in order"), (pl, t) -> {
					d.steps.add(new FxStep());
					save(pl, d, "add step");
					step(pl, d, d.steps.size() - 1);
				});
			}
		} else if (d.type == FxDef.Type.COMMAND) {
			int slot = 18;
			for (int i = 0; i < d.commands.size() && slot < 36; i++) {
				final int idx = i;
				g.set(slot++, Gui.item("COMMAND_BLOCK", "&bCommand " + (i + 1), StaffMenu.wrap(d.commands.get(i), 40).stream().map(x -> "&7" + x).toArray(String[]::new)),
						(pl, t) -> Confirm.menu(pl, "Remove command " + (idx + 1), null, () -> {
							d.commands.remove(idx);
							save(pl, d, "remove command");
							edit(pl, d);
						}, () -> edit(pl, d)));
			}
			g.set(39, Gui.item("LIME_DYE", "&a&lAdd a command", "&7Runs as the console. Fill-ins:", "&7%player% %x% %y% %z% %world%"),
					(pl, t) -> FxPrompt.ask(pl, "Command to add (without the leading /)", txt -> {
						d.commands.add(txt.trim().startsWith("/") ? txt.trim().substring(1) : txt.trim());
						save(pl, d, "add command");
						edit(pl, d);
					}));
			g.set(40, Gui.item("REPEATER", "&fRepeat: &e" + d.repeat + "x", "&eClick to change"), (pl, t) -> number(pl, "Times to run the commands (1-50)", 1, 50, v -> {
				d.repeat = (int) v;
				save(pl, d, "repeat");
				edit(pl, d);
			}));
			g.set(41, Gui.item("CLOCK", "&fEvery: &e" + d.interval + " ticks", "&720 ticks = 1 second", "&eClick to change"), (pl, t) -> number(pl, "Ticks between runs (1-200)", 1, 200, v -> {
				d.interval = (int) v;
				save(pl, d, "interval");
				edit(pl, d);
			}));
		}
		g.set(45, Gui.item("ARROW", "&7« Options"), (pl, t) -> options(pl, d));
		g.frameRow(0).frameRow(5);
		g.openLater(p);
	}

	static String shapeIcon(String shape) {
		switch (shape) {
			case "rain": return "BLUE_ICE";
			case "ring": return "HEART_OF_THE_SEA";
			case "helix": return "TWISTING_VINES";
			case "column": return "END_ROD";
			case "sphere": return "SNOWBALL";
			default: return "FIRE_CHARGE";
		}
	}

	static void step(Player p, FxDef d, int idx) {
		FxStep s = d.steps.get(idx);
		Gui g = new Gui(6, "&8" + d.display + " · step " + (idx + 1));
		Runnable back = () -> step(p, d, idx);
		g.set(4, Gui.item(shapeIcon(s.shape), "&bStep " + (idx + 1) + ": " + s.shape, "&7" + shapeHelp(s.shape)), null);
		g.set(9, Gui.item(shapeIcon(s.shape), "&fShape: &e" + s.shape, "&7" + String.join(", ", FxStep.SHAPES), "&eClick for the next shape"), (pl, t) -> {
			s.shape = FxStep.SHAPES.get((FxStep.SHAPES.indexOf(s.shape) + 1) % FxStep.SHAPES.size());
			save(pl, d, "shape");
			step(pl, d, idx);
		});
		g.set(10, Gui.item("BLAZE_POWDER", "&fParticle: &e" + s.particle, "&eLeft: next  &8·  &eRight: previous"), (pl, t) -> {
			int i = FxEngine.PARTICLES.indexOf(s.particle);
			int n = FxEngine.PARTICLES.size();
			s.particle = FxEngine.PARTICLES.get(((t.isRightClick() ? i - 1 : i + 1) % n + n) % n);
			save(pl, d, "particle");
			step(pl, d, idx);
		});
		g.set(11, Gui.item("PINK_DYE", "&fColours (" + s.colors.size() + ")", "&7Used by the DUST particle", "&eClick to pick colours"), (pl, t) -> palette(pl, d, idx));
		num(g, 12, p, d, s, "Size", "SLIME_BALL", s.size, 0.1, 4, "Dust particle size", v -> s.size = v);
		num(g, 13, p, d, s, "Count", "PRISMARINE_CRYSTALS", s.count, 1, 400, "Particles per pulse (burst, ring, column, sphere)", v -> s.count = (int) v);
		num(g, 14, p, d, s, "Radius", "TARGET", s.radius, 0, 16, "How wide (blocks)", v -> s.radius = v);
		num(g, 15, p, d, s, "Height", "LADDER", s.height, -2, 10, "Centre height above the feet (burst, ring, sphere)", v -> s.height = v);
		num(g, 16, p, d, s, "Top", "LADDER", s.top, -2, 12, "Upper height (rain, helix, column)", v -> s.top = v);
		num(g, 17, p, d, s, "Bottom", "LADDER", s.bottom, -2, 12, "Lower height (rain, helix, column)", v -> s.bottom = v);
		num(g, 18, p, d, s, "Rain rate", "BLUE_ICE", s.rate, 1, 20, "Rain: new pieces per tick", v -> s.rate = (int) v);
		num(g, 19, p, d, s, "Fall speed min", "FEATHER", s.speedMin, 0.005, 1, "Rain: slowest fall, blocks per tick", v -> s.speedMin = v);
		num(g, 20, p, d, s, "Fall speed max", "FEATHER", s.speedMax, 0.005, 1, "Rain: fastest fall, blocks per tick", v -> s.speedMax = v);
		num(g, 21, p, d, s, "Duration", "CLOCK", s.duration, 1, 1200, "Ticks it keeps going (20 = 1 second)", v -> s.duration = (int) v);
		num(g, 22, p, d, s, "Pulse every", "REPEATER", s.interval, 1, 200, "Ticks between pulses (burst, ring, column, sphere)", v -> s.interval = (int) v);
		num(g, 23, p, d, s, "Delay", "HOPPER", s.delay, 0, 600, "Ticks before this step starts", v -> s.delay = (int) v);
		g.set(24, Gui.item(s.follow ? "LEAD" : "STRING", "&fFollows the player: " + (s.follow ? "&ayes" : "&7no"), "&eClick to switch"), (pl, t) -> {
			s.follow = !s.follow;
			save(pl, d, "follow");
			step(pl, d, idx);
		});
		g.set(25, Gui.item("NOTE_BLOCK", "&fSound: &e" + (s.sound == null || s.sound.isEmpty() ? "none" : s.sound),
				"&7A Minecraft sound name, e.g.", "&7entity.player.levelup or block.amethyst_block.chime", "&eClick to change (type - for none)"),
				(pl, t) -> FxPrompt.ask(pl, "Sound name (e.g. entity.player.levelup), or - for none", txt -> {
					String v = txt.trim().toLowerCase(Locale.ROOT);
					s.sound = v.equals("-") || v.equals("none") ? "" : v.replace("minecraft:", "");
					save(pl, d, "sound");
					step(pl, d, idx);
				}));
		num(g, 26, p, d, s, "Volume", "BELL", s.volume, 0, 4, "Sound volume", v -> s.volume = v);
		num(g, 27, p, d, s, "Pitch", "BELL", s.pitch, 0.5, 2, "Sound pitch (0.5 low, 2 high)", v -> s.pitch = v);
		g.set(40, Gui.item("ENDER_EYE", "&a&lPreview", "&7Plays the whole effect on you"), (pl, t) -> {
			pl.closeInventory();
			FxEngine.play(d, pl);
		});
		g.set(45, Gui.item("ARROW", "&7« " + d.display), (pl, t) -> edit(pl, d));
		g.frameRow(5);
		g.openLater(p);
	}

	static String shapeHelp(String shape) {
		switch (shape) {
			case "rain": return "Pieces fall from Top to Bottom around the player";
			case "ring": return "A flat circle at Height, pulsing";
			case "helix": return "Two strands spiralling from Bottom to Top";
			case "column": return "A vertical line from Bottom to Top";
			case "sphere": return "A hollow ball at Height";
			default: return "A burst of particles inside a ball at Height";
		}
	}

	private interface Setter { void set(double v); }

	private static void num(Gui g, int slot, Player p, FxDef d, FxStep s, String label, String icon, double value, double min, double max,
							String help, Setter setter) {
		final int idx = d.steps.indexOf(s);
		g.set(slot, Gui.item(icon, "&f" + label + ": &e" + FxStep.fmt(value), "&7" + help, "&8Allowed " + FxStep.fmt(min) + " to " + FxStep.fmt(max), "&eClick to change"),
				(pl, t) -> number(pl, label + " (" + FxStep.fmt(min) + "-" + FxStep.fmt(max) + ", now " + FxStep.fmt(value) + ")", min, max, v -> {
					setter.set(v);
					save(pl, d, label.toLowerCase(Locale.ROOT));
					step(pl, d, idx);
				}));
	}

	private static void number(Player p, String question, double min, double max, Setter then) {
		FxPrompt.ask(p, question, txt -> {
			double v;
			try {
				v = Double.parseDouble(txt.trim());
			} catch (NumberFormatException e) {
				Ui.error(p, "That's not a number.");
				return;
			}
			if (v < min || v > max) {
				Ui.error(p, "Use " + FxStep.fmt(min) + " to " + FxStep.fmt(max) + ".");
				return;
			}
			then.set(v);
		});
	}

	static void palette(Player p, FxDef d, int idx) {
		FxStep s = d.steps.get(idx);
		Gui g = new Gui(4, "&8Colours · step " + (idx + 1));
		int slot = 0;
		for (String[] c : PALETTE) {
			boolean on = s.colors.stream().anyMatch(x -> x.equalsIgnoreCase(c[1]));
			ItemStack it = Gui.item(c[2], (on ? "&a✔ " : "&7") + c[0], "&8" + c[1], on ? "&eClick to remove" : "&eClick to add");
			g.set(slot++, it, (pl, t) -> {
				if (on) {
					if (s.colors.size() == 1) {
						Ui.error(pl, "Keep at least one colour.");
						return;
					}
					s.colors.removeIf(x -> x.equalsIgnoreCase(c[1]));
				} else {
					s.colors.add(c[1]);
				}
				save(pl, d, "colours");
				palette(pl, d, idx);
			});
		}
		List<String> custom = new ArrayList<>();
		for (String c : s.colors) {
			boolean inPalette = false;
			for (String[] pc : PALETTE) if (pc[1].equalsIgnoreCase(c)) inPalette = true;
			if (!inPalette) custom.add(c);
		}
		g.set(22, Gui.item("WRITABLE_BOOK", "&fAdd a custom colour", "&7Type a hex code like #33CCFF",
				custom.isEmpty() ? null : "&7Custom now: &f" + String.join(" ", custom), custom.isEmpty() ? null : "&eRight-click to clear custom colours"),
				(pl, t) -> {
					if (t.isRightClick()) {
						s.colors.removeAll(custom);
						if (s.colors.isEmpty()) s.colors.add("#FFFFFF");
						save(pl, d, "colours");
						palette(pl, d, idx);
						return;
					}
					FxPrompt.ask(pl, "Hex colour, e.g. #33CCFF", txt -> {
						String v = txt.trim().startsWith("#") ? txt.trim() : "#" + txt.trim();
						if (!v.matches("#[0-9A-Fa-f]{6}")) {
							Ui.error(pl, "Use six hex digits, like #33CCFF.");
							return;
						}
						s.colors.add(v.toUpperCase(Locale.ROOT));
						save(pl, d, "colours");
						palette(pl, d, idx);
					});
				});
		g.set(27, Gui.item("ARROW", "&7« Step " + (idx + 1)), (pl, t) -> step(pl, d, idx));
		g.frameRow(3);
		g.openLater(p);
	}
}
