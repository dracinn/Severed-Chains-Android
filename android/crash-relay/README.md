# Crash relay

Cloudflare Worker that receives `crash-<ts>.log` uploads from the Android app
(`CrashReporter`) and files them as deduplicated GitHub issues. No
Play-Services dependency, no SDK in the app — just an HTTPS POST.

## One-time deploy

```sh
cd android/crash-relay
npx wrangler login
npx wrangler secret put GH_TOKEN   # fine-grained PAT: repo Issues Read+Write
npx wrangler secret put RELAY_KEY  # any random string; goes in the APK too
npx wrangler deploy                # prints https://sc-crash-relay.<acct>.workers.dev
```

Then build the app with the endpoint + key:

```sh
./gradlew :android:assembleDebug -PcrashRelayUrl=https://sc-crash-relay.<acct>.workers.dev -PcrashRelayKey=<same-secret>
```

(or set `crashRelayUrl=`/`crashRelayKey=` in `gradle.properties` / CI secrets so
release builds pick it up).

## Flow

1. `GameLog.writeCrashLog` saves `crash-<ts>.log` (device/GL block, stack,
   debug.log tail) on uncaught exceptions.
2. Next launch, `CrashReporter` finds unsent logs. First crash asks once;
   "always send" uploads silently thereafter (setting in SharedPreferences
   `crash_reporting`).
3. The worker dedupes on `X-SC-Hash` (sha-256 of exception + top app frame —
   embedded in the issue body) and either creates
   `[crash] <Exception> at <frame>` with `crash`/`auto-reported` labels or
   comments on the existing issue.

Sent files are renamed `crash-*.log.sent` so they stay on the device for
manual sharing but aren't re-uploaded.
