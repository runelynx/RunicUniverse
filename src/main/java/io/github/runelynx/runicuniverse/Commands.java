package io.github.runelynx.runicuniverse;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.github.runelynx.runicuniverse.RunicMessaging.RunicFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * 
 * @author Andrewxwsaa
 */
public class Commands implements CommandExecutor {

	// pointer to your main class, not required if you don't need methods fromfg
	// the main class
	private Plugin instance = RunicUniverse.getInstance();

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// TODO Auto-generated method stub

		switch (cmd.getName()) {
		case "evacuate":
			if (sender.hasPermission("rp.staff.evacuate")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					goToServer("Hub", p);
				}
			}
		case "staffrank":
			if (args.length == 2) {
				MySQL MySQL = new MySQL(instance, instance.getConfig().getString("dbHost"),
						instance.getConfig().getString("dbPort"), instance.getConfig().getString("dbDatabase"),
						instance.getConfig().getString("dbUser"), instance.getConfig().getString("dbPassword"));

				if (!args[1].equals("None") && !args[1].equals("Helper") && !args[1].equals("Enforcer")
						&& !args[1].equals("Architect") && !args[1].equals("Director") && !args[1].equals("Admin")) {
					// bad syntax
					RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
							"Bad syntax! /staffrank <player> <Admin/Director/Architect/Enforcer/Helper/None>");
					RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
							"Rank name must by typed exactly as shown here. Case sensitive!");

				} else {
					Connection z = MySQL.openConnection();
					try {
						//
						Statement insertStmt = z.createStatement();
						insertStmt.executeUpdate("UPDATE rp_PlayerInfo SET StaffRank = '" + args[1]
								+ "' WHERE PlayerName = '" + args[0] + "';");

						z.close();
					} catch (SQLException e) {
						Bukkit.getLogger().log(Level.SEVERE, "Could not update staff rank - " + e.getMessage());
					}
					RunicMessaging.sendMessage((Player) sender, RunicFormat.RANKS,
							"Saved " + args[0] + "'s staff rank as " + args[1]);
				}
			} else {
				RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
						"Bad syntax! /staffrank <player> <Admin/Director/Architect/Enforcer/Helper/None>");
			}
			return true;
		case "censor":
			if (args.length > 0) {
				switch (args[0]) {
					case "RC":
					case "rc":
						if (sender.hasPermission("rp.staff.director")) {
							// Remove censored word
							if (ChatCensor.removeBadWordFromYML(args[1], sender.getName())) {
								// Success
								RunicMessaging.sendMessage((Player) sender, RunicFormat.JUSTICE,
										args[1] + " has been removed from the censorship list.");
							} else {
								// Failure
								RunicMessaging.sendMessage((Player) sender,
										RunicFormat.ERROR,
										"Something went wrong.");
							}
						} else {
							RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
									"Only directors+ can do that.");
						}
						break;
					case "AC":
					case "ac":
						if (sender.hasPermission("rp.staff.director")) {
							// Add censored word
							if (ChatCensor.addBadWordToYML(args[1], sender.getName())) {
								// Success
								RunicMessaging.sendMessage((Player) sender, RunicFormat.JUSTICE,
										args[1] + " has been added to the censorship list.");
							} else {
								// Failure
								RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
										"Something went wrong. Maybe that word isn't on the list?");
							}
						} else {
							RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
									"Only directors+ can do that.");
						}
						break;
					case "LC":
					case "lc":
						if (sender.hasPermission("rp.staff.director")) {
							RunicMessaging.sendMessage((Player) sender, RunicFormat.JUSTICE,
									"Current censorship list:");
							RunicMessaging.sendMessage((Player) sender, RunicFormat.EMPTY,
									ChatCensor.listBadWordsFromYML().toString());
						} else {
							RunicMessaging.sendMessage((Player) sender, RunicFormat.ERROR,
									"Only directors+ can do that.");
						}
						break;
					default:
						if (sender.hasPermission("rp.staff.director")) {
							sender.sendMessage(ChatColor.AQUA + "/censor lc" + ChatColor.GRAY + " List censored words");
							sender.sendMessage(
									ChatColor.AQUA + "/censor ac <badword>" + ChatColor.GRAY + " Add censored word");
							sender.sendMessage(
									ChatColor.AQUA + "/censor rc <badword>" + ChatColor.GRAY + " Remove censored word");
						}
						break;
				}
			} else {
				if (sender.hasPermission("rp.staff.director")) {
					sender.sendMessage(ChatColor.AQUA + "/censor lc" + ChatColor.GRAY + " List censored words");
					sender.sendMessage(ChatColor.AQUA + "/censor ac <badword>" + ChatColor.GRAY + " Add censored word");
					sender.sendMessage(
							ChatColor.AQUA + "/censor rc <badword>" + ChatColor.GRAY + " Remove censored word");
				}

			}
			return true;
		case "announce":
			if (args.length > 0) {
				if (args[0].equals("RA") || args[0].equals("ra")) {
					RunicMessaging.initializeAnnouncements(instance);
				} else if (args[0].equals("CD") || args[0].equals("cd")) {
					RunicMessaging.setAnnouncementDelay(((Player) sender), Integer.parseInt(args[1]));
				} else if (args[0].equals("SA") || args[0].equals("sa")) {
					if (args.length < 3) {
						sender.sendMessage("Incorrect command usage. Not enough parameters.");
						sender.sendMessage("/staff sa <msg #> <new text>");
						return true;
					}

					if (!RunicUtilities.isInteger(args[1])) {
						sender.sendMessage("Incorrect command usage.");
						sender.sendMessage("/staff sa <msg #> <new text>");
						return true;
					}

					StringBuilder buildTemp = new StringBuilder();

					for (int ii = 2; ii < args.length; ii++) {
						String arg = args[ii] + " ";
						buildTemp.append(arg);
					}
					RunicMessaging.setAnnouncement(((Player) sender), Integer.parseInt(args[1]), buildTemp.toString());

				} else if (args[0].equals("TA") || args[0].equals("ta")) {
					if (!(args.length == 2)) {
						sender.sendMessage("Incorrect command usage.");
						sender.sendMessage("/staff ta <msg #>");
						return true;
					}

					if (!RunicUtilities.isInteger(args[1])) {
						sender.sendMessage("Incorrect command usage.");
						sender.sendMessage("/staff ta <msg #>");
						return true;
					}

					RunicMessaging.toggleAnnouncement(((Player) sender), Integer.parseInt(args[1]));

				} else if (args[0].equalsIgnoreCase("LA")) {
					RunicMessaging.listAnnouncements(((Player) sender));
				} else {
					sender.sendMessage(ChatColor.BLUE + "Announcement Messages");

					sender.sendMessage(ChatColor.AQUA + "/announce la" + ChatColor.GRAY + " Lists announcements");
					sender.sendMessage(ChatColor.AQUA + "/announce sa <#> <text...>" + ChatColor.GRAY
							+ " Changes announcement text");
					sender.sendMessage(
							ChatColor.AQUA + "/announce ta <#>" + ChatColor.GRAY + " Toggles an announcement");
					sender.sendMessage(
							ChatColor.AQUA + "/announce cd <#>" + ChatColor.GRAY + " Changes announcement delay");
					sender.sendMessage(
							ChatColor.AQUA + "/announce ra" + ChatColor.GRAY + " Reloads announcements file");
				}
			} else {
				sender.sendMessage(ChatColor.BLUE + "Announcement Messages");

				sender.sendMessage(ChatColor.AQUA + "/announce la" + ChatColor.GRAY + " Lists announcements");
				sender.sendMessage(
						ChatColor.AQUA + "/announce sa <#> <text...>" + ChatColor.GRAY + " Changes announcement text");
				sender.sendMessage(ChatColor.AQUA + "/announce ta <#>" + ChatColor.GRAY + " Toggles an announcement");
				sender.sendMessage(
						ChatColor.AQUA + "/announce cd <#>" + ChatColor.GRAY + " Changes announcement delay");
				sender.sendMessage(ChatColor.AQUA + "/announce ra" + ChatColor.GRAY + " Reloads announcements file");
			}

			return true;
		case "goto":
		case "travel":
			RunicUniverse.showHubAstridTravelMenu(((Player) sender));
			return true;
		case "runicholo":
			double height = 0.0;
			if (args.length > 0) {
				if (args[0].equalsIgnoreCase("create")) {
					switch (args[1]) {
						case "1":
							height = 0.5;
							break;
						case "2":
							height = 0.75;
							break;
						case "3":
							height = 1.0;
							break;
						default:
							break;
					}

					ArmorStand a = ((Player) sender).getLocation().getWorld()
							.spawn(((Player) sender).getLocation().subtract(0, height, 0), ArmorStand.class);
					a.setGravity(false);
					a.setVisible(false);
					a.setCustomName(ChatColor.translateAlternateColorCodes('&', args[2].replace('_', ' ')));
					a.setCustomNameVisible(true);
					return true;
				} else if (args[0].equalsIgnoreCase("show")) {
					for (Entity e : ((Player) sender).getNearbyEntities(2, 2, 2)) {
						if (e.getType() == EntityType.ARMOR_STAND) {
							((ArmorStand) e).setVisible(true);
						}
					}
					return true;
				} else if (args[0].equalsIgnoreCase("hide")) {
					for (Entity e : ((Player) sender).getNearbyEntities(2, 2, 2)) {
						if (e.getType() == EntityType.ARMOR_STAND) {
							((ArmorStand) e).setVisible(false);
						}
					}
					return true;
				}
			}

			// If it gets here it's always error
			sender.sendMessage(ChatColor.DARK_PURPLE + "Rune's Handy Dandy Hologram Help:");
			sender.sendMessage("/runicholo create 1 TopLine_Hologram");
			sender.sendMessage("/runicholo create 2 MidLine_Hologram");
			sender.sendMessage("/runicholo create 3 BottomLine_Hologram");
			sender.sendMessage("/runicholo show (Reveal nearby hologram stand)");
			sender.sendMessage("/runicholo hide (Hide nearby hologram stand)");
			sender.sendMessage(ChatColor.LIGHT_PURPLE + "No spaces in holograms! Use underscore!");
			sender.sendMessage(ChatColor.LIGHT_PURPLE + "Color codes work!! /info colors");
			sender.sendMessage(ChatColor.LIGHT_PURPLE + "Only use holograms in areas OK'd by an Admin!");
			return true;
		}

		return false;
	}

	public void goToServer(String serverName, Player player) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("Connect");
		out.writeUTF(serverName);

		player.sendPluginMessage(instance, "BungeeCord", out.toByteArray());
	}

}
