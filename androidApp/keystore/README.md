# Debug keystore

`debug.keystore` is a throwaway key (password `android`, alias `androiddebugkey`) shared by every
debug build, so APKs from GitHub Actions, cloud sessions and local builds are signed identically
and install over one another. It protects nothing; never use it for a release build.

Changing or deleting it makes Android refuse updates ("signatures do not match"): the app must be
uninstalled first, which loses its saved hub address.
