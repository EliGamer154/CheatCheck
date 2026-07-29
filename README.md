# TradeShop

A Fabric **server-side** mod for Minecraft **26.1.2 - 26.2**. Players join with a vanilla client — no client mod required. It has two parts:

1. A `/shop` player-to-player item trading system.
2. An **anti-cheat moderation toolkit** (see below): `/report`, a `/cheatcheck` spectate menu, `/safemode`, `/offend`, and `/tempban`.

## How it works

Run `/shop` to open the trade menu:

- **Add Listing** — pick exactly one item type from your inventory that you're willing to trade away; click it again to stack more of it, up to that item's normal max stack size (so a sword stays at 1, but dirt can go up to 64). You can have up to 15 active listings at a time by default (configurable) - each one is still just a single item type. Confirming **removes it from your inventory immediately** and holds it in escrow inside the listing. You get it back in full if you cancel the listing; it's only handed to the buyer once a trade actually completes.
- **Browse Listings** — see other players' open listings and make an offer (up to 9 distinct item types from your own inventory, and you can click the same item again to offer more of it; offer items are *not* taken from you until the trade completes).
- **My Listings** — see offers made on your listings and **accept** one, or **cancel the listing** entirely (which returns the escrowed items to you and cancels any pending/accepted offers against it). Accepting a specific offer automatically cancels the other pending offers on that listing.
- **My Offers** — click an offer to open its detail screen. Once the seller accepts, **Confirm Trade** completes it; at any point before that you can **Withdraw Offer**.

The trade only actually happens when both sides have confirmed. Since the listing's items are already held in escrow, only the buyer's offered items are checked at that point — if the buyer no longer has them, the trade fails, both players are notified, and the listing reopens so the seller can accept a different offer.

Anywhere a listed or offered item is shown as an icon, **right-click it to peek inside** if it's a shulker box (or anything else carrying container contents).

**Server operators** (OP) see an extra **Admin: Manage Listings** button in the main menu, listing every open listing from every player with a force-delete action. Deleting one returns the escrowed item to its owner if they're online; if not, the item is forfeited.

## Anti-cheat moderation

All of this is server-side and works for vanilla clients. The moderation commands (except `/report`) require operator (gamemaster) permission.

### Reporting — for everyone

- **`/report <player> <reason>`** — flag a suspected cheater. The reason must be one of the server's configured violations (`esp`, `range`, `xray`, `kill aura`, `auto totem` by default). Targets must be online, and each reporter is limited to one report per 60 seconds (configurable).

### Checking cheaters — operators

- **`/cheatcheck`** — opens a menu of every reported player (as heads, with report counts and reasons). **Left-click** a player to start watching them; **right-click** to clear their reports.
  - A **Check any online player** button lists everyone online so you can watch anyone, reported or not.
  - **`/cheatcheck <player>`** watches an online player directly.
  - An **Edit violations & ban times** button opens the customizable menu.
- Starting a watch puts you in **spectator**, teleports you to the target, and turns **safemode** on.

### Safemode

- **`/safemode`** toggles safemode for you. While it's **on**:
  - Your name stays normally-colored in the tab list even though you're a spectator (it isn't grayed out).
  - You can't place or break blocks.
  - If you're watching someone, you're **leashed** within 100 blocks (configurable) of them — stray too far and you're teleported back.
- Toggling safemode **off** clears all of the above and releases the leash. Your gamemode/position are left as-is (use `/gamemode` to return yourself).

### Offenses & bans

- **`/offend <player> <reason>`** — bans the player for that violation's preset ban time.
- **`/offend add <name> <time>`** — adds a new violation with a preset ban time, e.g. `/offend add fly 3d`. Times use `s`/`m`/`h`/`d`/`w` units (`30m`, `2h`, `7d`, `1d12h`) or `perm`.
- **`/offend remove <name>`** — deletes a violation.
- **`/offend menu`** (or the button in `/cheatcheck`) — the customizable menu: left-click a violation to bump its preset ban time up the ladder, right-click to step it down.
- **`/tempban <time> <player> [reason]`** — directly temp-bans an online player for any parsed duration.
- **`/pardon <name>`** — lifts an active ban early.

Bans are enforced on rejoin: a banned player is refused at login with the reason and **remaining time**; bans auto-expire when the time is up. Everything (reports, violations, active bans) is saved to the world and survives restarts.

### `/spawnstash`

- **`/spawnstash`** (operators) — places one of several random arrangements of empty containers (and a bed) in a clear line next to you: barrel + crafting table + bed (shuffled), a shulker next to a bed, a lone shulker, a lone barrel, or a barrel + shulker.

## Configuration

Settings live in `config/tradeshop.json`, created automatically on first run:

```json
{
  "maxActiveListingsPerPlayer": 15,
  "maxOfferItemTypes": 9,
  "reportCooldownSeconds": 60,
  "safeModeLeashRadius": 100.0
}
```

Edit the file and run `/shop reload` (OP only) to apply changes without restarting the server.

## Requirements

- Minecraft **26.1.2 - 26.2** (Fabric)
- Fabric Loader `>= 0.19.3`
- Fabric API (matching game version)
- Java **25** on the server

## Building

```
./gradlew build
```

The output jar is written to `build/libs/`.

## Known limitations (v1)

- A listing is 1 item type, stackable up to that item's normal max stack size; an offer can contain up to 9 distinct item types (configurable), each stackable to any quantity.
- Both players must be online at the moment the trade is confirmed — there's no offline queue.
- Item matching for "still has it" checks compares item type + full component/NBT data (so enchanted items must match exactly), but counts are summed across stacks.
- No in-game currency — this is purely item-for-item barter.
