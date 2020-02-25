package io.github.runelynx.runicuniverse;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.github.runelynx.runicuniverse.RunicMessaging.RunicFormat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Level;

public class RunicUniverse extends JavaPlugin implements PluginMessageListener, Listener {

	private static Plugin instance;
	public static String SERVER_NAME = "Unknown";
	public static Permission perms = null;

	@Override
	public void onEnable() {

		instance = this;

		getServer().getPluginManager().registerEvents(this, this);

		Bukkit.getLogger().log(Level.INFO, "RunicUniverse plugin is loading..");

		// getConfig().options().copyDefaults(true);
		// saveConfig();

		SERVER_NAME = instance.getConfig().getString("ServerName");

		pushPlayerListToWeb();
		setupPermissions();
		RunicStats.startScheduledStatTask_OnlinePlayers();

		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

		getCommand("goto").setExecutor(new Commands());
		getCommand("travel").setExecutor(new Commands());
		getCommand("announce").setExecutor(new Commands());
		getCommand("staffrank").setExecutor(new Commands());
		getCommand("censor").setExecutor(new Commands());
		getCommand("runicholo").setExecutor(new Commands());
		getCommand("evacuate").setExecutor(new Commands());

		ChatCensor.censoredWords.clear();
		ChatCensor.censoredWords.addAll(ChatCensor.listBadWordsFromYML());

		for (Player p : Bukkit.getOnlinePlayers()) {
			p.sendMessage(ChatColor.DARK_GRAY + "Connection to Runic Universe " + ChatColor.GREEN + "established"
					+ ChatColor.DARK_GRAY + "!");
		}

		Bukkit.getLogger().log(Level.INFO, "RunicUniverse plugin is loaded!");

		// tell the other server this one is reconnected to the universe
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("Forward"); // So BungeeCord knows to forward it
		out.writeUTF("ONLINE");
		out.writeUTF("ServerConn"); // The channel name to check if this
									// your data

		ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
		DataOutputStream msgout = new DataOutputStream(msgbytes);

		try {
			msgout.writeUTF(SERVER_NAME); // You can do anything
			// msgout
			msgout.writeShort(123);
		} catch (IOException ignored) {}

		out.writeShort(msgbytes.toByteArray().length);
		out.write(msgbytes.toByteArray());

		// If you don't care about the player
		// Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(),
		// null);
		// Else, specify them

		List<Player> list = new ArrayList<>(Bukkit.getServer().getOnlinePlayers());
		boolean runOnce = false;

		if (list.size() != 0) {
			runOnce = true;
		}

		while (runOnce) {
			list.get(0).sendPluginMessage(instance, "BungeeCord", out.toByteArray());
			runOnce = false;
		}

	}

	@Override
	public void onDisable() {
		try {
			this.setEnabled(true);

			// Call original onDisable code
			// tell the other server this one is reconnected to the universe
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("Forward"); // So BungeeCord knows to forward it
			out.writeUTF("ONLINE");
			out.writeUTF("ServerDisconn"); // The channel name to check if this
											// your data

			ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
			DataOutputStream msgout = new DataOutputStream(msgbytes);

			try {
				msgout.writeUTF(SERVER_NAME); // You can do anything
				// msgout
				msgout.writeShort(123);
			} catch (IOException e) {
				getLogger().log(Level.INFO, "Error when sending ServerDisconn plugin message: " + e);
			}

			out.writeShort(msgbytes.toByteArray().length);
			out.write(msgbytes.toByteArray());

			// If you don't care about the player
			// Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(),
			// null);
			// Else, specify them

			Iterables.getFirst(Bukkit.getOnlinePlayers(), null).sendPluginMessage(instance, "BungeeCord",
					out.toByteArray());

			this.setEnabled(false);
		} catch (Exception ignored) {}

		for (Player p : Bukkit.getOnlinePlayers()) {
			p.sendMessage(ChatColor.DARK_GRAY + "Connection to Runic Universe " + ChatColor.RED + "broken"
					+ ChatColor.DARK_GRAY + "!");

		}

	}

	public static Plugin getInstance() {
		return instance;
	}

