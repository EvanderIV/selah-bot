package com.selah;

import java.util.List;
import java.util.stream.Collectors;

public class MonitorManager {

    private static volatile boolean isMonitoring = false;
    private static Thread monitorThread;

    /**
     * Starts the monitor in a background thread that displays channels or users
     * sorted from least to most "hot" (ascending order by heat).
     * Updates after every message is processed.
     * 
     * @param serverId The server to monitor
     * @param monitorType "channels" or "users"
     */
    public static void startMonitor(String serverId, String monitorType) {
        isMonitoring = true;

        monitorThread = new Thread(() -> {
            while (isMonitoring) {
                displayMonitor(serverId, monitorType);
                
                try {
                    // Refresh display every 500ms, or sooner if interrupted
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (!isMonitoring) break; // Exit if monitoring was stopped
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Stops the monitor thread
     */
    public static void stopMonitor() {
        isMonitoring = false;
        if (monitorThread != null) {
            try {
                monitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Displays the monitor output for channels or users
     */
    private static void displayMonitor(String serverId, String monitorType) {
        StatsManager.ServerStats stats = StatsManager.liveStats.get(serverId);
        if (stats == null) {
            System.out.println("ERROR: Server ID " + serverId + " not found in statistics.");
            return;
        }

        StringBuilder output = new StringBuilder();
        
        // Move cursor to home and clear screen on first run
        output.append("\033[H\033[2J");
        output.append("╔════════════════════════════════════════════════════════════════╗\n");
        output.append("║ MONITOR MODE - " + stats.serverName + " (" + monitorType.toUpperCase() + ")\n");
        output.append("╚════════════════════════════════════════════════════════════════╝\n");
        output.append("\n");

        if ("channels".equalsIgnoreCase(monitorType)) {
            buildChannelMonitor(stats, output);
        } else if ("users".equalsIgnoreCase(monitorType)) {
            buildUserMonitor(stats, output);
        } else if ("unified".equalsIgnoreCase(monitorType)) {
            buildUnifiedMonitor(stats, output);
        } else {
            output.append("ERROR: Invalid monitor type. Use 'channels' or 'users'.\n");
        }

        output.append("\n");
        output.append("Total Messages Analyzed: " + stats.totalMessagesAnalyzed + "\n");
        output.append("Total Infractions: " + stats.totalInfractions + "\n");
        output.append("\n");
        output.append("[Press Ctrl+C to exit monitor mode]\n");
        
        System.out.print(output.toString());
        System.out.flush();
    }

    private static void buildUnifiedMonitor(StatsManager.ServerStats stats, StringBuilder output) {
        if ((stats.channels == null || stats.channels.isEmpty()) && (stats.members == null || stats.members.isEmpty())) {
            output.append("No channels or user data available yet.\n");
            return;
        }

        List<StatsManager.ChannelStats> sortedChannels = stats.channels.values().stream()
                .sorted((a, b) -> Double.compare(a.averageHeatIndex, b.averageHeatIndex))
                .collect(Collectors.toList());

        List<StatsManager.MemberStats> sortedUsers = stats.members.values().stream()
                .sorted((a, b) -> Double.compare(a.averageHeatLevel, b.averageHeatLevel))
                .collect(Collectors.toList());

        // Adjusted formatting strings for alignment
        String headerFormat = "%-65s|%-65s\n";
        String subHeaderFormat = "%-30s %11s %-22s|%-30s %18s %-23s\n";
        String lineFormat = "%-65s|%-65s\n";
        String separator = "─".repeat(65) + "|" + "─".repeat(65) + "\n";

        output.append(String.format(headerFormat, "CHANNELS", " USERS"));
        output.append(String.format(subHeaderFormat, "Channel", "Avg Heat", "", " User", "Avg Heat Level", ""));
        output.append(separator);

        int maxRows = Math.max(sortedChannels.size(), sortedUsers.size());

        for (int i = 0; i < maxRows; i++) {
            String channelLine = "";
            if (i < sortedChannels.size()) {
                StatsManager.ChannelStats channel = sortedChannels.get(i);
                String heatBar = generateHeatBar(channel.averageHeatIndex);
                channelLine = String.format("#%-29s %8.3f   %s",
                        channel.channelName,
                        channel.averageHeatIndex,
                        heatBar);
            }

            String userLine = "";
            if (i < sortedUsers.size()) {
                StatsManager.MemberStats member = sortedUsers.get(i);
                String heatBar = generateHeatBar(member.averageHeatLevel);
                userLine = String.format(" %-30s %8.3f   %s",
                        member.memberName,
                        member.averageHeatLevel,
                        heatBar);
            }

            output.append(String.format(lineFormat, channelLine, userLine));
        }
    }

    private static void buildChannelMonitor(StatsManager.ServerStats stats, StringBuilder output) {
        if (stats.channels == null || stats.channels.isEmpty()) {
            output.append("No channels data available yet.\n");
            return;
        }

        // Sort channels by average heat index (ascending = least to most hot)
        List<StatsManager.ChannelStats> sorted = stats.channels.values().stream()
                .sorted((a, b) -> Double.compare(a.averageHeatIndex, b.averageHeatIndex))
                .collect(Collectors.toList());

        output.append(String.format("%-30s %12s %15s\n", "Channel", "Avg Heat", "Msgs/Min"));
        output.append("─".repeat(60) + "\n");

        for (StatsManager.ChannelStats channel : sorted) {
            String heatBar = generateHeatBar(channel.averageHeatIndex);
            output.append(String.format("#%-29s %8.3f    %10.2f   %s\n",
                    channel.channelName,
                    channel.averageHeatIndex,
                    channel.messagesPerMinute,
                    heatBar));
        }
    }

    private static void buildUserMonitor(StatsManager.ServerStats stats, StringBuilder output) {
        if (stats.members == null || stats.members.isEmpty()) {
            output.append("No user data available yet.\n");
            return;
        }

        // Sort members by average heat level (ascending = least to most hot)
        List<StatsManager.MemberStats> sorted = stats.members.values().stream()
                .sorted((a, b) -> Double.compare(a.averageHeatLevel, b.averageHeatLevel))
                .collect(Collectors.toList());

        output.append(String.format("%-30s %12s\n", "User", "Avg Heat Level"));
        output.append("─".repeat(50) + "\n");

        for (StatsManager.MemberStats member : sorted) {
            String heatBar = generateHeatBar(member.averageHeatLevel);
            output.append(String.format("%-30s %8.3f   %s\n",
                    member.memberName,
                    member.averageHeatLevel,
                    heatBar));
        }
    }

    /**
     * Generates a visual heat bar representation (0.0 to 1.0)
     */
    private static String generateHeatBar(double heat) {
        int barLength = 20;
        int filled = (int) (heat * barLength);
        filled = Math.min(filled, barLength); // Cap at barLength

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        return bar.toString();
    }
}
