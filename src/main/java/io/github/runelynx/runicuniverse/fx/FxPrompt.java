package io.github.runelynx.runicuniverse.fx;

import io.github.runelynx.runicuniverse.RunicUniverse;
import io.github.runelynx.runicuniverse.staff.Ui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** "Type it in chat" for the RunicFX editor: the next chat line is captured (not shown publicly). "cancel" aborts. */
@SuppressWarnings("deprecation")
final class FxPrompt implements Listener {

	private static final Map<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> EXPIRES = new ConcurrentHashMap<>();

	static void ask(Player p, String question, Consumer<String> callback) {
		PENDING.put(p.getUniqueId(), callback);
		EXPIRES.put(p.getUniqueId(), System.currentTimeMillis() + 120_000);
		p.closeInventory();
		Ui.info(p, question + "  (type it in chat, or 'cancel')");
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent e) {
		UUID id = e.getPlayer().getUniqueId();
		Consumer<String> cb = PENDING.remove(id);
		Long exp = EXPIRES.remove(id);
		if (cb == null) return;
		e.setCancelled(true);
		if (exp != null && exp < System.currentTimeMillis()) return;
		String text = e.getMessage().trim();
		Player p = e.getPlayer();
		Bukkit.getScheduler().runTask(RunicUniverse.getInstance(), () -> {
			if (text.equalsIgnoreCase("cancel")) {
				Ui.info(p, "Cancelled.");
				return;
			}
			cb.accept(text);
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		PENDING.remove(e.getPlayer().getUniqueId());
		EXPIRES.remove(e.getPlayer().getUniqueId());
	}
}
