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

## Formats

| Prefix | Associated data | Made by |
|---|---|---|
| `v1:<key id>:<base64>` | the key id | `encrypt(plain)` |
| `v2:<key id>:<base64>` | the key id, a `0x00` byte, the context | `encrypt(plain, context)` |

The base64 part is the 12-byte nonce, the ciphertext and the 16-byte tag. The context says where a
value lives, e.g. `card.pan:<card id>` or `card.expiry:<card id>`: a `v2` value decrypts only with
the same context, so a ciphertext copied to another card or another column fails (NIST SP 800-38D
associated data, as Tink's `encrypt(plaintext, associatedData)`). A `v1` value is bound only to its
key, so it could be moved unnoticed. A `v2` context is never blank (`encrypt` and `decrypt` refuse
it), and the blind-index key differs from every encryption key.

### Moving data from v1 to v2

1. Read with `decrypt(value, context)`: it opens `v2` values with their context and `v1` values as
   before, so code can switch before the data does.
2. Write with `encrypt(plain, context)`.
3. Rewrap the stored `v1` values with `rewrap(value, context)` (it also moves a `v2` value under an
   old key to the active one), and relax any database check that accepts only `v1:` to `v1:`/`v2:`
   first.
4. Once no `v1` value is left, accept only `v2:`.

## Rotation

1. Add the new key to Vault next to the old one (`ecom.crypto.keys.k2`), keeping `active-key-id` on
   the old key, and restart: every instance can now read both.
2. Make the new key active (`ecom.crypto.active-key-id=k2`) and restart: new values are sealed with
   it, old values still read.
3. Rewrap the old values: for each one, `crypto.isStale(value)` says whether it was sealed with
   another key and `crypto.rewrap(value, context)` (`rewrap(value)` for `v1`) re-seals it with the
   active one. `crypto.keyIds()` lists
   the keys this instance holds.
4. When no value uses the old key any more, remove it from Vault and restart.

The blind-index key is not rotated this way: changing it changes every index, so it needs every
indexed value recomputed.

## Tests

- `./gradlew -p platform :crypto:test`: round trips, tampering, moving a value to another key id,
  rotation and rewrap, and the property checks.
- `./gradlew -p platform :crypto:integrationTest`: the keys read from a real Vault, the active key
  flipped there, and old values rewrapped after a restart.
