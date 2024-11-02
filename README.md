# Pokey
[![GitHub downloads](https://img.shields.io/github/downloads/KoalaSat/pokey/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/KoalaSat/pokey/releases)
[![release](https://img.shields.io/github/v/release/KoalaSat/pokey)](https://github.com/KoalaSat/nostros/pokey)
[![MIT](https://img.shields.io/badge/license-MIT-blue)](https://github.com/KoalaSat/pokey/blob/main/LICENSE)

 Receive live notifications for your nostr events and allow other apps to receive and interact with them.

<div align="center">
    <img src="https://github.com/KoalaSat/pokey/blob/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Description of Image" />
</div>
<div align="center">
    <a href="https://github.com/ImranR98/Obtainium" target="_blank">
        <img src="./docs/obtainium.png" alt="Get it on Obtaininum" height="70" />
    </a>
<!--     <a src="https://github.com/zapstore/zapstore-cli" target="_blank">
        <img src="./docs/obtainium.png alt="Get it on Zap.Store" height="70" />
    </a> -->
    <a href="https://github.com/KoalaSat/pokey/releases" target="_blank">
        <img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="70">
    </a>
</div>

# Getting Started

What is Nostr: https://www.nostr.how

# Features

- [x] Finish POC
- [x] Connect with Amber
- [x] Broadcast to other apps
- [x] Auth to relays
- [ ] Use built-in Tor engine
- [ ] Multi-account
- [ ] InBox Relays management
- [x] Granulated notification settings

# Receiving user's nostr events in your app

1. Register the intent filter in the `AndroidManifest.xml` file:
```xml
<receiver android:name=".MyBroadcastReceiver">
    <intent-filter>
        <action android:name="com.shared.NOSTR" />
    </intent-filter>
</receiver>
```
2. Register receiver in your Service or Activity:
```kotlin
override fun onStart() {
    super.onStart()
    val filter = IntentFilter("com.shared.NOSTR")
    registerReceiver(myBroadcastReceiver, filter)
}

override fun onStop() {
    super.onStop()
    unregisterReceiver(myBroadcastReceiver)
}

```

# Kudos

- Inspired by [https://github.com/greenart7c3/Amber](https://github.com/greenart7c3/Amber)
- @vitorpamplona for the push :D
