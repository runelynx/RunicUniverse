package io.github.runelynx.runicuniverse.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Chat building blocks for /rs: one prefix, clickable buttons, copy-to-clipboard. Works on 1.20.2 and 26.x. */
public final class Ui {

	private Ui() {}

	public static final TextColor ACCENT = TextColor.color(0x55C1FF);

	public static Component prefix() {
		return Component.text("RS ", ACCENT, TextDecoration.BOLD)
				.append(Component.text("» ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));
	}

	/** Send a prefixed line. */
	public static void send(CommandSender s, Component c) {
		s.sendMessage(prefix().append(c));
	}

	public static void info(CommandSender s, String text) {
		send(s, Component.text(text, NamedTextColor.GRAY));
	}

	public static void ok(CommandSender s, String text) {
		send(s, Component.text(text, NamedTextColor.GREEN));
	}

	public static void error(CommandSender s, String text) {
		send(s, Component.text(text, NamedTextColor.RED));
	}

	/** Legacy "&a"/"§a" text to a component (for names and lore that already use colour codes). */
	public static Component legacy(String text) {
		if (text == null) {
			return Component.empty();
		}
		// Only &X where X is a real colour/format code becomes a colour; a plain & ("Events & Games") stays as text.
		return LegacyComponentSerializer.legacySection().deserialize(text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1"));
	}

	/** [Label] that runs a command when clicked. */
	public static Component button(String label, TextColor color, String command, String hover) {
		return Component.text("[" + label + "]", color, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(Component.text(hover == null ? command : hover, NamedTextColor.GRAY)));
	}

	/** [Label] that puts text into the chat box for the player to finish. */
	public static Component suggest(String label, TextColor color, String command, String hover) {
		return Component.text("[" + label + "]", color)
				.clickEvent(ClickEvent.suggestCommand(command))
				.hoverEvent(HoverEvent.showText(Component.text(hover == null ? "Click to fill in: " + command : hover, NamedTextColor.GRAY)));
	}

	/** [Label] that copies text to the clipboard. */
	public static Component copy(String label, String text) {
		return Component.text("[" + label + "]", NamedTextColor.AQUA)
				.clickEvent(ClickEvent.copyToClipboard(text))
				.hoverEvent(HoverEvent.showText(Component.text("Click to copy:\n", NamedTextColor.GRAY)
						.append(Component.text(text, NamedTextColor.WHITE))));
	}

	/** Clickable text (no brackets) that runs a command, e.g. a player name. */
	public static Component link(String text, TextColor color, String command, String hover) {
		return Component.text(text, color)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
	}

	public static Component space() {
		return Component.text(" ");
	}
}
