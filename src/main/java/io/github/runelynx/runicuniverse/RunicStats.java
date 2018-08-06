package io.github.runelynx.runicuniverse;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public class RunicStats {

	static void startScheduledStatTask_OnlinePlayers() {

		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();

		scheduler.runTaskTimerAsynchronously(RunicUniverse.getInstance(), new Runnable() {

			@Override
			public void run() {
				recordOnlinePlayers();
			}

		}, 0L, 6000L);

	}

	private static void recordOnlinePlayers() {

		int staffOnline = 0;
		int nonStaffOnline = 0;
		int totalPlayersOnline = 0;

		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.hasPermission("rp.staff")) {
				staffOnline++;
			} else {
				nonStaffOnline++;
			}
		}

		totalPlayersOnline = staffOnline + nonStaffOnline;

		Date date = new Date(); // given date
		Calendar calendar = GregorianCalendar.getInstance(); // creates a new
																// calendar
																// instance
		calendar.setTime(date); // assigns calendar to given date
		int hour = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h
														// format
		// calendar.get(Calendar.HOUR); // gets hour in 12h format
		int month = calendar.get(Calendar.MONTH) + 1; // gets month number, NOTE
													// this is zero based!
		int dateNum = calendar.get(Calendar.DATE);
		int year = calendar.get(Calendar.YEAR);
		int day = calendar.get(Calendar.DAY_OF_WEEK);
		int minute = calendar.get(Calendar.MINUTE);

		final MySQL MySQL = new MySQL(RunicUniverse.getInstance(),
				RunicUniverse.getInstance().getConfig().getString("dbHost"),
				RunicUniverse.getInstance().getConfig().getString("dbPort"),
				RunicUniverse.getInstance().getConfig().getString("dbDatabase"),
				RunicUniverse.getInstance().getConfig().getString("dbUser"),
				RunicUniverse.getInstance().getConfig().getString("dbPassword"));

		try {
			Connection z = MySQL.openConnection();
			Statement insertStmt = z.createStatement();
			insertStmt.executeUpdate("INSERT INTO rp_StatsPlayersOnline (`StaffOnline`, `NonStaffOnline`, `TotalOnline`,"
					+ " `Minute`, `Hour`, `DayOfWeek`, `DateOfMonth`, `Month`, `Year`, `Server`) VALUES (" + staffOnline
					+ ", " + nonStaffOnline + ", " + totalPlayersOnline + ", " + minute + ", " + hour + ", " + day + ", "+ dateNum
					+ ", " + month + ", " + year + ", '" + RunicUniverse.SERVER_NAME + "');");
			insertStmt.close();
			z.close();
		} catch (SQLException e) {
			Bukkit.getLogger().log(Level.SEVERE, "Could not update recordOnlinePlayers because: " + e.getMessage());
		}

	}
}
