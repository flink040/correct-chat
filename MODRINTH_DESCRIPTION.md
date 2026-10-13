# Correct Chat

Correct Chat is a client-side Fabric mod that transforms Minecraft's chat into a customizable workspace. It combines advanced filtering, chat tabs, additional windows, searchable history, alerts, timestamps, local logs, anti-spam tools, and optional Twitch integration in one mod.

## Features

### Custom chat tabs and windows

- Create tabs for players, keywords, servers, mods, regular expressions, or message types.
- Combine multiple filters using AND or OR rules.
- Assign different tab groups to the main chat and additional chat windows.
- Move and resize secondary windows directly in-game.
- Create separate workspace profiles for individual servers or singleplayer worlds.
- See unread-message counters and highlighted mentions.

### Searchable chat history

Open the built-in history browser to search messages by:

- Message text
- Player name
- Message type
- Server
- Assigned chat tab

Chat messages can also be copied or used to create new filters, listeners, alerts, and ignore rules through a convenient right-click menu.

### Automation and alerts

Create automatic listeners that react to matching chat messages. Configure custom alerts as titles, action bars, or boss bars, and play Minecraft sounds or local MP3, WAV, and OGG files.

Suggested regular expressions can be reviewed and edited before they are saved. Potentially sensitive actions always require confirmation.

### Local chat logs

Correct Chat can export recent messages as readable text or JSON files. Logs are stored locally in `chatlogs/correct-chat/`, and an optional retention limit automatically removes the oldest exports.

External uploads are disabled by default and only support HTTPS endpoints.

### Additional utilities

- Configurable timestamps
- Text replacements
- Anti-spam and cooldown options
- Clickable copy button
- Local player ignore list
- Command typo detection
- Outgoing link confirmation and allowlist
- Minecraft color-code preview
- Automatic detection of messages produced by installed Fabric mods
- German and English interface

### Twitch chat integration

Connect a Twitch channel and display its messages in a dedicated chat tab. Reading chat works without authentication, while sending messages requires a Twitch user access token with `chat:read` and `chat:edit` permissions.

Twitch messages can use the same filters, logs, replacements, listeners, and alerts as Minecraft messages.

## Controls and commands

- `F8` — Open Correct Chat settings
- `/lcl` — Export all stored messages
- `/lcl 100` — Export the latest 100 messages
- `/ignore <player>` — Toggle a player on the local ignore list
- `/ignorelist` — Display locally ignored players
- `/ignoreclear` — Clear the local ignore list

Settings can also be opened through Mod Menu when it is installed.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.5 or newer
- Fabric API 0.160.0+26.2 or newer
- Java 25
- Mod Menu 20.x optional

Correct Chat runs entirely on the client and does not need to be installed on the server. Configuration files and Twitch credentials remain stored locally.
