# GeyserReversion

This is an unofficial continuation of **[Oryxel's GeyserReversion](https://github.com/oryxel1/GeyserReversion)**.

## Authors and AI disclosure

- **Original GeyserReversion author:** [Oryxel / oryxel1](https://github.com/oryxel1), credited as `oryxel` in the extension metadata.
- **This fork:** maintained by [papergrown76-ux](https://github.com/papergrown76-ux).
- **AI modifications:** the compatibility changes, regression tests, smoke-test tools, and documentation in this fork were generated and modified with **OpenAI Codex (AI)** at the fork maintainer's request. Validation performed so far is described below; gameplay with Nexo still needs testing on a real server.
- **Protocol translation library:** [Oryxel's Ouranos fork](https://github.com/oryxel1/Ouranos), originally by [Blackjack200](https://github.com/Blackjack200/Ouranos).
- **Original inspiration:** [bundabrg/GeyserReversion](https://github.com/bundabrg/GeyserReversion).

The original project's authorship and the repository's [GPL-3.0 license](LICENSE) are retained.

## Downloads

Download `geyserreversion.jar` from this fork's [GitHub Releases](https://github.com/papergrown76-ux/GeyserReversion-forked-/releases).
Release uploads contain the extension JAR only; no SHA-256 checksum files are attached.

## Versions

Build and run with Java 21 and **Geyser 2.10.0** (standalone build 1162 is used by the smoke test).
This extension uses Geyser internals; newer Geyser releases require separate compatibility testing.

| Bedrock release | Protocol | Handling |
| --- | --- | --- |
| 26.0–26.3, including 26.2 | 924 | Geyser's native path |
| 26.10 | 944 | Native path; also the bridge target for older clients |
| 26.20–26.23 | 975 | Geyser's native path |

26.2 and 26.20 are different releases. Native clients keep Geyser's own packet handlers and mappings.
Older clients use the bundled Ouranos translation paths, reaching back to 1.12.0 with upstream limitations.

## Custom content

The compatibility changes use Geyser's registered custom block and item definitions, without depending on a particular content plugin.
They isolate codec helpers for each connection, resolve block IDs from that connection's custom palette, restore Geyser's block definitions for serverbound interactions, and preserve announced custom items instead of converting them into fallback items.
The server palette comes directly from Geyser, and existing modern block variants are preserved when legacy block states are upgraded.

This is intended for Nexo and other plugins with a working Geyser custom-content integration and a matching resource pack.
Custom-block translation is covered by automated packet/chunk tests and a standalone fixture that registers custom block variants through Geyser's API.
It still needs gameplay validation with Nexo: join, load chunks, receive items, place/break blocks, reconnect, and connect two clients using different supported versions.

Clients before **1.16.100 (protocol 419)** cannot receive custom block definitions and are rejected on servers registering custom blocks.
They can still use servers without custom blocks. Custom resources can impose additional minimum client versions; this extension does not downgrade resource-pack assets or block components.

## Build and tests

```sh
bash ./gradlew --no-daemon --max-workers=2 build shadowJar
```

The extension is `build/libs/geyserreversion.jar`. Copy it into Geyser's `extensions` directory and restart the host.
No publishing token is needed to build or test.

The optional standalone smoke test checks extension startup, the complete server palette after custom blocks are registered, the installed runtime item transformer, and an actual Bedrock/RakNet ping. It binds only to loopback and stops its server afterwards.
It does not require a Java backend or player credentials.

```sh
bash ./gradlew customContentFixtureJar
curl -fSL https://download.geysermc.org/v2/projects/geyser/versions/2.10.0/builds/1162/downloads/standalone -o /tmp/Geyser-2.10.0-b1162.jar
python3 scripts/smoke-test.py --geyser-jar /tmp/Geyser-2.10.0-b1162.jar
```

The smoke runner verifies the official JAR's SHA-256 and writes `build/reports/smoke-test.log`.
The fixture extension is separate from the release artifact. Gameplay and anticheat compatibility require testing on the actual server.
