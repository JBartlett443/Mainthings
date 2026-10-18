# SignedVault

A mobile-first catalogue for signed memorabilia: QR-labelled storage boxes, COA tracking,
verified eBay UK sold-comp valuations, and insurance exports.

## Live app

**https://claude.ai/artifact/WCRt7RCxGnJVwjPPYcjujN**: cloud-synced across phone and desktop
(sign in to claude.ai). Box QR labels open `…#BOX-01` straight to that box.

## Files

- `src/signedvault.html`: the app source (published as the artifact above).
- `index.html`: a standalone build (`./build.sh`) for any static host such as GitHub Pages.
  Hosted on its own, it stores everything in the browser's IndexedDB on that device only.
  Use Backup / Restore to move data between copies. `?box=BOX-01` and `#BOX-01` both work.

## Valuation rule

Current verified market value = the mean (and median) of the **most recent 6** sold comps that are
marked COA verified, are not excluded as outliers, and sold within the last 12 months.
