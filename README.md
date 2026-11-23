# EconomyLogger

Logs every economy transaction on your server: who got money, who paid it, which plugin moved it,
and what items came with it. Everything is browsable in a green-gradient GUI.

Runs on Paper and Folia: 1.21.11, 26.1.2, 26.3. Java 21+.

## How it works
- **Vault recorder.** On startup EconomyLogger registers a transparent `Economy` at the highest
  priority. Every plugin that asks Vault (or VaultUnlocked) for the economy talks through it,
  so every `depositPlayer` / `withdrawPlayer` is seen. The real economy (Essentials, CMI, ...)
  keeps doing the work; the recorder only reads the result.
- **Which plugin.** The calling plugin is found from the call stack (cached per class).
- **Player to player.** A withdraw from Y and a deposit to X by the same plugin, on the same thread,
  within `pairing.window-ms` are linked: X sees "from Y", Y sees "to X". Works for /pay plugins,
  auction houses (with tax), trades.
- **Items.** When money moves for an online player, the inventory is copied and compared a couple of
  ticks later. Items that arrived or left are saved with the transaction (exact item, enchants, name).
- **Context.** The command or menu the player used right before ("/ah sell 500", "menu: Auction House").
- **Essentials.** /pay, /eco and /sell don't go through Vault; they're read from Essentials' own event.
- **Storage.** SQLite (WAL). Writes are batched on one background thread; GUI reads on another.
  Nothing touches the disk on a tick thread.

## Commands
| Command | Permission | Default |
|---|---|---|
| `/ecolog` | economylogger.use | everyone |
| `/ecolog <player>` | economylogger.others | op |
| `/ecolog lookup <player> [page]` | economylogger.lookup | op |
| `/ecolog stats`, `/ecolog reload` | economylogger.admin | op |

Aliases: `/elog`, `/economylog`.

## GUI
- Summary head: received, spent, net, balance now for the chosen period.
- 28 transactions per page. Left click a purchase to see the exact items. Right click a payment to open
  the other player's log (needs `economylogger.others`).
- Filters: everything / money in / money out / player payments / with items; period 24h / 7d / 30d / all;
  source plugin.
- "Where money comes from": totals per plugin and top trading partners.

## Files
- `config.yml` tracking, pairing, items, storage, formatting
- `gui.yml` theme colors, every title, name, lore line, material, slot and sound
- `messages.yml` chat messages
- `transactions.db` the data

## Limits
- Plugins that change balances without Vault and without Essentials' events (their own internal
  economy) can't be seen.
- Plugins using only the VaultUnlocked v2 API (`net.milkbowl.vault2`) bypass the v1 recorder.
- If another plugin registers an Economy at Highest priority before startup, `/ecolog stats` shows
  "bypassed by ...".
- Item tracking needs the payment to happen on the player's own thread (normal for shops and auction
  houses); items sent to a mailbox are not in the inventory, so they're not seen.

## Build
`mvn package` → `target/EconomyLogger-<version>.jar`
