package io.github.runelynx.runicuniverse.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small chest-menu toolkit used by /rs and other Runic plugins. Each menu owns its inventory (identified by the
 * holder, never by title), every click is cancelled, and only clicks inside the menu run a slot's handler.
 */
public final class Gui implements InventoryHolder {

	public interface Click { void click(Player player, ClickType type); }

	private final Inventory inventory;
	private final Map<Integer, Click> handlers = new HashMap<>();

	public Gui(int rows, String title) {
		this.inventory = Bukkit.createInventory(this, rows * 9, Ui.legacy(title));
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public int size() {
		return inventory.getSize();
	}

	public Gui set(int slot, ItemStack item, Click click) {
		inventory.setItem(slot, item);
		if (click != null) {
			handlers.put(slot, click);
		} else {
			handlers.remove(slot);
		}
		return this;
	}

	/** Fill empty slots of a row with dark glass so menus look framed. */
	public Gui frameRow(int row) {
		ItemStack pane = item("GRAY_STAINED_GLASS_PANE", " ");
		for (int i = row * 9; i < row * 9 + 9; i++) {
			if (inventory.getItem(i) == null) {
				inventory.setItem(i, pane);
			}
		}
		return this;
	}

	public void open(Player p) {
		p.openInventory(inventory);
	}

	/** Open on the next tick (required when opening from inside another menu's click). */
	public void openLater(Player p) {
		Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inventory));
	}

	// ------------------------------------------------------------------ items

	public static ItemStack item(String materialName, String name, String... lore) {
		List<String> l = new ArrayList<>();
		for (String s : lore) {
			if (s != null) {
				l.add(s);
			}
		}
		return item(materialName, name, l);
	}

	public static ItemStack item(String materialName, String name, List<String> lore) {
		Material m = Material.matchMaterial(materialName);
		if (m == null || !m.isItem()) {
			m = Material.PAPER;
		}
		ItemStack it = new ItemStack(m);
		ItemMeta meta = it.getItemMeta();
		if (meta != null) {
			meta.displayName(Ui.legacy(name).decoration(TextDecoration.ITALIC, false));
			if (lore != null && !lore.isEmpty()) {
				List<Component> cl = new ArrayList<>();
				for (String s : lore) {
					cl.add(Ui.legacy(s).decoration(TextDecoration.ITALIC, false));
				}
				meta.lore(cl);
			}
			it.setItemMeta(meta);
		}
		return it;
	}

	public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
		ItemStack it = item("PLAYER_HEAD", name, lore);
		if (it.getItemMeta() instanceof SkullMeta sm) {
			sm.setOwningPlayer(owner);
			it.setItemMeta(sm);
		}
		return it;
	}

	// ------------------------------------------------------------------ listener

	static Plugin plugin;

	public static void init(Plugin p) {
		plugin = p;
		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler(ignoreCancelled = false)
			public void onClick(InventoryClickEvent e) {
				if (!(e.getView().getTopInventory().getHolder() instanceof Gui gui)) {
					return;
				}
				e.setCancelled(true);
				int raw = e.getRawSlot();
				if (raw < 0 || raw >= gui.size() || !(e.getWhoClicked() instanceof Player player)) {
					return; // clicks in the player's own inventory do nothing
				}
				Click c = gui.handlers.get(raw);
				if (c != null) {
					try {
						c.click(player, e.getClick());
					} catch (RuntimeException ex) {
						Ui.error(player, "That didn't work: " + ex.getMessage());
						plugin.getLogger().warning("[rs] menu click failed: " + ex);
					}
				}
			}

			@EventHandler
			public void onDrag(InventoryDragEvent e) {
				if (e.getView().getTopInventory().getHolder() instanceof Gui) {
					e.setCancelled(true);
				}
			}
		}, p);
	}
}
