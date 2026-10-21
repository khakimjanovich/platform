# crypto

AES-256-GCM for data that must never be readable in the database (card numbers, expiry dates, the
schemes' tokens), with key ids and rotation, and a keyed blind index to find equal values without
decrypting them.

## Keys

All three come from Vault, like every secret (`spring.config.import: vault://secret/ecom`):

| Property | What |
|---|---|
| `ecom.crypto.keys.<id>` | an AES-256 key, base64 of exactly 32 bytes; the id matches `[A-Za-z0-9_-]{1,32}` |
| `ecom.crypto.active-key-id` | the key new values are sealed with; setting it turns the library on |
| `ecom.crypto.blind-index-key` | the HMAC-SHA256 key of the blind index, base64 of 32 bytes |

A missing key, a key of the wrong size or an active id that names no key stops startup.

## Rotation

1. Add the new key to Vault next to the old one (`ecom.crypto.keys.k2`), keeping `active-key-id` on
   the old key, and restart: every instance can now read both.
2. Make the new key active (`ecom.crypto.active-key-id=k2`) and restart: new values are sealed with
   it, old values still read.
3. Rewrap the old values: for each one, `crypto.isStale(value)` says whether it was sealed with
   another key and `crypto.rewrap(value)` re-seals it with the active one. `crypto.keyIds()` lists
   the keys this instance holds.
4. When no value uses the old key any more, remove it from Vault and restart.

The blind-index key is not rotated this way: changing it changes every index, so it needs every
indexed value recomputed.

## Tests

- `./gradlew -p platform :crypto:test`: round trips, tampering, moving a value to another key id,
  rotation and rewrap, and the property checks.
- `./gradlew -p platform :crypto:integrationTest`: the keys read from a real Vault, the active key
  flipped there, and old values rewrapped after a restart.
