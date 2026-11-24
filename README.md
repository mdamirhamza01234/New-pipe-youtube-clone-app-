# YouTube Clone

NewPipe jaisa kaam karne wala, YouTube (ReVanced-style dark UI) jaisa dikhne wala Android app.
Koi Google account / API key / ads nahi. Video data [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) se aata hai.

## Features
- Home feed + category chips, search with suggestions, infinite scroll
- Video player (ExoPlayer): quality picker (144p–1080p+), fullscreen, live streams (HLS)
- **Background play** + notification controls, **Picture-in-picture**
- Mini player (video minimize karo, browsing jaari rakho)
- Comments, related videos, channel pages
- Subscriptions (local, bina login), History, Watch later, Liked videos
- Download video / audio (Downloads/YouTube Clone)
- `youtube.com` / `youtu.be` links seedha is app mein khulte hain
- Dark + light theme (phone ki setting ke hisaab se)

## APK kaise milega (GitHub se)
1. Is repo mein **Actions** tab kholo, **Build APK** workflow chalne do (push par apne aap chalta hai).
2. Khatam hone par repo ke **Releases** → **YouTube Clone (latest build)** se `YouTube-Clone.apk` download karo.
3. Phone mein install karo ("Unknown sources" allow karna padega).

## Local build
```
gradle assembleDebug     # Gradle 8.9, JDK 17, Android SDK 34
```

## Dhyan rakho
- Yeh YouTube ka official app nahi hai aur YouTube ke Terms of Service ke khilaaf ho sakta hai (NewPipe ki tarah). Play Store par nahi daal sakte.
- YouTube jab bhi apna system badalta hai, extractor toot sakta hai. Tab `app/build.gradle.kts` mein `NewPipeExtractor` ka version naya karo.
- NewPipeExtractor GPLv3 hai, isliye yeh project bhi GPLv3 (open source) rehna chahiye.
