# ESM Anchor Share

Addon for [Extreme Sound Muffler](https://github.com/LeoBeliik/ExtremeSoundMuffler) (ESM) 4.05+ on Minecraft 26.3, for Fabric and NeoForge.

- **Share anchors in chat:** other players with the addon get a chat line with a **[Copy]** button that adds the anchor to their own ESM anchors.
- **Server anchors:** admins publish one of their ESM anchors to the server, and it syncs to every player with the addon.

## Download

Grab the Fabric or NeoForge jar from the [latest release](https://github.com/Cripacx/esm-anchor-share/releases/latest).

## Installation

| Side | Needs |
|---|---|
| Client | ESM + this addon (Fabric also needs Fabric API) |
| Server | Only this addon (ESM is client-only and not needed on the server) |

Both sides are optional. Vanilla players can still join, and the addon still works on servers that don't run it (chat sharing then falls back to plain text, see below).

## In the muffler screen

Open ESM's **Anchors** tab and select an anchor. Two buttons appear left of the edit button:

| Button | Who sees it | Effect |
|---|---|---|
| Share (arrow out of a box) | Everyone | Shares the selected anchor in chat |
| Publish (arrow onto a bar) | Operators, on servers running the addon | Turns the selected anchor into a server anchor for all players |

A toast confirms the action or explains why it failed, because the screen covers the chat.

## Commands

The same actions are also available as commands. Client (all players):

| Command | Effect |
|---|---|
| `/esmanchor share <anchor>` | Shares one of your anchors in chat |
| `/esmanchor publish <anchor>` | Operators only (level 2): turns your anchor into a server anchor. A server anchor with the same name gets replaced. |
| `/esmanchor copy <id>` | Run by the [Copy] button |

Server (operators, level 2):

| Command | Effect |
|---|---|
| `/esmadmin list` | Lists all server anchors |
| `/esmadmin remove <name>` | Removes a server anchor for everyone |

To create a server anchor, build it in the ESM GUI as usual (position, radius, sounds), then press Publish or run `/esmanchor publish <name>`.

## How it behaves

**Sharing when the server runs the addon:** the anchor goes to the server as a packet and the server hands it to every player. Players without the addon get a short notice instead. Each player has a 3-second cooldown, and every share is written to the server log.

**Sharing when the server does not run the addon:** the anchor is posted to chat as compressed text (`[ESM] esma2:<id>:<part>/<total>:…`). Clients with the addon hide these lines and show the [Copy] line once all parts have arrived; players without it see the text.
- Minecraft allows 256 characters per message. One message fits about 14 typical sounds.
- Larger anchors are split into up to 5 messages (about 145 sounds), sent one second apart so chat spam limits don't trigger. Anything larger is refused with a message.
- Parts that never complete are dropped after 30 seconds.

Chat format, before deflate and base64url:

| Field | Encoding |
|---|---|
| name | VarInt length + UTF-8 |
| dimension, sound ids | Like the name; `minecraft:` is left out, other namespaces are kept, and ESM's mod-wide entries get a trailing `:` (`create:`) |
| x, y, z | Zigzag VarInts |
| radius | VarInt |
| sounds | VarInt count, then sorted id + 1 byte volume (steps of 1/200) |
| blocks | VarInt count, then names |

**Server anchors on the client:**
- They only apply when none of the player's own anchors already muffles the sound.
- ESM's main toggle and its "disable anchors" option apply to them as well.
- They don't show up in ESM's anchor list.
- They are dropped when the player leaves the server.

**Storage:** `<world>/data/esmanchorshare_anchors.json`, written atomically. If the file can't be read, it is moved to `.corrupt` instead of being overwritten.

**Languages:** ESM stores the "everything" entry in the player's language. The addon swaps it for a neutral placeholder while sharing, so anchors work between players who use different languages.

**Limits:**
- Name: 32 characters
- Radius: 1–1024; copying clamps it to your own ESM `maxAnchorRange`
- 512 sounds per anchor
- 128 server anchors, and the sync packet stays under 1 MiB

## Building

```bash
./gradlew build
```

The jars end up in `fabric/build/libs` and `neoforge/build/libs`.

Releases are automatic: every push to `main` is built and tested, and when `version` in `gradle.properties` has no GitHub release yet, the workflow publishes `v<version>` with both jars. To release, bump the version and push.

Tests:

- `./gradlew :common:test` covers encoding and validation.
- `./gradlew :fabric:runClientGameTest` starts a real client and plays through the whole flow: muffler buttons, server round trip, copy, chat fallback and server anchor muffling. ESM is pulled in as a compile dependency from the Modrinth maven (see `gradle.properties`).
