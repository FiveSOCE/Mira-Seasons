package com.mira.seasons;

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
    private SeasonService seasons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        seasons = new SeasonService(this);
        getServer().getServicesManager().register(MiraSeasonsApi.class, seasons, this, ServicePriority.Normal);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new SeasonPlaceholders(this).register();
        Objects.requireNonNull(getCommand("season")).setExecutor(this);
        Objects.requireNonNull(getCommand("mseason")).setExecutor(this);
        getLogger().info("MiraSeasons v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (seasons != null) seasons.save();
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("season")) {
            Season current = seasons.current().orElse(null);
            if (current == null) {
                sender.sendMessage(c("&7No season is currently active."));
                return true;
            }
            sender.sendMessage(c("&6&l" + current.name()));
            sender.sendMessage(c("&7ID: &f" + current.id()));
            sender.sendMessage(c("&7Ends: &f" + formatDuration(Math.max(0, current.endsAt() - System.currentTimeMillis()))));
            if (!current.winners().isEmpty()) sender.sendMessage(c("&7Winners: &f" + String.join(", ", current.winners())));
            return true;
        }

        if (!sender.hasPermission("miraseasons.admin")) {
            sender.sendMessage(c("&cYou do not have permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(c("&e/mseason start <id> <duration> [display name]"));
            sender.sendMessage(c("&e/mseason end [id]"));
            sender.sendMessage(c("&e/mseason winner <name>"));
            sender.sendMessage(c("&e/mseason list"));
            sender.sendMessage(c("&e/mseason reload"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 3) return false;
                long duration = parseDuration(args[2]);
                if (duration <= 0) {
                    sender.sendMessage(c("&cUse a duration such as 7d, 12h or 30m."));
                    return true;
                }
                String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : args[1];
                Season season = seasons.start(args[1], name, duration);
                Bukkit.broadcastMessage(c("&6&lSeason Started &8» &f" + season.name()));
            }
            case "end" -> {
                Season ended = seasons.end(args.length >= 2 ? args[1] : null).orElse(null);
                if (ended == null) sender.sendMessage(c("&cNo matching active season."));
                else Bukkit.broadcastMessage(c("&6&lSeason Ended &8» &f" + ended.name()));
            }
            case "winner" -> {
                if (args.length < 2) return false;
                if (!seasons.addWinner(String.join(" ", Arrays.copyOfRange(args, 1, args.length)))) sender.sendMessage(c("&cNo active season."));
                else sender.sendMessage(c("&aSeason winner recorded."));
            }
            case "list" -> {
                sender.sendMessage(c("&6Mira Seasons"));
                for (Season season : seasons.all()) {
                    sender.sendMessage(c((season.active() ? "&a" : "&7") + season.id() + " &8- &f" + season.name() + (season.active() ? " &aACTIVE" : "")));
                }
            }
            case "reload" -> {
                seasons.load();
                sender.sendMessage(c("&aMiraSeasons reloaded."));
            }
            default -> sender.sendMessage(c("&cUnknown subcommand."));
        }
        return true;
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
        } catch (NumberFormatException e) { return -1; }
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
            current().ifPresent(s -> end(s.id()));
            long now = System.currentTimeMillis();
            Season season = new Season(id, name, now, now + durationMillis, true, new ArrayList<>());
            records.put(id.toLowerCase(Locale.ROOT), season);
            currentId = id;
            save();
            return season;
        }

        synchronized Optional<Season> end(@Nullable String id) {
            Season target = id == null ? current().orElse(null) : get(id).orElse(null);
            if (target == null || !target.active()) return Optional.empty();
            Season ended = new Season(target.id(), target.name(), target.startsAt(), Math.min(System.currentTimeMillis(), target.endsAt()), false, new ArrayList<>(target.winners()));
            records.put(target.id().toLowerCase(Locale.ROOT), ended);
            if (currentId != null && currentId.equalsIgnoreCase(target.id())) currentId = null;
            save();
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
                end(cur.id());
                Bukkit.broadcastMessage(c("&6&lSeason Ended &8» &f" + cur.name()));
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
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "id" -> s == null ? "" : s.id();
                case "name" -> s == null ? "No Season" : s.name();
                case "active" -> Boolean.toString(s != null);
                case "remaining" -> s == null ? "0m" : formatDuration(plugin.seasons.remainingMillis());
                case "ends_at" -> s == null ? "0" : Long.toString(s.endsAt());
                default -> null;
            };
        }
    }
}
