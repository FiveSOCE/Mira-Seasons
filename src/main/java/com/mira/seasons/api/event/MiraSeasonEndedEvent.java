package com.mira.seasons.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MiraSeasonEndedEvent extends Event {
    public enum Reason { MANUAL, EXPIRED, REPLACED }

    private static final HandlerList HANDLERS = new HandlerList();

    private final String seasonId;
    private final String displayName;
    private final Reason reason;

    public MiraSeasonEndedEvent(String seasonId, String displayName, Reason reason) {
        this.seasonId = seasonId;
        this.displayName = displayName;
        this.reason = reason == null ? Reason.MANUAL : reason;
    }

    public String seasonId() { return seasonId; }
    public String displayName() { return displayName; }
    public Reason reason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
