# MiraSeasons

MiraSeasons is the server-wide season lifecycle system for the Mira Paper server suite. It provides one authoritative active season, persistent start/end times, archived completed seasons and winner records for other Mira systems to reference.

## Download

[**Download MiraSeasons v0.1.1**](https://github.com/FiveSOCE/Mira-Seasons/releases/download/v0.1.1/MiraSeasons-0.1.1.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.2.0 or newer
- PlaceholderAPI optional

## How MiraSeasons Works

Only one server season is active at a time. A season is started with an ID, duration and optional display name. MiraSeasons persists its start/end timestamps, automatically detects season expiry, archives completed seasons and stores declared winners. Other plugins can consume the public `MiraSeasonsApi` or PlaceholderAPI values instead of maintaining separate season clocks.

v0.1.1 makes MiraSeasons a first-class MiraCore service. Season start/end/winner changes are auditable, typed `MiraSeasonStartedEvent` and `MiraSeasonEndedEvent` lifecycle events are emitted, archived season IDs cannot be accidentally reused, and declaring a known Minecraft player as a winner awards the `season.<id>.champion` MiraCore milestone that MiraTags can consume automatically.

Current and historical season data is stored in `plugins/MiraSeasons/seasons.yml`.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/season` | None required | Shows the current season and remaining time. |
| `/mseason start <id> <duration> [display name]` | `miraseasons.admin` | Starts a new server season. |
| `/mseason end [id]` | `miraseasons.admin` | Ends the active season or specified season. |
| `/mseason winner <name>` | `miraseasons.admin` | Records a winner for the current season. |
| `/mseason list` | `miraseasons.admin` | Lists season records/history. |
| `/mseason reload` | `miraseasons.admin` | Reloads MiraSeasons configuration/data where supported. |

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miraseasons.admin` | OP | Allows creating, ending and administering seasons. |


## PlaceholderAPI

Current season:

- `%miraseasons_id%`
- `%miraseasons_name%`
- `%miraseasons_active%`
- `%miraseasons_remaining%`
- `%miraseasons_starts_at%`
- `%miraseasons_ends_at%`
- `%miraseasons_winner_count%`
- `%miraseasons_winners%`

Most recently completed season:

- `%miraseasons_last_id%`
- `%miraseasons_last_name%`
- `%miraseasons_last_winner_count%`
- `%miraseasons_last_winners%`

## Integration

MiraSeasons registers its existing `MiraSeasonsApi` with both Bukkit services and MiraCore. Other modules can listen for season start/end lifecycle events instead of polling the YAML state.
