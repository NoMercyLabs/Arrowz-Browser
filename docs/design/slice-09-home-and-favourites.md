# Slice 9 — The home screen, favorites, and the store underneath them

The chrome so far is one bar over a page. This slice gives the browser the surface it opens on and the only place a viewer can keep something: favorites, most visited, and the store both come from.

## A new tab is a screen, not a search engine

Opening a tab currently loads DuckDuckGo. That is a search box on a television, reached with a keyboard that costs a dozen presses per word. The home screen is a grid of things already worth opening — favorites first, most visited after — so the common case is two presses and no typing at all.

The search engine stays exactly one press away, because the address field is on the same screen.

## What the store has to be, given sync arrives later

No accounts and no sync are built here. The schema is built as though they were, because retrofitting identity onto rows that only ever had autoincrement ids means a migration on somebody's television:

- **Stable UUID primary keys**, generated on device.
- **`updatedAt`** on every row, set on every write.
- **Tombstones** rather than hard deletes, so a delete can propagate rather than being silently undone by an older copy.

The plan's intended path is Drive `appDataFolder` under the device's own Google account, which keeps the "no data collected by the developer" claim true. Nothing here depends on that choice; it depends only on the rows being able to describe themselves.

## Most visited is derived, not curated

A visit row per navigation, and the grid reads a count grouped by origin. Two consequences that shape it:

- **Origin, not URL.** Twenty article pages on one site are one tile, or the grid becomes a list of everything the viewer has ever read.
- **A favourited origin is not repeated in most visited.** The same tile twice in one screen is the failure that makes these grids feel unconsidered.

## Tiles carry a letter, not a favicon, until a favicon exists

Fetching a favicon is a network request per tile at exactly the moment the screen must appear. Tiles draw the origin's first letter on a color derived from the origin's hash, which is instant, offline, and stable across launches. A favicon captured during browsing can replace it later without the grid changing shape.

## Failure modes this slice must not ship

1. **A home screen that blocks on a database read**, so the first frame after opening a tab is empty.
2. **A grid that renders before its focus target exists**, leaving the D-pad pointing at nothing.
3. **A favorite that cannot be removed**, or one that reappears after removal because the delete was a hard delete and something restored the row.
4. **Duplicate tiles** for a favourited origin.
5. **Writes on the main thread.** A television's storage is slow enough to drop frames.

## Acceptance

Unit tests for the store's decisions: tombstoned rows are excluded, an update bumps `updatedAt`, most-visited groups by origin and excludes favorites.

On the 8000 at `192.168.2.80`, driven with the six keycodes only: open a tab and land on the home screen, favorite the current page from the bar, see the tile appear, open it from the grid, remove it, and confirm it stays gone across a restart.
