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

## GitHub Pages

`.github/workflows/pages.yml` builds `index.html` and deploys it on every push to `main`.
One-time setup: repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
The site is then at `https://jbartlett443.github.io/Mainthings/`.

## COA lookup links

PSA/DNA links straight to the cert. Beckett (BAS), JSA and Fanatics are placeholders. Paste
their lookup URLs (use `{cert}` where the number goes) in **Settings → COA verification links**.

## Android app (APK, not on the Play Store)

`android/` wraps the standalone page in a native WebView app with a built-in QR scanner, camera
photo capture, exports saved to **Downloads/SignedVault**, and printing.
`.github/workflows/android.yml` builds it on every push; builds from `main` are signed with a
stable key and published as a GitHub release.

Install: on your phone open
**https://github.com/JBartlett443/Mainthings/releases/latest/download/SignedVault.apk**, open the
downloaded file, and allow installs from your browser when Android asks.

- Data is stored on the phone (the app doesn't sync with the claude.ai version). Use
  **Backup** / **Restore** to copy your catalogue between them.
- To make phone-camera scans of box labels open the app, go to Android **Settings → Apps →
  SignedVault → Open by default → Add link** and tick `jbartlett443.github.io`. Or use the app's
  own **Scan** button.
