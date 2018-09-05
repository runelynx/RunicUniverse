package io.github.runelynx.runicuniverse;

import io.github.runelynx.runicuniverse.RunicMessaging.RunicFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ChatCensor {
	public static ArrayList<String> censoredWords = new ArrayList<>();
	
	public static Boolean addBadWordToYML(String newBadWord, String changerName) {
		String editedBadWord;

		if (newBadWord.length() < 3) {
			return false;
		} else {
			editedBadWord = newBadWord.toLowerCase();
		}

		String filename = "RunicData.yml";
		File dataFile = new File("/home/AMP/.ampdata/instances/Survival/Minecraft/plugins", filename);
		FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		try {

			List<String> words = dataConfig.getStringList("Badwords");

			words.add(editedBadWord);
			dataConfig.set("Badwords", words);
			dataConfig.save(dataFile);

			Bukkit.getLogger().log(Level.INFO, changerName + " added bad word to the censor list: " + editedBadWord);

			censoredWords.add(editedBadWord);
			return true;

		} catch (IOException e) {
			Bukkit.getLogger().log(Level.WARNING,
					"FAILED: " + changerName + " added bad word to the censor list: " + editedBadWord);
			e.printStackTrace();
			return false;
		}
	}

	public static Boolean removeBadWordFromYML(String badWord, String changerName) {
		String filename = "RunicData.yml";
		File dataFile = new File("/home/AMP/.ampdata/instances/Survival/Minecraft/plugins", filename);
		FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		try {
			List<String> words = dataConfig.getStringList("Badwords");

			words.remove(badWord);
			dataConfig.set("Badwords", words);
			dataConfig.save(dataFile);

			Bukkit.getLogger().log(Level.INFO, changerName + " removed bad word from the censor list: " + badWord);
			censoredWords.remove(badWord);
			return true;

		} catch (IOException e) {
			Bukkit.getLogger().log(Level.WARNING,
					"FAILED: " + changerName + " removed bad word from the censor list: " + badWord);
			e.printStackTrace();
			return false;
		}
	}
	
	public static List<String> listBadWordsFromYML() {
		String filename = "RunicData.yml";
		File dataFile = new File("/home/AMP/.ampdata/instances/Survival/Minecraft/plugins", filename);
		FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		return dataConfig.getStringList("Badwords");

	}

	public static void cleansePlayerChat(AsyncPlayerChatEvent chat) {
		boolean censor = false;

		for (String bws : listBadWordsFromYML()) {
			if (bws.length() == 3) {
				bws = " " + bws + " ";
			}
			if (chat.getMessage().toLowerCase().contains(bws)) {
				censor = true;
				chat.setMessage(chat.getMessage().toLowerCase().replace(bws, "✗✗✗✗"));
			}
		}

		if (censor) {
			if (chat.getPlayer().hasPermission("rp.chatfilterwarning2")) {

				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"tempmute " + chat.getPlayer().getName() + " 3m Auto mute for vulgar chat after warning.");

				for (Player p : Bukkit.getOnlinePlayers()) {
					RunicMessaging.sendMessage(p, RunicMessaging.RunicFormat.JUSTICE,
							"Another criminal behind bars. Keep chat clean please!");
				}

			} else if (chat.getPlayer().hasPermission("rp.chatfilterwarning1")) {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"tempmute " + chat.getPlayer().getName() + " 1m Auto mute for vulgar chat after warning.");

				RunicUniverse.perms.playerAdd(chat.getPlayer(), "rp.chatfilterwarning2");
				RunicUniverse.perms.playerAdd(chat.getPlayer(), "-essentials.me");

				for (Player p : Bukkit.getOnlinePlayers()) {
					RunicMessaging.sendMessage(p, RunicFormat.JUSTICE, "Silence is golden. Keep chat clean!");
				}

			} else {
				RunicUniverse.perms.playerAdd(chat.getPlayer(), "rp.chatfilterwarning1");

				chat.getPlayer().sendMessage(ChatColor.BOLD + "" + ChatColor.DARK_RED
						+ "This is a family-friendly server, do not use vulgar language here! Repeat offenders will be punished.");

			}
		}
	}
}
