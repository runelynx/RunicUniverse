package io.github.runelynx.runicuniverse;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class RunicUtilities {

	public static Boolean isInteger(String x) {

		if (x == null) {
			return false;
		}

		try {
			Integer.parseInt(x);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}

	}

	public static void fixGroupManager() {

		// save any pending changes
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Saving any pending permissions changes...");
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mansave");

		File userFile = new File(
				Bukkit.getServer().getPluginManager().getPlugin("GroupManager").getDataFolder().getAbsolutePath()
						+ "/worlds/runicrealm",
				"users.yml");

		FileConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);

		for (Player p : Bukkit.getOnlinePlayers()) {

			if (userConfig.contains("users." + p.getUniqueId().toString())) {
				// user has a UUID branch
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Found " + ChatColor.GREEN + "good" + ChatColor.AQUA + " record for " + p.getDisplayName());

				if (userConfig.contains("users." + p.getName())) {
					// user has a name branch too!
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Also found " + ChatColor.RED + "bad"
							+ ChatColor.AQUA + " record for " + p.getDisplayName());
					try {
						userConfig.getConfigurationSection("users").set(p.getName(), null);
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Removed " + ChatColor.RED + "bad"
								+ ChatColor.AQUA + " record for " + p.getDisplayName());

					} catch (Exception e) {
						e.printStackTrace();
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Failed to remove " + ChatColor.RED + "bad"
								+ ChatColor.AQUA + " record for " + p.getDisplayName());
					}

				}

			} else if (userConfig.contains("users." + p.getName())) {
				// user only has a name branch too!
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"sc Found " + ChatColor.GOLD + "OK" + ChatColor.AQUA + " record for " + p.getDisplayName());
			}

		}

		try {
			userConfig.save(userFile);
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Saving cleanup changes back to file...");
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Reloading permissions on server...");
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "manload RunicRealm");

		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sc Failed to save changes!");
		}

	}



}
