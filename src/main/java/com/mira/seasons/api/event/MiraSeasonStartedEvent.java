package com.mira.seasons.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MiraSeasonStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String seasonId;
    private final String displayName;
    private final long startsAt;
    private final long endsAt;

    public MiraSeasonStartedEvent(String seasonId, String displayName, long startsAt, long endsAt) {
        this.seasonId = seasonId;
        this.displayName = displayName;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public String seasonId() { return seasonId; }
    public String displayName() { return displayName; }
    public long startsAt() { return startsAt; }
    public long endsAt() { return endsAt; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
