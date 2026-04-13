package com.selah;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.selah.StatsManager.MemberStats;

public class PunishmentManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * A centralized method to handle punishments for infractions.
     * For now, it will just log the event to the console.
     * @param user The user who committed the infraction.
     * @param guild The guild where the infraction occurred.
     * @param reason A description of the infraction.
     */
    public static void invokePunishment(User user, Guild guild, String reason) {
        System.out.println("--- PUNISHMENT INVOKED ---");
        System.out.println("User: " + user.getName() + " (" + user.getId() + ")");
        System.out.println("Guild: " + guild.getName() + " (" + guild.getId() + ")");
        System.out.println("Reason: " + reason);
        System.out.println("--------------------------");

        String guildId = guild.getId();
        String userId = user.getId();
        StatsManager.ServerStats serverStats = StatsManager.liveStats.get(guildId);

        if (serverStats == null) {
            System.err.println("Server stats not found for guild: " + guild.getName());
            return;
        }

        MemberStats memberStats = serverStats.members.stream()
            .filter(member -> member.memberId.equals(userId))
            .findFirst()
            .orElseGet(() -> {
                MemberStats newMember = new MemberStats(user.getName(), userId);
                serverStats.members.add(newMember);
                return newMember;
            });

        // Increment infraction level and total infractions
        memberStats.infractionLevel++;
        memberStats.totalInfractions++;

        // Update last infraction date
        LocalDate now = LocalDate.now();
        memberStats.lastInfractionDate = now.format(DATE_FORMATTER);

        // Update or reset infraction level reduction date
        if (memberStats.infractionLevel > 0) {
            memberStats.infractionLevelReductionDate = now.plusDays(14).format(DATE_FORMATTER);
        } else {
            memberStats.infractionLevelReductionDate = null;
        }

        // Save updated stats
        StatsManager.saveServerStats(guildId);
    }
}
