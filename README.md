<h1 align="center">SejioMedia</h1>
<p align="center">A Minecraft Fabric mod that renders YouTube videos onto placeable in-game blocks, with synchronized audio playback.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21-brightgreen"/>
  <img src="https://img.shields.io/badge/Loader-Fabric-blue"/>
  <img src="https://img.shields.io/badge/Version-2.0-orange"/>
  <a href="https://discord.gg/nqPjgKM8dg"><img src="https://img.shields.io/badge/Discord-Join-5865F2"/></a>
</p>

---

## NOTICE
Source code may not always reflect the latest release build.

---

## Features

- **Two placeable blocks** — Media Screen (wall-mounted monitor) and Display Frame (flat frame variant), both face the direction you're looking when placed
- **YouTube video playback** — paste any YouTube URL and hit Play; yt-dlp resolves the stream automatically
- **Adjustable FPS** — Target FPS and FPS Cap sliders in the GUI (33–144 range)
- **GUI with debug log** — scrollable in-game log showing download progress, playback status, errors and live FPS
- **Auto-install dependencies** — yt-dlp, FFmpeg and VLC download automatically on first use into `.minecraft/mediascreen/`

---

## Requirements

- Minecraft 1.21
- [Fabric Loader](https://fabricmc.net/use/installer/) ≥ 0.15.0
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21
- Windows only — Mac and Linux are not currently supported

---

## How It Works

When you press Play, the mod:
1. Resolves the YouTube stream URL via **yt-dlp**
2. Pipes raw video frames through **FFmpeg** into an OpenGL texture rendered on the block face
3. Plays audio in parallel through **VLC**

All three tools are downloaded automatically to `.minecraft/mediascreen/` and only need to be downloaded once.

---

## Blocks

| Block | Description |
|-------|-------------|
| `Display Frame` | Flat display frame variant with the same playback functionality |

---

## Current Limitations

- Singleplayer only — multiplayer support is planned
- Early release, expect bugs and rough edges

---

## Planned Features

- Multiplayer support (synchronized playback across clients)
- Redesigned GUI with collapsible debug lo

---

## Legal

This mod downloads [yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/), and [VLC](https://www.videolan.org/) at runtime. These are open-source tools distributed under their respective licenses. Users are responsible for ensuring their usage complies with the terms of any content they stream.
