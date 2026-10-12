# BuildBro

Describe a build in one sentence and Claude builds it in your Minecraft world.

BuildBro is an open-source Fabric mod. It starts [Claude Code](https://claude.com/claude-code) on your computer, gives it a small set of game tools (and nothing else), and Claude plans and builds in your singleplayer world. Every request can be undone.

> Status: early development (MVP). Fabric, Minecraft 26.2, singleplayer only.

## Requirements

- Minecraft Java 26.2 with Fabric Loader 0.19.5+ and Fabric API
- Java 25
- Claude Code installed and signed in with a Claude Pro or Max account (run `claude` once in a terminal and sign in)

## Use

| Command | What it does |
| --- | --- |
| `/bro <request>` | Build something: `/bro a small stone watchtower with a spiral staircase` |
| `/bro stop` | Stop the current build (what was built stays) |
| `/bro undo [n]` | Revert the last build, or the last n builds |
| `/bro detail [quick\|detailed\|masterpiece]` | Level of detail (also `rapido`, `detalhado`, `caprichado`) |
| `/bro model [name]` | Model: `sonnet` (default), `opus`, `haiku`, `fable` or a full model id |
| `/bro limit [blocks]` | Most blocks one request may change (default 100000) |
| `/bro status` | Current settings |

Settings live in `config/buildbro.json`. `claudePath` points to the `claude` executable when it is not found automatically.

## How it works

```
/bro request ──► BuildController ──► claude -p (Claude Code, your subscription)
                        │                   │
                        │                   ▼ MCP over HTTP, 127.0.0.1 only
                        └──────────► McpHttpServer ──► ToolRegistry ──► WorldEditor ──► the world
                                                                          (undo snapshot first)
```

- **Tools** (`tool/game`): `player_info`, `survey_terrain`, `find_block`, `inspect_area`, `fill`, `place_blocks`, `undo`. They do not depend on MCP, so a direct API backend can reuse them later.
- **World writes** go through `WorldEditor`, which checks the limits and stores each block (and its container contents) before changing it. One request is one undo step.
- **Levels of detail** change only the reasoning effort (`low`, `medium`, `high`) and a short prompt; the model stays the same.

## Security

Claude can only touch the game world, never your computer:

- Claude Code starts with no built-in tools (`--tools ""`), only our MCP server (`--strict-mcp-config`, `--allowedTools mcp__buildbro`), no user or project settings or hooks (`--setting-sources ""`), no slash commands, and in an empty temporary folder.
- The MCP server listens on `127.0.0.1` on a random port, requires a new random token per build (compared in constant time), and refuses browser requests (any `Origin` header) and foreign `Host` headers.
- Your request goes to Claude Code through stdin, never the command line.
- `ANTHROPIC_API_KEY` and similar variables are removed from Claude Code's environment, so it uses your subscription login.
- Block text from the model is parsed without NBT.
- No telemetry and no auto-update. Nothing leaves your machine except what Claude Code itself sends to Anthropic.

## Build from source

```
./gradlew build
```

Needs a JDK 25. The jar lands in `build/libs/`. `./gradlew test -Dbuildbro.e2e=true` also runs an end-to-end test against your real Claude Code (it uses your subscription for one tiny request).

## Clean room

BuildBro was inspired by CRAFT AI, a closed-source mod. No code, prompts, texts or assets from it are used; this project is written from scratch.

## License

MIT
