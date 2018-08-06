package io.github.runelynx.runicuniverse;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

public class RunicMessaging {

	private static int AnnTask = 0;

	public enum RunicFormat {
		AFTERLIFE(ChatColor.DARK_RED + "Runic" + ChatColor.DARK_GRAY + "Afterlife" + ChatColor.GRAY + " > " + ChatColor.GRAY),
		BORDERLANDS(ChatColor.DARK_RED + "Border" + ChatColor.DARK_PURPLE + "Lands" + ChatColor.GRAY + " > " + ChatColor.AQUA),
		FAITH(ChatColor.LIGHT_PURPLE + "RunicFaith" + ChatColor.GRAY + " > " + ChatColor.GRAY), 
		CASINO(ChatColor.GOLD + "RunicCasino" + ChatColor.GRAY + " > " + ChatColor.GRAY), 
		RANKS(
						ChatColor.GREEN + "RunicRanks" + ChatColor.GRAY + " > " + ChatColor.GRAY), 
		EXPLORER(
								ChatColor.YELLOW + "ExplorersLeague" + ChatColor.GRAY + " > " + ChatColor.GRAY), 
		EMPTY(
										ChatColor.GRAY + ""), 
		SYSTEM(ChatColor.DARK_AQUA + "RunicEngine"
												+ ChatColor.GRAY + " > " + ChatColor.GRAY), 
		RAFFLE(ChatColor.LIGHT_PURPLE
														+ "Runic" + ChatColor.YELLOW + "Raffle" + ChatColor.GRAY + " > "
														+ ChatColor.GRAY), 
		HELP(ChatColor.BLUE + "Runic"
																+ ChatColor.AQUA + "Help" + ChatColor.GRAY + " > "
																+ ChatColor.GRAY), 
		ERROR(
																		ChatColor.DARK_RED + "Error" + ChatColor.RED
																				+ " > " + ChatColor.GRAY), 
		JUSTICE(
																						ChatColor.YELLOW + "Runic"
																								+ ChatColor.GOLD
																								+ "Justice"
																								+ ChatColor.GRAY + " > "
																								+ ChatColor.GRAY);
		private String text;

		private RunicFormat(String txt) {
			this.text = txt;
		}
	}

	public static void sendMessage(Player p, RunicFormat format, String msg) {

		p.sendMessage(format.text + msg);

	}

	public static ChatColor getRandomColor() {
		Random rand = new Random();

		int n = rand.nextInt(14) + 1;
		// 50 is the maximum and the 1 is our minimum

		switch (n) {
		case 1:
			return ChatColor.AQUA;
		case 2:
			return ChatColor.BLUE;
		case 3:
			return ChatColor.DARK_AQUA;
		case 4:
			return ChatColor.DARK_BLUE;
		case 5:
			return ChatColor.DARK_GRAY;
		case 6:
			return ChatColor.DARK_GREEN;
		case 7:
			return ChatColor.DARK_PURPLE;
		case 8:
			return ChatColor.DARK_RED;
		case 9:
			return ChatColor.GOLD;
		case 10:
			return ChatColor.GRAY;
		case 11:
			return ChatColor.GREEN;
		case 12:
			return ChatColor.LIGHT_PURPLE;
		case 13:
			return ChatColor.RED;
		case 14:
			return ChatColor.YELLOW;
		default:
			break;

		}

		return ChatColor.WHITE;
	}

