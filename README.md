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
At startup it reports the detected version and build. Geyser 2.10.0 build 1162 is marked `TESTED`;
other 2.10.0 builds are allowed with an `UNTESTED_BUILD` warning. Different or unidentified
Geyser versions disable the protocol bridge and retain Geyser's native listener.
`allow-untested-geyser: true` explicitly permits attempting the bridge on those versions;
it does not establish compatibility.

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

Clients before **1.16.100 (protocol 419)** cannot receive custom block definitions and are rejected on servers registering custom blocks unless vanilla fallbacks are enabled below.
They can still use servers without custom blocks. Custom resources can impose additional minimum client versions; this extension does not downgrade resource-pack assets or block components.

## Vanilla replacements for older clients

Optional visual replacements are disabled by default. Configure `extensions/geyser-reversion/config.yml`
and restart Geyser. Rules use the **registered Geyser block identifier**, which can differ from a plugin's item ID:

```yaml
custom-block-fallbacks:
  enabled: true
  minimum-client-protocol: 419
  default-block: minecraft:stone
  blocks:
    geyser_custom:my_custom_ore:
      minimum-client-protocol: 924
      block: minecraft:gold_ore
```

This example replaces all custom blocks for translated clients below protocol 419, and the specified
ore below protocol 924. A per-block minimum overrides the global minimum, with a floor of 419.
Clients at or above the applicable minimum keep the custom definition. Native Geyser clients continue
using Geyser's own handling. A replacement missing from a client's palette becomes stone.

The bridge filters unsupported definitions, maps chunk/block updates to vanilla states, and displays
custom block items as matching vanilla items. Returning items are restored from bounded, server-issued
snapshots rather than client-supplied custom NBT. Java-side behavior and drops remain server-controlled.
This does not downgrade resource packs or guarantee old-client compatibility with every content plugin.

## Status and diagnostics

Run `/reversion status` in game, or `reversion status` in the console. Players need
`geyserreversion.command.status` (suggested operator-only). The report includes the detected Geyser
version/build, bridge state, registered custom blocks, native/translated client counts and versions,
fallback counts, and the last translation error for each displayed connection.

Errors, intentionally omitted packets, and substitutions are counted for active translated connections
even with `debug-mode: false`. Enable `debug-mode: true` for console diagnostics including packet class,
translation stage, client/server protocol, available block ID/position, and exception class/location.
Packet payloads, authentication data, and exception messages are not printed. Repeated messages are
limited per connection and packet/stage by `diagnostics-log-interval-seconds` (default 30, range 1–3600).
Counters reset when the connection ends; omitted packets can be normal protocol differences.

## Build and tests

```sh
bash ./gradlew --no-daemon --max-workers=2 build shadowJar
```

The extension is `build/libs/geyserreversion.jar`. Copy it into Geyser's `extensions` directory and restart the host.
No publishing token is needed to build or test.

The optional standalone smoke test checks extension startup, the complete server palette after custom blocks are registered, the installed runtime item/block fallback transformers, `/reversion status`, and an actual Bedrock/RakNet ping. It binds only to loopback and stops its server afterwards.
It does not require a Java backend or player credentials.

```sh
bash ./gradlew customContentFixtureJar
curl -fSL https://download.geysermc.org/v2/projects/geyser/versions/2.10.0/builds/1162/downloads/standalone -o /tmp/Geyser-2.10.0-b1162.jar
python3 scripts/smoke-test.py --geyser-jar /tmp/Geyser-2.10.0-b1162.jar
python3 scripts/smoke-test.py --geyser-jar /tmp/Geyser-2.10.0-b1162.jar --fallbacks
```

The smoke runner verifies the official JAR's SHA-256 and writes `build/reports/smoke-test.log`.
The fixture extension is separate from the release artifact. Gameplay and anticheat compatibility require testing on the actual server.