	public void onPluginMessageReceived(String channel, Player player, byte[] message) {

		if (!channel.equals("BungeeCord")) {
			return;
		}

		ByteArrayDataInput in = ByteStreams.newDataInput(message);

		String subChannel = in.readUTF();
		short len;
		byte[] msgbytes;

		// Process messaging from BUNGEE directly here. Messaging from SPIGOTS
		// is further down!
		if (subChannel.equals("BungeePromoterSubchannel") || subChannel.equals("PlayerList") || subChannel.equals("ServerIP") ||
				subChannel.equals("CMIPlayerInfoReqChannel") || subChannel.equals("GetServers")) {

			return;
		}

		len = in.readShort();

		msgbytes = new byte[len];

		in.readFully(msgbytes);

		DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(msgbytes));
		try {
			String somedata = msgin.readUTF();
			short somenumber = msgin.readShort();

			Bukkit.getLogger().log(Level.INFO, "Received a message: " + somedata);

			if (subChannel.equals("Chat")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					p.sendMessage(somedata);
				}
			} else if (subChannel.equals("ServerConn")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					p.sendMessage(ChatColor.DARK_GRAY + somedata + " has " + ChatColor.GREEN + "connected"
							+ ChatColor.DARK_GRAY + " to the Runic Universe");
				}
			} else if (subChannel.equals("ServerDisconn")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					p.sendMessage(ChatColor.DARK_GRAY + somedata + " has " + ChatColor.RED + "disconnected"
							+ ChatColor.DARK_GRAY + " from the Runic Universe");
				}
			} else if (subChannel.equals("NewPlayer")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					p.sendMessage(ChatColor.LIGHT_PURPLE + somedata + "" + ChatColor.RED
							+ " has joined Survival for the first time!");
				}
			} else if (subChannel.equals("PlayerReady")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					p.sendMessage(ChatColor.LIGHT_PURPLE + somedata + "" + ChatColor.RED
							+ " is ready for /seeker in Survival!");
				}
			} else if (subChannel.equals("Seeker")) {
				if (SERVER_NAME.equals("Survival")) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "consoleseeker " + somedata);
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			Bukkit.getLogger().log(Level.INFO, "RunicUniverse: PluginMessageRecvd hit an NPE. Channel: " + channel + ". Subchannel: " + subChannel);
		} // Read the data in the same way you wrote it

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncPlayerChatEvent event) throws IOException {

		// CENSOR!
		ChatCensor.cleansePlayerChat(event);

		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("Forward"); // So BungeeCord knows to forward it
		out.writeUTF("ONLINE");
		out.writeUTF("Chat"); // The channel name to check if this your data

		ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
		DataOutputStream msgout = new DataOutputStream(msgbytes);

		String workaround;

		workaround = event.getFormat().replace("%2$s", "");

		msgout.writeUTF(workaround + event.getMessage()); // You can do anything
															// you want with
															// msgout
		msgout.writeShort(123);

		out.writeShort(msgbytes.toByteArray().length);
		out.write(msgbytes.toByteArray());

		event.getPlayer().sendPluginMessage(instance, "BungeeCord", out.toByteArray());
	}

	@EventHandler
	public void processServerWarpPortals(PlayerMoveEvent event) {

		// player has entered the portal in the Hub cave
		if (event.getTo().getWorld().getName().equalsIgnoreCase("world")) {
			if ((event.getTo().getX() >= 1779 && event.getTo().getX() <= 1781)
					&& (event.getTo().getY() >= 13 && event.getTo().getY() <= 14)
					&& (event.getTo().getZ() >= 344 && event.getTo().getZ() <= 346)) {

				if (event.getPlayer().hasPermission("ru.travel")) {

					event.getPlayer().teleport(new Location(event.getTo().getWorld(), 1791.273, 16.0, 345.160));
					showHubAstridTravelMenu(event.getPlayer());
				} else {
					event.getPlayer().sendMessage("Astrid rejects your request!");
					event.getPlayer().teleport(new Location(event.getTo().getWorld(), 1791.273, 16.0, 345.160));
				}
				// player has entered the portal in the skyblock area
			} else if ((event.getTo().getX() >= 1885 && event.getTo().getX() <= 1887)
					&& (event.getTo().getY() >= 95 && event.getTo().getY() <= 97)
					&& (event.getTo().getZ() >= 375 && event.getTo().getZ() <= 377)) {

				if (event.getPlayer().hasPermission("ru.travel")) {

					event.getPlayer().teleport(new Location(event.getTo().getWorld(), 1874.399, 96.93750, 379.541));
					showHubAstridTravelMenu(event.getPlayer());
				} else {
					event.getPlayer().sendMessage("Astrid rejects your request!");
					event.getPlayer().teleport(new Location(event.getTo().getWorld(), 1874.399, 96.93750, 379.541));
				}

			}

		} else if (event.getTo().getWorld().getName().equalsIgnoreCase("runicsky")) {
			if ((event.getTo().getX() >= -538 && event.getTo().getX() <= -536)
					&& (event.getTo().getY() >= 65 && event.getTo().getY() <= 67)
					&& (event.getTo().getZ() >= 188 && event.getTo().getZ() <= 190)) {

				if (event.getPlayer().hasPermission("ru.travel")) {

					event.getPlayer().teleport(new Location(event.getTo().getWorld(), -537.495, 65.0, 194.150));
					showHubAstridTravelMenu(event.getPlayer());
				} else {
					event.getPlayer().sendMessage("Astrid rejects your request!");
					event.getPlayer().teleport(new Location(event.getTo().getWorld(), -537.495, 65.0, 194.150));
				}

			}

		} else if (event.getTo().getWorld().getName().equalsIgnoreCase("skyspawn")) {
			if ((event.getTo().getX() >= -12 && event.getTo().getX() <= -9)
					&& (event.getTo().getY() >= 93 && event.getTo().getY() <= 95)
					&& (event.getTo().getZ() >= 45 && event.getTo().getZ() <= 49)) {

				if (event.getPlayer().hasPermission("ru.travel")) {

					event.getPlayer().teleport(new Location(event.getTo().getWorld(), -10.564, 93.93750, 42.684,
							(float) -179.2, (float) 1.4));
					showHubAstridTravelMenu(event.getPlayer());
				} else {
					event.getPlayer().sendMessage("Astrid rejects your request!");
					event.getPlayer().teleport(new Location(event.getTo().getWorld(), -10.564, 93.93750, 42.684,
							(float) -179.2, (float) 1.4));

				}

			}

		}

	}

	public static void showHubAstridTravelMenu(Player p) {

		Inventory faithInventory = Bukkit.createInventory(null, 36, ChatColor.DARK_PURPLE + "Astrid World Transport");

		ItemMeta meta;
		Random random = new Random();
		ArrayList<String> treeLore = new ArrayList<String>();
		treeLore.add(ChatColor.YELLOW + "Astrid is the great tree at the");
		treeLore.add(ChatColor.YELLOW + "heart of Runic Universe. She has");
		treeLore.add(ChatColor.YELLOW + "the power to send you between ");
		treeLore.add(ChatColor.YELLOW + "worlds. Just click where you ");
		treeLore.add(ChatColor.YELLOW + "want to go!");

		ItemStack tree = new ItemStack(Material.ACACIA_SAPLING, 1);
		meta = tree.getItemMeta();
		meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Astrid Warp Menu");
		meta.setLore(treeLore);
		tree.setItemMeta(meta);

		ItemStack flower1 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower1.getItemMeta();
		meta.setDisplayName("Astrid World Transport");
		flower1.setItemMeta(meta);
		ItemStack flower2 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower2.getItemMeta();
		meta.setDisplayName("");
		flower2.setItemMeta(meta);
		ItemStack flower3 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower3.getItemMeta();
		meta.setDisplayName("");
		flower3.setItemMeta(meta);
		ItemStack flower4 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower4.getItemMeta();
		meta.setDisplayName("");
		flower4.setItemMeta(meta);
		ItemStack flower5 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower5.getItemMeta();
		meta.setDisplayName("");
		flower5.setItemMeta(meta);
		ItemStack flower6 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower6.getItemMeta();
		meta.setDisplayName("");
		flower6.setItemMeta(meta);
		ItemStack flower7 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower7.getItemMeta();
		meta.setDisplayName("");
		flower7.setItemMeta(meta);
		ItemStack flower8 = new ItemStack(Material.ROSE_BUSH, 1);
		meta = flower8.getItemMeta();
		meta.setDisplayName("");
		flower8.setItemMeta(meta);

		faithInventory.setItem(0, flower1);
		faithInventory.setItem(1, flower2);
		faithInventory.setItem(2, flower3);
		faithInventory.setItem(3, flower4);
		faithInventory.setItem(4, tree);
		faithInventory.setItem(5, flower5);
		faithInventory.setItem(6, flower6);
		faithInventory.setItem(7, flower7);
		faithInventory.setItem(8, flower8);

		ItemStack skyblockIcon;
		ItemStack survivalIcon;
		ItemStack hubIcon;
		ItemStack creativeIcon;
		List<String> lore = new ArrayList<String>();

		survivalIcon = new ItemStack(Material.EMERALD_BLOCK, 1);
		meta = survivalIcon.getItemMeta();
		meta.setDisplayName("Survival World");
		survivalIcon.setItemMeta(meta);

		skyblockIcon = new ItemStack(Material.DIAMOND_BLOCK, 1);
		meta = skyblockIcon.getItemMeta();
		meta.setDisplayName("Skyblock World");
		skyblockIcon.setItemMeta(meta);

		hubIcon = new ItemStack(Material.REDSTONE_BLOCK, 1);
		meta = hubIcon.getItemMeta();
		meta.setDisplayName("Hub World");
		hubIcon.setItemMeta(meta);

		creativeIcon = new ItemStack(Material.GOLD_BLOCK, 1);
		meta = creativeIcon.getItemMeta();
		meta.setDisplayName("Creative World");
		creativeIcon.setItemMeta(meta);

		if (SERVER_NAME.equals("Hub")) {

			hubIcon = new ItemStack(Material.STONE, 1);
			meta = hubIcon.getItemMeta();
			meta.setDisplayName("Hub World");
			lore.add("You're already here!");
			meta.setLore(lore);
			hubIcon.setItemMeta(meta);
			lore = null;

		} else if (SERVER_NAME.equals("Skyblock")) {

			skyblockIcon = new ItemStack(Material.STONE, 1);
			meta = skyblockIcon.getItemMeta();
			meta.setDisplayName("Skyblock World");
			lore.add("You're already here!");
			meta.setLore(lore);
			skyblockIcon.setItemMeta(meta);

		} else if (SERVER_NAME.equals("Survival")) {

			survivalIcon = new ItemStack(Material.STONE, 1);
			meta = survivalIcon.getItemMeta();
			meta.setDisplayName("Survival World");
			lore.add("You're already here!");
			meta.setLore(lore);
			survivalIcon.setItemMeta(meta);
		} else if (SERVER_NAME.equals("Creative")) {

			creativeIcon = new ItemStack(Material.STONE, 1);
			meta = creativeIcon.getItemMeta();
			meta.setDisplayName("Creative World");
			lore.add("You're already here!");
			meta.setLore(lore);
			creativeIcon.setItemMeta(meta);
		}

		faithInventory.setItem(19, survivalIcon);
		faithInventory.setItem(21, skyblockIcon);
		faithInventory.setItem(23, creativeIcon);
		faithInventory.setItem(25, hubIcon);

		p.openInventory(faithInventory);

	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {

		if (event.getView().getTitle().contains("Astrid World Transport")) {

			// Survival
			if (event.getSlot() == 19 && !SERVER_NAME.equals("Survival")) {
				event.setCancelled(true);
				sendPlayerToServer("Survival", ((Player) event.getWhoClicked()));

				// Skyblock
			} else if (event.getSlot() == 21 && !SERVER_NAME.equals("Skyblock")) {
				event.setCancelled(true);
				sendPlayerToServer("Skyblock", ((Player) event.getWhoClicked()));

				// Creative
			} else if (event.getSlot() == 23 && !SERVER_NAME.equals("Creative")) {

				event.setCancelled(true);
				sendPlayerToServer("Creative", ((Player) event.getWhoClicked()));

				// Hub
			} else if (event.getSlot() == 25 && !SERVER_NAME.equals("Hub")) {

				event.setCancelled(true);
				sendPlayerToServer("Hub", ((Player) event.getWhoClicked()));

			}

			event.setCancelled(true);
		}

	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent pje) {
		pje.setJoinMessage("");
		checkStaffLevel(pje.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent pqe) {
		pqe.setQuitMessage("");
	}

	public void pushPlayerListToWeb() {

		final MySQL MySQL = new MySQL(instance, instance.getConfig().getString("dbHost"),
				instance.getConfig().getString("dbPort"), instance.getConfig().getString("dbDatabase"),
				instance.getConfig().getString("dbUser"), instance.getConfig().getString("dbPassword"));

		// Save online players to DB every minute
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.runTaskTimerAsynchronously(this, () -> {
			Connection z = MySQL.openConnection();
			List<UUID> PlayerIDs = new ArrayList<>();
			for (Player all : getServer().getOnlinePlayers()) {
				PlayerIDs.add(all.getUniqueId());
			}

			try {
				// clear the table
				Statement insertStmt = z.createStatement();
				insertStmt.executeUpdate("DELETE FROM rp_PlayersOnline WHERE Server = '"
						+ instance.getConfig().getString("ServerName") + "';");
			} catch (SQLException e) {
				getLogger().log(Level.SEVERE, "Could not reset PlayersOnline table! because: " + e.getMessage());
			}

			// Clear the table
			Date date = new Date();

			for (UUID uuid : PlayerIDs) {
				if (Bukkit.getPlayer(uuid).hasPermission("rp.admin")) {
					try {
						Statement insertStmt = z.createStatement();
						insertStmt.executeUpdate(
								"INSERT INTO rp_PlayersOnline (`PlayerName`, `UUID`, `Staff`, `Type`, `Timestamp`, `Server`) VALUES ('"
										+ Bukkit.getPlayer(uuid).getName() + "', '" + uuid + "', '1', 'Admin', '"
										+ date.getTime() + "', '" + instance.getConfig().getString("ServerName")
										+ "');");

					} catch (SQLException e) {
						getLogger().log(Level.SEVERE,
								"Could not update PlayersOnline ADMIN! because: " + e.getMessage());
					}

				} else if (getServer().getPlayer(uuid).hasPermission("rp.mod")) {
					try {
						Statement insertStmt = z.createStatement();
						insertStmt.executeUpdate(
								"INSERT INTO rp_PlayersOnline (`PlayerName`, `UUID`, `Staff`, `Type`, `Timestamp`, `Server`) VALUES ('"
										+ Bukkit.getPlayer(uuid).getName() + "', '" + uuid.toString()
										+ "', '1', 'Mod', '" + date.getTime() + "', '"
										+ instance.getConfig().getString("ServerName") + "');");

					} catch (SQLException e) {
						getLogger().log(Level.SEVERE,
								"Could not update PlayersOnline MOD! because: " + e.getMessage());
					}

				} else if (getServer().getPlayer(uuid).hasPermission("rp.staff.helper")) {
					try {
						Statement insertStmt = z.createStatement();
						insertStmt.executeUpdate(
								"INSERT INTO rp_PlayersOnline (`PlayerName`, `UUID`, `Staff`, `Type`, `Timestamp`, `Server`) VALUES ('"
										+ Bukkit.getPlayer(uuid).getName() + "', '" + uuid.toString()
										+ "', '1', 'Helper', '" + date.getTime() + "', '"
										+ instance.getConfig().getString("ServerName") + "');");

					} catch (SQLException e) {
						getLogger().log(Level.SEVERE, "Could not update PlayersOnline  because: " + e.getMessage());
					}

				} else {
					try {
						Statement insertStmt = z.createStatement();
						insertStmt.executeUpdate(
								"INSERT INTO rp_PlayersOnline (`PlayerName`, `UUID`, `Staff`, `Type`, `Timestamp`, `Server`) VALUES ('"
										+ Bukkit.getPlayer(uuid).getName() + "', '" + uuid.toString()
										+ "', '0', 'None', '" + date.getTime() + "', '"
										+ instance.getConfig().getString("ServerName") + "');");

					} catch (SQLException e) {
						getLogger().log(Level.SEVERE,
								"Could not update PlayersOnline nonstaff! because: " + e.getMessage());
					}

				}

			}

			try {

				z.close();
			} catch (SQLException e) {
				getLogger().log(Level.SEVERE,
						"Could not update close PlayersOnline update  conn! because: " + e.getMessage());
			}
		}, 0L, 1200L);

	}

	public void sendPlayerToServer(String serverName, Player player) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("Connect");
		out.writeUTF(serverName);

		// If you don't care about the player
		// Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(),
		// null);
		// Else, specify them

		player.sendPluginMessage(instance, "BungeeCord", out.toByteArray());
	}

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager()
				.getRegistration(net.milkbowl.vault.permission.Permission.class);
		perms = rsp.getProvider();
		return perms != null;
	}




	private void checkStaffLevel(Player p) {

		MySQL MySQL = new MySQL(instance, instance.getConfig().getString("dbHost"),
				instance.getConfig().getString("dbPort"), instance.getConfig().getString("dbDatabase"),
				instance.getConfig().getString("dbUser"), instance.getConfig().getString("dbPassword"));
		
		String rankCheck = "";

		try {
			// TODO: Get data from DB based on UUID to protect vs name changes
			final Connection d = MySQL.openConnection();
			Statement dStmt = d.createStatement();

			ResultSet playerData = dStmt.executeQuery("SELECT StaffRank FROM `rp_PlayerInfo` WHERE `UUID` = '"
					+ p.getUniqueId().toString() + "' ORDER BY `id` ASC LIMIT 1;");

			if (!playerData.isBeforeFirst()) {
				// Player doesn't exist in the DB!
				// TODO: Need to add them to DB!
			} else {
				// Player does exist in the DB
				playerData.next();
				rankCheck = playerData.getString("StaffRank");
				
				Bukkit.getLogger().log(Level.INFO, p.getName() + " rank check = " + rankCheck);

				if (rankCheck.equals("Admin") && !perms.playerInGroup(p, "Admins")) {
					
					RunicUniverse.perms.playerAddGroup(null, Bukkit.getOfflinePlayer(p.getUniqueId()), "Admins");
					RunicMessaging.sendMessage(p, RunicFormat.RANKS, "Promoting you to admin on this server!");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " promoting to Admin");
					
				} else if (rankCheck.equals("Director") && !perms.playerInGroup(p, "Director")) {
					
					RunicUniverse.perms.playerAddGroup(null, Bukkit.getOfflinePlayer(p.getUniqueId()), "Director");
					RunicMessaging.sendMessage(p, RunicFormat.RANKS, "Promoting you to director on this server!");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " promoting to Director");
					
				} else if (rankCheck.equals("Architect") && !perms.playerInGroup(p, "Architect")) {
					
					RunicUniverse.perms.playerAddGroup(null, Bukkit.getOfflinePlayer(p.getUniqueId()), "Architect");
					RunicMessaging.sendMessage(p, RunicFormat.RANKS, "Promoting you to architect on this server!");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " promoting to Architect");
					
				} else if (rankCheck.equals("Enforcer") && !perms.playerInGroup(p, "Enforcer")) {
					
					RunicUniverse.perms.playerAddGroup(null, Bukkit.getOfflinePlayer(p.getUniqueId()), "Enforcer");
					RunicMessaging.sendMessage(p, RunicFormat.RANKS, "Promoting you to enforcer on this server!");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " promoting to Enforcer");
					
				} else if (rankCheck.equals("Helper") && !perms.playerInGroup(p, "Helper")) {
					
					RunicUniverse.perms.playerAddGroup(null, Bukkit.getOfflinePlayer(p.getUniqueId()), "Helper");
					RunicMessaging.sendMessage(p, RunicFormat.RANKS, "Promoting you to helper on this server!");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " promoting to Helper");
				}
				
				if ((rankCheck.equals("Helper") || rankCheck.equals("Enforcer") || rankCheck.equals("Architect") || rankCheck.equals("Director") || rankCheck.equals("None")) && perms.playerInGroup(p, "Admins")) {
					perms.playerRemoveGroup(p, "Admins");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " removing from Admin");
				}
				
				if ((rankCheck.equals("Helper") || rankCheck.equals("Enforcer") || rankCheck.equals("Architect")|| rankCheck.equals("None")) && perms.playerInGroup(p, "Director")) {
					perms.playerRemoveGroup(p, "Director");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " removing from Director");
				}
				
				if ((rankCheck.equals("Helper") || rankCheck.equals("Enforcer") || rankCheck.equals("None")) && perms.playerInGroup(p, "Architect")) {
					perms.playerRemoveGroup(p, "Architect");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " removing from Architect");
				}
				
				if ((rankCheck.equals("Helper")|| rankCheck.equals("None")) && perms.playerInGroup(p, "Enforcer")) {
					perms.playerRemoveGroup(p, "Enforcer");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " removing from Enforcer");
				}
				
				if ((rankCheck.equals("None") && perms.playerInGroup(p, "Helper"))) {
					perms.playerRemoveGroup(p, "Helper");
					Bukkit.getLogger().log(Level.INFO, p.getName() + " removing from Helper");
				}
			}

		} catch (Exception e) {
		}
	}

}