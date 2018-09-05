package io.github.runelynx.runicuniverse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import static org.bukkit.ChatColor.*;

public class RunicMessaging {

	private static int AnnTask = 0;

	public enum RunicFormat {
		AFTERLIFE(DARK_RED + "Runic" + DARK_GRAY + "Afterlife" + GRAY + " > " + GRAY),
		BORDERLANDS(DARK_RED + "Border" + DARK_PURPLE + "Lands" + GRAY + " > " + AQUA),
		FAITH(LIGHT_PURPLE + "RunicFaith" + GRAY + " > " + GRAY),
		CASINO(GOLD + "RunicCasino" + GRAY + " > " + GRAY),
		RANKS(GREEN + "RunicRanks" + GRAY + " > " + GRAY),
		EXPLORER(YELLOW + "ExplorersLeague" + GRAY + " > " + GRAY),
		EMPTY(GRAY + ""),
		SYSTEM(DARK_AQUA + "RunicEngine" + GRAY + " > " + GRAY),
		RAFFLE(LIGHT_PURPLE + "Runic" + YELLOW + "Raffle" + GRAY + " > "
				+ GRAY),
		HELP(BLUE + "Runic" + AQUA + "Help" + GRAY + " > " + GRAY),
		ERROR(DARK_RED + "Error" + RED + " > " + GRAY),
		JUSTICE(YELLOW + "Runic" + GOLD + "Justice" + GRAY + " > " + GRAY);
		private String text;

		RunicFormat(String txt) {
			this.text = txt;
		}
	}

	public static void sendMessage(Player p, RunicFormat format, String msg) {
		p.sendMessage(format.text + msg);
	}

	private final static ChatColor[] COLORS = new ChatColor[] {
		BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD,
			GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE
	};

	public static ChatColor getRandomColor() {
		return COLORS[ThreadLocalRandom.current().nextInt(COLORS.length)];
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
					"sc " + p.getDisplayName() + AQUA + " updated announcement " + i + AQUA
							+ " to: " + translateAlternateColorCodes('&', text));
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
					"sc " + p.getDisplayName() + AQUA + " toggled announcement " + i);
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
					DARK_RED + "Announcement delay not changed. Invalid number given. (Must be 1 - 59)");
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
					"sc " + p.getDisplayName() + AQUA + " changed announcement delay");
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

		p.sendMessage(DARK_PURPLE + "RunicAnnouncer" + GRAY + ">"
				+ " Listing messages. Number color = on/off.");

		p.sendMessage(DARK_GRAY + "Current message delay: " + GRAY
				+ announceConfig.getString("General.MinutesBetweenMessages"));

		while (count < 10) {
			if (announceConfig.get("Message" + count + ".Status").equals("Enabled")) {
				statusColor = DARK_GREEN;
			} else {
				statusColor = DARK_RED;
			}

			p.sendMessage(DARK_GRAY + "[" + statusColor + count + DARK_GRAY + "] " + translateAlternateColorCodes('&', announceConfig.get("Message" + count + ".Text").toString()));
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

				// Before starting a new repeating task, let's kill any existing
				// one
				cancelRepeatingTask();

				BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
				taskID = scheduler.scheduleAsyncRepeatingTask(instance, RunicMessaging::playAnnouncement, 0L, delayLong);

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

					p.sendMessage(DARK_GRAY + "*** " + RESET
							+ translateAlternateColorCodes('&',
									announceConfig.getString("Message" + getNextMsg + ".Text"))
							+ DARK_GRAY + " ***");
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
