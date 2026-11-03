# Terminal page (SSH into the gateway host)

A **Terminal** tile in the sessions sidebar, right of Archived, opens a shell on the machine the gateway runs on.
Pi only: the host is the current gateway's host, there is no host field.

## Pieces
- `org.connectbot:sshlib` 2.2.51 (Maven Central, Java, Android-safe Ed25519): the SSH client. Blocking API, so it runs on `Dispatchers.IO`.
- `org.connectbot:termlib` 0.3.14 (Compose terminal view on libvterm): draws the shell, handles the soft keyboard, scrollback, pinch zoom.
- `ui/terminal/TerminalModel.kt` (common): `expect TerminalPage`, host-key verdict, login-name check, the authorise command. Unit tested.
- `ui/terminal/SshShell.kt`, `TerminalPage.android.kt` (android): key generation, connect/auth/PTY, the page, the extra-key row.

## Auth and trust
- Ed25519 key generated on the phone, stored in the existing encrypted `KeyValueStore` (Android Keystore AES-GCM). No password login.
- Setup screen shows the public key as a one-line command for the owner to run once on the host. Herald never edits the host.
- Host key: trust on first use, fingerprint kept per host. A different key later is refused; "Trust new key" forgets it on purpose.

## Known limits (v1)
- Lives inside the sidebar page, so its width is the drawer's width, not full screen.
- No background session: leaving the page closes the shell. Use tmux on the host for anything that must survive.
- Release (R8) build untested; keep rules are in `androidApp/proguard-rules.pro`.
- Needs sshd reachable on port 22 over the tailnet.
