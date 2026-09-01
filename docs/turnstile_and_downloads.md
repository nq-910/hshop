# 🌐 Turnstile & Download Pipeline

## Overview
hShop protects its download endpoints using Cloudflare Turnstile anti-bot verification. **hShop Thor** uses a headless-capable embedded WebView verification workflow to obtain direct CDN URLs with zero user friction.

---

## Download Execution Flow

```
1. User requests download of Title (ID: 000400000017C200)
                    │
                    ▼
2. TurnstileDownloadDialog loads https://hshop.erista.me/t/{id}
   - CookieManager enables 3rd-party cookies.
   - MutationObserver listens for direct CDN token generation.
                    │
                    ▼
3. Extracted URL received: https://download4.erista.me/content/{id}?token=...
                    │
                    ▼
4. ThorDownloadManager streams chunked bytes to /sdcard/ROMs/3DS/Game.cia
                    │
                    ▼
5. Post-Download Trigger:
   - If autoConvertTo3ds is enabled: triggers Cia3ds native decryption.
   - If autoRemoveDownloadedCia is enabled: deletes source .cia upon verified .cci creation.
```

---

## State Machine (`DownloadStatus`)

- `QUEUED` ➔ `CONNECTING` ➔ `DOWNLOADING` ➔ `CONVERTING` ➔ `COMPLETED`
- Exceptions or aborts transition to `FAILED` or `CANCELLED`.
