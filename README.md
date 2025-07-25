# Pokey
[![GitHub downloads](https://img.shields.io/github/downloads/KoalaSat/pokey/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/KoalaSat/pokey/releases)
[![release](https://img.shields.io/github/v/release/KoalaSat/pokey)](https://github.com/KoalaSat/pokey)
[![MIT](https://img.shields.io/badge/license-MIT-blue)](https://github.com/KoalaSat/pokey/blob/main/LICENSE)
[![Nostr](https://img.shields.io/badge/chat-nostr-brightgreen)](https://chachi.chat/groups.0xchat.com/7SbVsYrEQMtZAMIn)

 Display live notifications for your nostr events and allow other apps to receive and interact with them.

<div align="center">
    <img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Description of Image" />
</div>
<div align="center">
    <a href="https://apt.izzysoft.de/fdroid/index/apk/com.koalasat.pokey" target="_blank">
        <img src="./docs/IzzyOnDroid.png" alt="Get it on IzzyOnDroid.png" height="70" />
    </a>
    <a href="https://github.com/ImranR98/Obtainium" target="_blank">
        <img src="./docs/obtainium.png" alt="Get it on Obtaininum" height="70" />
    </a>
    <a href="https://github.com/zapstore/zapstore/releases" target="_blank">
        <img src="./docs/zapstore.svg" alt="Get it on Zap.Store" height="70" />
    </a>
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
- [x] Multi-account
- [x] InBox Relays management
- [x] Granulated notification settings
- [x] Mute actions
- [x] Mute view
- [x] ~~Last 20~~ notifications view
- [ ] Custom emojis
- [x] Search term notification
- [x] Display more info in sticky notification

# Receiving user's inbox events on your app

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

# Sponsors

<div align="center">
    <a href="https://opensats.org" target="_blank">
        <img src="./docs/opensats_logo.png" alt="Get it on Obtaininum" />
    </a>
</div>

# Kudos

- Inspired by [https://github.com/greenart7c3/Amber](https://github.com/greenart7c3/Amber)
- @vitorpamplona for the push :D
