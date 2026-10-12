# NearPing wire protocol v1

This document is the contract. You can write a client for any platform from it, without
looking at the source of the macOS application.

## Discovery

Bonjour / DNS-SD service: `_nearping._tcp`, domain `local.`

TXT record:

| Key | Value |
|---|---|
| `v` | protocol version, currently `1` |
| `pk` | Ed25519 public key (Curve25519 signing), base64url without padding |
| `n` | display name, UTF-8 |

Nothing else goes into the TXT record. **Everyone** on the network can see the record, not only
contacts. For this reason, the pause status and any other information about the person is sent
only over an established trusted connection.

A record with a different `v` is not discarded. The peer appears in the list with the label
"different version", but the client does not open a connection to it. This way the person sees
that the colleague needs to update, and does not think the colleague is offline.

A peer is identified by `pk`, not by address, service name or machine name. A change of IP,
MAC randomization and a move to another network do not count as a change of identity.

## Stream framing

TCP does not preserve message boundaries. For this reason, each message starts with its length:

```
+--------+--------+--------+--------+---------------------------+
| len[3] | len[2] | len[1] | len[0] |   payload (len bytes)     |
+--------+--------+--------+--------+---------------------------+
```

- The length is an unsigned 32-bit integer, **big-endian**.
- The maximum is **65536** bytes. The sender never sends a longer frame, and the receiver treats
  one as an error. The declared length comes from the network. Without an upper bound, a foreign
  packet would make the receiver buffer memory while it waits for a body that never arrives.
- The receiver must assemble frames from chunks of any size. One `read` can bring half a frame,
  one and a half frames, or three frames at once.

## Envelope

The frame body is UTF-8 JSON:

```json
{ "v": 1, "body": { "type": "ping", "id": "p1", "urgent": true } }
```

- `v` is required in every message. A mismatch with the own version is an error, and the
  receiver closes the connection. The receiver must not ignore it silently. Otherwise different
  versions would half-understand each other.
- The `type` field inside `body` distinguishes the message variant.
- Unknown fields are ignored. This allows optional fields to be added without breaking old
  clients.

## Messages

### `hello`

The first message after the connection is established, from both sides.

```json
{ "type": "hello", "publicKey": "<base64url>", "displayName": "Dima" }
```

### `auth_challenge` / `auth_response`

A challenge-response exchange that also derives the session key. Right after `hello`, both sides
send their own `auth_challenge`. Each side answers the other side's challenge with `auth_response`.

```json
{ "type": "auth_challenge", "challenge": "<base64url, 32 random bytes>" }
{ "type": "auth_response",
  "challenge": "<the same challenge>",
  "ephemeralKey": "<base64url, X25519 public key for this connection>",
  "signature": "<base64url, Ed25519 signature over challenge ‖ ephemeralKey>" }
```

Rules:

- The challenge is **single-use**. After the first check, the receiver strikes it off.
  The receiver rejects a second presentation of the same challenge-signature pair.
  Otherwise an attacker could replay an overheard exchange.
- The receiver checks the signature with the public key from the `hello` of the same connection.
  The signature covers **both the challenge and the ephemeral key**. Without this, an
  intermediary could replace the key and listen to both sides.
- Both sides derive the session key from X25519, using their own and the other side's ephemeral
  keys. See the section "Session encryption" for details.
- The private identity key never goes on the wire.

### `pair_request` / `pair_accept` / `pair_reject`

```json
{ "type": "pair_request", "displayName": "Dima" }
{ "type": "pair_accept" }
{ "type": "pair_reject" }
```

The receiver sends a refusal explicitly and does not stay silent. Otherwise the sender thinks the
request was lost and sends it again.

### `ping`

```json
{ "type": "ping", "id": "<unique within the connection>", "urgent": false }
```

The receiver **discards** a `ping` from an unpaired peer. It never reaches the interface.
This is the trust barrier: anyone can be seen on the network, but only peers that accepted
a request can call.

### `ack`

```json
{ "type": "ack", "id": "<call id>", "state": "DELIVERED" }
{ "type": "ack", "id": "<call id>", "state": "SEEN" }
{ "type": "ack", "id": "<call id>", "state": "RATE_LIMITED" }
```

The receiver sends `DELIVERED` on receipt. It sends `SEEN` when the person dismisses the banner
or the notification. It sends `RATE_LIMITED` instead of `DELIVERED` when it dropped the call
because of the rate limit: the call reached the machine, but the person did not hear it.
During a pause, the receiver sends `DELIVERED`, because the sender sees the pause itself.

### `presence`

```json
{ "type": "presence", "pausedUntilEpochSeconds": 1790000000, "heartbeat": true }
{ "type": "presence", "pausedUntilEpochSeconds": null, "heartbeat": true }
```

The sender sends this message to trusted peers when the connection is established, when the pause
changes, and **every three seconds as a heartbeat** (`heartbeat: true`). A receiver that has
seen at least one heartbeat treats the connection as dead after **12 seconds** of silence and
closes it. Without this, "offline" appears only after the TCP timeout, that is, after minutes.
A client that sends no heartbeats gets no deadline, so old builds keep working.

`heartbeat` defaults to `false`. The field can be omitted. A heartbeat never raises a
notification for the person.

A pause silences **both** call types, including the urgent one. The sender sees the pause in
advance and does not send blindly.

## Session encryption

The handshake establishes not only "who is at the other end" but also the key for all further
exchange.

1. Each side creates an **ephemeral** X25519 key pair for this connection.
2. The public part of the ephemeral key is sent in `auth` and is **signed with the permanent
   identity key**. The signature binds the ephemeral key to the identity. Without it, an
   intermediary would replace the key and listen to both sides.
3. Both sides derive the shared X25519 secret, and from it the session key.
4. All frames **after** the handshake are sealed with ChaChaPoly. The frame holds
   `nonce ‖ ciphertext ‖ tag`, not open JSON.

Why this is needed, although the messages hold no secrets: without sealing, only the identity
**at the time of connection** is confirmed, and after that nothing protects the stream. On a
switched network, an intermediary can wedge into an established connection and send calls in
another person's name. The seal binds each frame to the party that passed the check.

The `hello` and `auth` exchange runs in clear text, because nothing is available to encrypt with
before the shared key is derived. Ephemeral keys are not stored. After the connection closes,
nothing is left to decrypt recorded traffic retroactively, even for the owner of the permanent
key.

### Consequence for export compliance

This is **non-exempt** cryptography: the app has its own key exchange and its own encryption,
not system TLS. For distribution outside the App Store, the question does not arise. For the App
Store, `ITSAppUsesNonExemptEncryption = true` and an annual self-classification report are
required.
