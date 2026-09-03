# MiraSeasons

MiraSeasons is the server-wide season lifecycle system for the Mira Paper server suite. It provides one authoritative active season, persistent start/end times, archived completed seasons and winner records for other Mira systems to reference.

## Download

[**Download MiraSeasons v0.1.0**](https://github.com/FiveSOCE/Mira-Seasons/releases/download/v0.1.0/MiraSeasons-0.1.0.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional

## How MiraSeasons Works

Only one server season is active at a time. A season is started with an ID, duration and optional display name. MiraSeasons persists its start/end timestamps, automatically detects season expiry, archives completed seasons and stores declared winners. Other plugins can consume the public `MiraSeasonsApi` or PlaceholderAPI values instead of maintaining separate season clocks.

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
