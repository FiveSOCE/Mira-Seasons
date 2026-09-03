# MiraSeasons

Server-wide season lifecycle source of truth for the Mira Paper 1.21.11 / Java 21 ecosystem.

## Download

Current release: **v0.1.0**

[**Download MiraSeasons v0.1.0**](https://github.com/FiveSOCE/Mira-Seasons/releases/download/v0.1.0/MiraSeasons-0.1.0.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Seasons/releases)

## Features

- one active server season at a time
- persistent start/end timestamps
- automatic season expiry
- archived completed seasons
- winner records
- `/season` player status command
- `/mseason start <id> <duration> [display name]`
- `/mseason end [id]`
- `/mseason winner <name>`
- `/mseason list`
- PlaceholderAPI support
- public `MiraSeasonsApi` through Bukkit ServicesManager

## PlaceholderAPI

```text
%miraseasons_id%
%miraseasons_name%
%miraseasons_active%
%miraseasons_remaining%
%miraseasons_ends_at%
```

## Data

Season history and current state are stored in:

```text
plugins/MiraSeasons/seasons.yml
```

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraSeasons-0.1.0.jar
```
