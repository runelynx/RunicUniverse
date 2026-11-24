package io.github.runelynx.runicuniverse.staff;

import io.github.runelynx.runicuniverse.RunicUniverse;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Staff audit log: every sensitive /rs action is written to plugins/RunicUniverse/audit.log and, if configured,
 * posted to the staff audit Discord channel through a webhook (config key AuditWebhook; blank = file only).
 */
public final class Audit {

	private Audit() {}

	public static void log(CommandSender who, String action, String details) {
		Plugin plugin = RunicUniverse.getInstance();
		String server = RunicUniverse.SERVER_NAME;
		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String line = time + " [" + server + "] " + who.getName() + " | " + action + " | " + details;
		String webhook = plugin.getConfig().getString("AuditWebhook", "");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(plugin.getDataFolder(), "audit.log"), true),
					StandardCharsets.UTF_8)) {
				w.write(line + System.lineSeparator());
			} catch (IOException e) {
				plugin.getLogger().warning("[rs] audit log write failed: " + e.getMessage());
			}
			if (webhook != null && webhook.startsWith("https://")) {
				post(plugin, webhook, "**" + server + "** · `" + who.getName() + "` · **" + action + "** · " + details);
			}
		});
	}

	private static void post(Plugin plugin, String url, String content) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
			c.setRequestMethod("POST");
			c.setDoOutput(true);
			c.setConnectTimeout(5000);
			c.setReadTimeout(5000);
			c.setRequestProperty("Content-Type", "application/json");
			c.setRequestProperty("User-Agent", "RunicAudit (local, 1.0)");
			String body = "{\"username\":\"Runic Staff Audit\",\"allowed_mentions\":{\"parse\":[]},\"content\":\""
					+ json(content.length() > 1900 ? content.substring(0, 1900) + "…" : content) + "\"}";
			try (OutputStream os = c.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}
			int code = c.getResponseCode();
			if (code >= 300) {
				plugin.getLogger().warning("[rs] audit webhook returned HTTP " + code);
			}
			c.disconnect();
		} catch (IOException e) {
			plugin.getLogger().warning("[rs] audit webhook failed: " + e.getMessage());
		}
	}

	private static String json(String s) {
		StringBuilder b = new StringBuilder();
		for (char ch : s.toCharArray()) {
			switch (ch) {
				case '"': b.append("\\\""); break;
				case '\\': b.append("\\\\"); break;
				case '\n': b.append("\\n"); break;
				case '\r': break;
				case '\t': b.append("\\t"); break;
				default:
					if (ch < 0x20) {
						b.append(String.format("\\u%04x", (int) ch));
					} else {
						b.append(ch);
					}
			}
		}
		return b.toString();
	}
}