	public static void setAnnouncement(Player p, int i, String text) {
		if (i < 1 || i > 9) {
			RunicMessaging.sendMessage(p, RunicFormat.SYSTEM, "Invalid message number. Use 1 - 9");
			return;
		}

		if (text == null) {
			RunicMessaging.sendMessage(p, RunicFormat.SYSTEM, "Message cannot be empty. Maybe just disable it?");
			return;
		}

		try {
			File announceFile = new File(
					Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
					"announcements.yml");

			FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

			String messageRef1 = "Message" + i + ".Text";
			String messageRef2 = "Message" + i + ".UpdatedBy";
			String messageRef3 = "Message" + i + ".UpdatedAt";

			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			// System.out.println(dateFormat.format(date)); //2014/08/06
			// 15:59:48

			announceConfig.set(messageRef1, text);
			announceConfig.set(messageRef2, p.getName());
			announceConfig.set(messageRef3, dateFormat.format(date));
			announceConfig.save(announceFile);

			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc " + p.getDisplayName() + ChatColor.AQUA + " updated announcement " + i + ChatColor.AQUA
							+ " to: " + ChatColor.translateAlternateColorCodes('&', text));
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc Couldn't update announcement" + i + " to " + text + " because... " + e.getMessage());
		}

	}

	public static void toggleAnnouncement(Player p, int i) {
		if (i < 1 || i > 9) {
			RunicMessaging.sendMessage(p, RunicFormat.ERROR, "Invalid message number. Use 1 - 9");
			return;
		}

		String newStatus = "";

		try {
			File announceFile = new File(
					Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
					"announcements.yml");

			FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

			if (announceConfig.getString("Message" + i + ".Status").equals("Enabled")) {
				// message is currently enabled
				newStatus = "Disabled";
			} else {
				// message is currently disabled
				newStatus = "Enabled";
			}

			String messageRef1 = "Message" + i + ".Status";
			String messageRef2 = "Message" + i + ".UpdatedBy";
			String messageRef3 = "Message" + i + ".UpdatedAt";

			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			// System.out.println(dateFormat.format(date)); //2014/08/06
			// 15:59:48

			announceConfig.set(messageRef1, newStatus);
			announceConfig.set(messageRef2, p.getName());
			announceConfig.set(messageRef3, dateFormat.format(date));
			announceConfig.save(announceFile);
			
			RunicMessaging.sendMessage(p, RunicFormat.SYSTEM, "Toggled announcement " + i);

			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc " + p.getDisplayName() + ChatColor.AQUA + " toggled announcement " + i);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc Couldn't toggle announcement" + i + " to " + newStatus + " because... " + e.getMessage());
		}

	}

	public static void setAnnouncementDelay(Player p, int x) {

		if (!(x > 0 && x < 60)) {
			// Invalid delay! Cancel!
			RunicMessaging.sendMessage(p, RunicFormat.SYSTEM,
					ChatColor.DARK_RED + "Announcement delay not changed. Invalid number given. (Must be 1 - 59)");
			return;
		}

		try {
			File announceFile = new File(
					Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
					"announcements.yml");
			FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

			announceConfig.set("General.MinutesBetweenMessages", x);

			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			// System.out.println(dateFormat.format(date)); //2014/08/06
			// 15:59:48

			announceConfig.set("General.DelayUpdatedBy", p.getName());
			announceConfig.set("General.DelayUpdatedAt", dateFormat.format(date));

			announceConfig.save(announceFile);

			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc " + p.getDisplayName() + ChatColor.AQUA + " changed announcement delay");
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"sc Couldn't change announcement delay because... " + e.getMessage());
		}

		initializeAnnouncements(RunicUniverse.getInstance());

	}

	public static void listAnnouncements(Player p) {

		File announceFile = new File(
				Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
				"announcements.yml");

		FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

		int count = 1;
		ChatColor statusColor;

		p.sendMessage(ChatColor.DARK_PURPLE + "RunicAnnouncer" + ChatColor.GRAY + ">"
				+ " Listing messages. Number color = on/off.");

		p.sendMessage(ChatColor.DARK_GRAY + "Current message delay: " + ChatColor.GRAY
				+ announceConfig.getString("General.MinutesBetweenMessages"));

		while (count < 10) {

			if (announceConfig.get("Message" + count + ".Status").equals("Enabled")) {
				statusColor = ChatColor.DARK_GREEN;
			} else {
				statusColor = ChatColor.DARK_RED;
			}

			p.sendMessage(ChatColor.DARK_GRAY + "[" + statusColor + count + ChatColor.DARK_GRAY + "] " + ChatColor
					.translateAlternateColorCodes('&', announceConfig.get("Message" + count + ".Text").toString()));
			count++;

		}

	}

	@SuppressWarnings("deprecation")
	public static void initializeAnnouncements(Plugin instance) {

		File announceFile = new File(
				Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
				"announcements.yml");

		if (announceFile.exists()) {
			// found the file!
			// Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Found the
			// announcements file :) ");

			try {
				announceFile.createNewFile();
				FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

				int delayMins = announceConfig.getInt("General.MinutesBetweenMessages");
				long delayLong = 20 * 60 * delayMins;
				int taskID;

				int countValidMessages = 0;

				// Before starting a new repeating task, let's kill any existing
				// one
				cancelRepeatingTask();

				BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
				taskID = scheduler.scheduleAsyncRepeatingTask(instance, new Runnable() {
					@Override
					public void run() {
						RunicMessaging.playAnnouncement();
					}
				}, 0L, delayLong);

				AnnTask = taskID;

				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Announcements will play every " + delayMins + " minutes. (" + taskID + ")");

			} catch (IOException e) {
				e.printStackTrace();
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Couldn't read the announcements file ... " + e.getMessage());
			}

		} else {
			// couldn't find the file :(
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Couldn't find the announcements file :( ");

			try {
				announceFile.createNewFile();
				FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

				announceConfig.set("General.MinutesBetweenMessages", "10");

				announceConfig.set("Message1.Status", "Disabled");
				announceConfig.set("Message1.Text", "Not set yet");
				announceConfig.set("Message1.LastPlayed", "0");

				announceConfig.set("Message2.Status", "Disabled");
				announceConfig.set("Message2.Text", "Not set yet");
				announceConfig.set("Message2.LastPlayed", "0");

				announceConfig.set("Message3.Status", "Disabled");
				announceConfig.set("Message3.Text", "Not set yet");
				announceConfig.set("Message3.LastPlayed", "0");

				announceConfig.set("Message4.Status", "Disabled");
				announceConfig.set("Message4.Text", "Not set yet");
				announceConfig.set("Message4.LastPlayed", "0");

				announceConfig.set("Message5.Status", "Disabled");
				announceConfig.set("Message5.Text", "Not set yet");
				announceConfig.set("Message5.LastPlayed", "0");

				announceConfig.set("Message6.Status", "Disabled");
				announceConfig.set("Message6.Text", "Not set yet");
				announceConfig.set("Message6.LastPlayed", "0");

				announceConfig.set("Message7.Status", "Disabled");
				announceConfig.set("Message7.Text", "Not set yet");
				announceConfig.set("Message7.LastPlayed", "0");

				announceConfig.set("Message8.Status", "Disabled");
				announceConfig.set("Message8.Text", "Not set yet");
				announceConfig.set("Message8.LastPlayed", "0");

				announceConfig.set("Message9.Status", "Disabled");
				announceConfig.set("Message9.Text", "Not set yet");
				announceConfig.set("Message9.LastPlayed", "0");

				announceConfig.save(announceFile);

				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Created the announcements file! :)");
			} catch (IOException e) {
				e.printStackTrace();
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Couldn't create the announcements file ... " + e.getMessage());
			}

		}

	}

	public static void playAnnouncement() {

		File announceFile = new File(
				Bukkit.getServer().getPluginManager().getPlugin("RunicUniverse").getDataFolder().getAbsolutePath(),
				"announcements.yml");

		if (announceFile.exists()) {
			// found the file!
			// Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Found the
			// announcements file :) ");

			int msgCnt = 1;
			long lastPlayedCheck = new Date().getTime();
			int getNextMsg = 0;

			try {
				announceFile.createNewFile();
				FileConfiguration announceConfig = YamlConfiguration.loadConfiguration(announceFile);

				// At the end of this while loop, getNextmsg should = the
				// enabled message that was played longest ago.
				while (msgCnt < 10) {

					if (announceConfig.getString("Message" + msgCnt + ".Status").equalsIgnoreCase("Enabled")
							&& announceConfig.getLong("Message" + msgCnt + ".LastPlayed") < lastPlayedCheck) {
						lastPlayedCheck = announceConfig.getLong("Message" + msgCnt + ".LastPlayed");
						getNextMsg = msgCnt;
					}

					msgCnt++;
				}

				if (getNextMsg == 0) {
					// No valid messages were found (they're all disabled?)
					// Abort!
					cancelRepeatingTask();
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
							"sc All announcements are disabled; halting scheduled task.");
					return;
				}

				// Update the last played date and play the message
				announceConfig.set("Message" + getNextMsg + ".LastPlayed", new Date().getTime());
				announceConfig.save(announceFile);

				for (Player p : Bukkit.getOnlinePlayers()) {

					p.sendMessage(ChatColor.DARK_GRAY + "*** " + ChatColor.RESET
							+ ChatColor.translateAlternateColorCodes('&',
									announceConfig.getString("Message" + getNextMsg + ".Text"))
							+ ChatColor.DARK_GRAY + " ***");
				}

			} catch (IOException e) {
				e.printStackTrace();
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Couldn't retrieve the announcements file ... " + e.getMessage());
			}

		}
	}

	public static void cancelRepeatingTask() {

		if (AnnTask != 0) {
			// if AnnTask=0, it suggests that the repeating task never
			// successfully registered its ID ... and thus it should not be
			// running.
			Bukkit.getScheduler().cancelTask(AnnTask);
		}
	}

}
