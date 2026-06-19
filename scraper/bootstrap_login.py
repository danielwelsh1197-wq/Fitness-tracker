"""One-time, interactive credential bootstrap. Run this on your own machine.

It does two things you only need to do once:

1. Logs in to Garmin Connect (handling MFA), caches the OAuth token, and prints
   a base64 blob to store as the ``GARMIN_TOKENS`` GitHub secret. CI restores
   this into ~/.garminconnect so it never needs your password again.

2. Helps you mint a Google **master token** for Keep, to store as the
   ``KEEP_MASTER_TOKEN`` secret.

Usage:
    python -m scraper.bootstrap_login garmin
    python -m scraper.bootstrap_login keep
"""
from __future__ import annotations

import getpass
import os
import sys


def _tokenstore() -> str:
    return os.path.expanduser(os.getenv("GARMIN_TOKENSTORE", "~/.garminconnect"))


def bootstrap_garmin() -> None:
    from garminconnect import Garmin

    email = input("Garmin email: ").strip()
    password = getpass.getpass("Garmin password: ")
    tokenstore = _tokenstore()

    garmin = Garmin(email=email, password=password, return_on_mfa=True)
    result1, result2 = garmin.login()
    if result1 == "needs_mfa":
        code = input("Garmin MFA code (from email/authenticator): ").strip()
        garmin.resume_login(result2, code)

    # Persist the token to a local directory so future local runs skip re-login.
    os.makedirs(tokenstore, exist_ok=True)
    garmin.client.dump(tokenstore)
    print(f"\n✓ Token cached at {tokenstore}")

    # garminconnect 0.3.x serialises the whole token set to one string, which
    # login() can load directly — so the GitHub secret is just this string.
    blob = garmin.client.dumps()
    print("\n=== Copy everything between the markers into the GARMIN_TOKENS secret ===\n")
    print(blob)
    print("=== end ===")


def bootstrap_keep() -> None:
    import gpsoauth

    print(
        "To mint a Google master token:\n"
        "  1. Open https://accounts.google.com/EmbeddedSetup in a browser.\n"
        "  2. Sign in to the Google account whose Keep notes you want.\n"
        "  3. When asked, open dev tools and copy the 'oauth_token' cookie value\n"
        "     (it starts with 'oauth2_4/...'). See gkeepapi docs for the latest steps.\n"
    )
    email = input("Google email: ").strip()
    oauth_token = getpass.getpass("oauth_token value: ").strip()
    android_id = "0123456789abcdef"  # any stable hex id works

    res = gpsoauth.exchange_token(email, oauth_token, android_id)
    token = res.get("Token")
    if not token:
        print(f"\nFailed to exchange token. Full response:\n{res}")
        sys.exit(1)

    print("\n=== Store these as GitHub secrets ===")
    print(f"KEEP_EMAIL={email}")
    print(f"KEEP_MASTER_TOKEN={token}")
    print("=== end ===")


def main() -> None:
    target = sys.argv[1] if len(sys.argv) > 1 else ""
    if target == "garmin":
        bootstrap_garmin()
    elif target == "keep":
        bootstrap_keep()
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main()
