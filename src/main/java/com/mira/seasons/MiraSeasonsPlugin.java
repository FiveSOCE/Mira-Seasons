package com.mira.seasons;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.seasons.api.event.MiraSeasonEndedEvent;
import com.mira.seasons.api.event.MiraSeasonStartedEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class MiraSeasonsPlugin extends JavaPlugin {
    private static final String PREFIX = "&5&lMira &8>> &r";
    private MiraCore core;
    private SeasonService seasons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = MiraCoreProvider.require();
        seasons = new SeasonService(this);
        getServer().getServicesManager().register(MiraSeasonsApi.class, seasons, this, ServicePriority.Normal);
        core.modules().register(this, "MiraSeasons");
        core.services().register(MiraSeasonsApi.class, seasons);
        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Authoritative season lifecycle, archive, winner milestones and placeholders ready");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new SeasonPlaceholders(this).register();
        Objects.requireNonNull(getCommand("season")).setExecutor(this);
        Objects.requireNonNull(getCommand("mseason")).setExecutor(this);
        getLogger().info("MiraSeasons v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (seasons != null) seasons.save();
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            if (seasons != null) core.services().unregister(MiraSeasonsApi.class, seasons);
            core.modules().unregister(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("season")) {
            Season current = seasons.current().orElse(null);
            if (current == null) {
                msg(sender, "&7No season is currently active.");
                return true;
            }
            msg(sender, "&6&l" + current.name());
            msg(sender, "&7ID: &f" + current.id());
            msg(sender, "&7Ends: &f" + formatDuration(Math.max(0, current.endsAt() - System.currentTimeMillis())));
            if (!current.winners().isEmpty()) msg(sender, "&7Winners: &f" + String.join(", ", current.winners()));
            return true;
        }

        if (!sender.hasPermission("miraseasons.admin")) {
            msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            msg(sender, "&e/mseason start <id> <duration> [display name]");
            msg(sender, "&e/mseason end [id]");
            msg(sender, "&e/mseason winner <name>");
            msg(sender, "&e/mseason list");
            msg(sender, "&e/mseason reload");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 3) return false;
                if (!args[1].matches("[A-Za-z0-9_-]{1,48}")) {
                    msg(sender, "&cSeason IDs may only contain letters, numbers, underscores and hyphens.");
                    return true;
                }
                if (seasons.get(args[1]).isPresent()) {
                    msg(sender, "&cThat season ID already exists in the archive. Use a new ID.");
                    return true;
                }
                long duration = parseDuration(args[2]);
                if (duration <= 0) {
                    msg(sender, "&cUse a duration such as 7d, 12h or 30m.");
                    return true;
                }
                String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : args[1];
                Season season = seasons.start(args[1], name, duration);
                core.audit().record("MiraSeasons", "SEASON_STARTED",
                        sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null,
                        sender.getName(), season.id(), "Started season",
                        Map.of("name", season.name(), "endsAt", Long.toString(season.endsAt())));
                broadcast("&6&lSeason Started &8» &f" + season.name());
            }
            case "end" -> {
                Season ended = seasons.end(args.length >= 2 ? args[1] : null).orElse(null);
                if (ended == null) {
                    msg(sender, "&cNo matching active season.");
                } else {
                    core.audit().record("MiraSeasons", "SEASON_ENDED",
                            sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null,
                            sender.getName(), ended.id(), "Ended season", Map.of("name", ended.name()));
                    broadcast("&6&lSeason Ended &8» &f" + ended.name());
                }
            }
            case "winner" -> {
                if (args.length < 2) return false;
                String winnerName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Season current = seasons.current().orElse(null);
                if (current == null || !seasons.addWinner(winnerName)) {
                    msg(sender, "&cNo active season.");
                    break;
                }

                OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerName);
                if (winner.isOnline() || winner.hasPlayedBefore()) {
                    core.milestones().award(winner.getUniqueId(), "season." + current.id() + ".champion",
                            "MiraSeasons", Map.of("season", current.id(), "seasonName", current.name()));
                }
                core.audit().record("MiraSeasons", "SEASON_WINNER_RECORDED",
                        sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null,
                        sender.getName(), current.id(), "Recorded season winner",
                        Map.of("winner", winnerName));
                msg(sender, "&aSeason winner recorded.");
            }
            case "list" -> {
                msg(sender, "&6Mira Seasons");
                for (Season season : seasons.all()) {
                    msg(sender, (season.active() ? "&a" : "&7") + season.id() + " &8- &f" + season.name() + (season.active() ? " &aACTIVE" : ""));
                }
            }
            case "reload" -> {
                seasons.load();
                msg(sender, "&aMiraSeasons reloaded.");
            }
            default -> msg(sender, "&cUnknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("mseason") || !sender.hasPermission("miraseasons.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return complete(args[0], List.of("start", "end", "winner", "list", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            return complete(args[1], seasons.all().stream().filter(Season::active).map(Season::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("winner")) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return complete(args[2], List.of("30m", "1h", "12h", "1d", "7d", "4w"));
        }
        return List.of();
    }

    private static List<String> complete(String prefix, Collection<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct().sorted().toList();
    }

    private void msg(CommandSender sender, String raw) {
        sender.sendMessage(c(getConfig().getString("messages.prefix", PREFIX) + raw));
    }

    private void broadcast(String raw) {
        Bukkit.broadcastMessage(c(getConfig().getString("messages.prefix", PREFIX) + raw));
    }

    static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long mins = d.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return Math.max(0, mins) + "m";
    }
    static long parseDuration(String raw) {
        if (raw == null || raw.length() < 2) return -1;
        try {
            long n = Long.parseLong(raw.substring(0, raw.length() - 1));
            return switch (Character.toLowerCase(raw.charAt(raw.length() - 1))) {
                case 'm' -> Duration.ofMinutes(n).toMillis();
                case 'h' -> Duration.ofHours(n).toMillis();
                case 'd' -> Duration.ofDays(n).toMillis();
                case 'w' -> Duration.ofDays(n * 7).toMillis();
                default -> -1;
            };
        } catch (RuntimeException e) { return -1; }
    }

    public interface MiraSeasonsApi {
        Optional<Season> current();
        Optional<Season> get(String id);
        List<Season> all();
        boolean isActive(String id);
        long remainingMillis();
    }

    public record Season(String id, String name, long startsAt, long endsAt, boolean active, List<String> winners) {}

    static final class SeasonService implements MiraSeasonsApi {
        private final MiraSeasonsPlugin plugin;
        private final File file;
        private YamlConfiguration data;
        private final Map<String, Season> records = new LinkedHashMap<>();
        private String currentId;

        SeasonService(MiraSeasonsPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "seasons.yml");
            load();
            Bukkit.getScheduler().runTaskTimer(plugin, this::expireIfNeeded, 20L, 20L * 30);
        }

        synchronized void load() {
            data = YamlConfiguration.loadConfiguration(file);
            records.clear();
            currentId = data.getString("current");
            var root = data.getConfigurationSection("seasons");
            if (root != null) for (String id : root.getKeys(false)) {
                String p = "seasons." + id;
                records.put(id.toLowerCase(Locale.ROOT), new Season(id, data.getString(p + ".name", id), data.getLong(p + ".starts-at"), data.getLong(p + ".ends-at"), data.getBoolean(p + ".active"), new ArrayList<>(data.getStringList(p + ".winners"))));
            }
            expireIfNeeded();
        }

        synchronized Season start(String id, String name, long durationMillis) {
            current().ifPresent(existing -> end(existing.id(), MiraSeasonEndedEvent.Reason.REPLACED));
            long now = System.currentTimeMillis();
            Season season = new Season(id, name, now, now + durationMillis, true, new ArrayList<>());
            records.put(id.toLowerCase(Locale.ROOT), season);
            currentId = id;
            save();
            Bukkit.getPluginManager().callEvent(new MiraSeasonStartedEvent(
                    season.id(), season.name(), season.startsAt(), season.endsAt()));
            return season;
        }

        synchronized Optional<Season> end(@Nullable String id) {
            return end(id, MiraSeasonEndedEvent.Reason.MANUAL);
        }

        synchronized Optional<Season> end(@Nullable String id, MiraSeasonEndedEvent.Reason reason) {
            Season target = id == null ? current().orElse(null) : get(id).orElse(null);
            if (target == null || !target.active()) return Optional.empty();
            Season ended = new Season(target.id(), target.name(), target.startsAt(),
                    Math.min(System.currentTimeMillis(), target.endsAt()), false, new ArrayList<>(target.winners()));
            records.put(target.id().toLowerCase(Locale.ROOT), ended);
            if (currentId != null && currentId.equalsIgnoreCase(target.id())) currentId = null;
            save();
            Bukkit.getPluginManager().callEvent(new MiraSeasonEndedEvent(ended.id(), ended.name(), reason));
            return Optional.of(ended);
        }

        synchronized boolean addWinner(String winner) {
            Season cur = current().orElse(null);
            if (cur == null) return false;
            List<String> winners = new ArrayList<>(cur.winners());
            if (winners.stream().noneMatch(w -> w.equalsIgnoreCase(winner))) winners.add(winner);
            records.put(cur.id().toLowerCase(Locale.ROOT), new Season(cur.id(), cur.name(), cur.startsAt(), cur.endsAt(), true, winners));
            save();
            return true;
        }

        private synchronized void expireIfNeeded() {
            Season cur = currentRaw().orElse(null);
            if (cur != null && cur.endsAt() > 0 && cur.endsAt() <= System.currentTimeMillis()) {
                end(cur.id(), MiraSeasonEndedEvent.Reason.EXPIRED);
                plugin.core.audit().record("MiraSeasons", "SEASON_EXPIRED", null, "scheduler",
                        cur.id(), "Season expired", Map.of("name", cur.name()));
                plugin.broadcast("&6&lSeason Ended &8» &f" + cur.name());
            }
        }

        private Optional<Season> currentRaw() { return currentId == null ? Optional.empty() : get(currentId); }
        @Override public synchronized Optional<Season> current() { return currentRaw().filter(Season::active); }
        @Override public synchronized Optional<Season> get(String id) { return id == null ? Optional.empty() : Optional.ofNullable(records.get(id.toLowerCase(Locale.ROOT))); }
        @Override public synchronized List<Season> all() { return records.values().stream().sorted(Comparator.comparingLong(Season::startsAt).reversed()).toList(); }
        @Override public synchronized boolean isActive(String id) { return get(id).map(Season::active).orElse(false); }
        @Override public synchronized long remainingMillis() { return current().map(s -> Math.max(0, s.endsAt() - System.currentTimeMillis())).orElse(0L); }

        synchronized void save() {
            data = new YamlConfiguration();
            data.set("current", currentId);
            for (Season s : records.values()) {
                String p = "seasons." + s.id();
                data.set(p + ".name", s.name());
                data.set(p + ".starts-at", s.startsAt());
                data.set(p + ".ends-at", s.endsAt());
                data.set(p + ".active", s.active());
                data.set(p + ".winners", s.winners());
            }
            try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Failed to save seasons.yml: " + e.getMessage()); }
        }
    }

    static final class SeasonPlaceholders extends PlaceholderExpansion {
        private final MiraSeasonsPlugin plugin;
        SeasonPlaceholders(MiraSeasonsPlugin plugin) { this.plugin = plugin; }
        @Override public @NotNull String getIdentifier() { return "miraseasons"; }
        @Override public @NotNull String getAuthor() { return "FiveS"; }
        @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
        @Override public boolean persist() { return true; }
        @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            Season s = plugin.seasons.current().orElse(null);
            Season last = plugin.seasons.all().stream().filter(season -> !season.active()).findFirst().orElse(null);
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "id" -> s == null ? "" : s.id();
                case "name" -> s == null ? "No Season" : s.name();
                case "active" -> Boolean.toString(s != null);
                case "remaining" -> s == null ? "0m" : formatDuration(plugin.seasons.remainingMillis());
                case "starts_at" -> s == null ? "0" : Long.toString(s.startsAt());
                case "ends_at" -> s == null ? "0" : Long.toString(s.endsAt());
                case "winner_count" -> s == null ? "0" : Integer.toString(s.winners().size());
                case "winners" -> s == null ? "" : String.join(", ", s.winners());
                case "last_id" -> last == null ? "" : last.id();
                case "last_name" -> last == null ? "" : last.name();
                case "last_winner_count" -> last == null ? "0" : Integer.toString(last.winners().size());
                case "last_winners" -> last == null ? "" : String.join(", ", last.winners());
                default -> null;
            };
        }
    }
}
