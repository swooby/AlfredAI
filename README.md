<img
 style="display: block; margin-left: auto; margin-right: auto; width: 50%;"
 src="./art/AlfredAI.svg" height="100" />

# AlfredAI

OpenAI Realtime API over WebRTC Push-To-Talk Android Phone[/Mobile] + Watch[/Wear] + [Bluetooth]AudioRouting:
* https://platform.openai.com/docs/guides/realtime
* https://platform.openai.com/docs/api-reference/realtime

## Demo

| Edited (cut, some think "fake") | Full/Raw (definitely not fake) |
| :----------------------------: | :-------------: |
| <a href="https://youtu.be/2dk9uPPfRKw"><img src="https://img.youtube.com/vi/2dk9uPPfRKw/0.jpg" width="320" ></a><br>https://youtube.com/shorts/2dk9uPPfRKw<br>https://youtu.be/2dk9uPPfRKw | <a href="https://youtu.be/KTrm58dskTk"><img src="https://img.youtube.com/vi/KTrm58dskTk/0.jpg" width="320" ></a><br>https://youtu.be/KTrm58dskTk<br>&nbsp; |

## Requirements

* **Phone[/Mobile]:** Android 14 (API 34) and above
* **Watch[/Wear]:** Android Wear OS 5.0 (Android 14, API 34) and above

## Frequently Unasked/Unanswered Questions (FUUQs)

* Q: Why not just write this as a webapp like everyone else?  
  Example:  
  https://youtu.be/oMKOtYQljM4  
  (Almost all examples out there are Node/JavaScript/TypeScript or Python based)  
  A: Because I want to:
    1. Control it remotely via a watch
    2. Route audio to/from different devices
    3. Do something different (Native Android) from I guess almost everyone else :/
* Q: Why use [WebRTC Android SDK](https://github.com/webrtc-sdk/android) and not
  [LiveKit Android SDK](https://github.com/livekit/client-sdk-android) or
  [GetStream Android SDK](https://github.com/GetStream/webrtc-android)
  (or even [GetStream Android Compose SDK](https://github.com/GetStream/webrtc-in-jetpack-compose))?  
  A: I was so confused by the LiveKit and GetStream offerings that I gave up and decided to just use `io.github.webrtc-sdk:android`.  
  I was even confused by the `WebRTC Android SDK` [readme](https://github.com/webrtc-sdk/android/blob/main/README.md#how-to-use) saying:  
  `We also offer a shadowed version that moves the org.webrtc package to livekit.org.webrtc, avoiding any collisions with other WebRTC libraries`.  
  Why is WebRTC.org distributing a `livekit` module? Are they related?  
  I believe the LiveKit and GetStream SDKs could be amazeballz, especially for general peer-to-peer or multi-party WebRTC,
  but I wanted to learn how to do AI WebRTC myself and mitigate any 3rd party points of failure.  
  I even looked at https://github.com/shepeliev/webrtc-kmp, felt safer sticking with
  the original raw WebRTC Android SDK than a Kotlin-Multi-Platform wrapper.
* Q: Why OpenAI and not Google (Gemini), Microsoft (Copilot), Perplexity, Anthropic (Claude), DeepSeek, etc?  
  (https://firstpagesage.com/reports/top-generative-ai-chatbots/)  
  A: **As far as I know, as of 2025/01/30, OpenAI is the only company that has a AI WebRTC "Realtime" API.**  
  There is LiveKit, which OpenAI uses in their [OpenAI Android App](https://play.google.com/store/apps/details?id=com.openai.chatgpt), but LiveKit does not directly provide access to an AI:  
  https://docs.livekit.io/agents/quickstarts/s2s/  
  https://docs.livekit.io/agents/quickstarts/voice-agent/  
  https://docs.livekit.io/agents/openai/overview/  
  https://playground.livekit.io/  
  https://github.com/livekit-examples/realtime-playground

  I am also still trying to better understand the relationship between OpenAI and LiveKit.  
  [On 2024/10/03 they mentioned some partnership with each other](https://blog.livekit.io/openai-livekit-partnership-advanced-voice-realtime-api/).  
  I have decompiled the OpenAI Android App with [JADX](https://github.com/skylot/jadx) and see extensive use of the [LiveKit Android SDK](https://github.com/livekit/client-sdk-android),
  but I don't see how the LiveKit Android SDK helps them or me or anyone else much more than OpenAI just using the
  [WebRTC Android SDK](https://github.com/webrtc-sdk/android).  
  OpenAI also shows they are hiring for a [`Software Engineer, Real Time`](https://openai.com/careers/software-engineer-real-time/) [which I have applied for and [I think] am fully qualified for but have never heard back from them about], but that job has been listed since 2024/08, and why doesn't OpenAI just hire or buy the whole LiveKit team?

## TODOs
(Not necessarily in any order)
1. Get Android [at least Phone, maybe Watch] Notification Reader working.  
   ([Like I had it in 2017 and loved!](https://github.com/swooby/alfred.2017/tree/m2/app/src/main/java/com/swooby/alfred/notification/parsers))
   This is probably more difficult in 2025 than it was in 2017 (~API 25-27), but there are a few new APIs since then that might help (or, as I expect, make things worse):
    * API29 https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_ASSISTANT_SETTINGS
    * API30 https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
    * API22 https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_SETTINGS ([What I used in 2017](https://github.com/SmartFoo/smartfoo/blob/master/android/smartfoo-android-lib-core/src/main/java/com/smartfoo/android/core/notification/FooNotificationListenerManager.java#L96-L119))
    * API23 https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS (This was around in 2017, but I did not have a use-case for it)
1. Localize strings (I committed the sin of hard coding strings)
1. Tests (another sin I committed)
1. Get `Stop` working better
1. **Standalone** `Wear` version (lower priority; requires adding tiles for settings, conversation, etc)
1. Learn Tool/Function integration
1. Find way to integrate with Gmail/Tasks/Keep/etc
1. Find way to save conversations to https://chatgpt.com/ history
1. Implement a `VoiceInteractionService`? https://developer.android.com/reference/android/service/voice/VoiceInteractionService
1. WHOA! Find a cool way to add and use https://github.com/ggerganov/ggwave / https://github.com/PennyroyalTea/gibberlink

## Bugs
1. The on/off switch acts a little odd
2. SharedViewModel needs to search for remote device when "waking up"  
   (especially when disconnected/screen-off, when screen turns on or reconnecting)

## Development
* If Mobile or Wear physical device wireless debugging does not connect in Android Studio:  
  (from https://youtu.be/lLUYPdaf_Ow)
    1. Look on device Dev options Wireless debugging for **pairing** ip address and port
    2. Example: `adb pair 10.0.0.113:42145`  
       (replace the ip address and port with yours)
    3. Look on device Dev options Wireless debugging for **connection** ip address and port
    4. Example: `adb connect 10.0.0.113:43999`  
       (replace the ip address and port with yours)
* To record videos, use https://github.com/Genymobile/scrcpy  
  https://github.com/Genymobile/scrcpy/blob/master/doc/recording.md#recording  
  (confimed works on both Mobile/Phone and Wear/Watch!):
    * `brew install scrcpy`
    * `scrcpy -s 10.0.0.113:43999 --record=wear.mp4 & scrcpy -s 10.0.0.137:46129 --record=mobile.mp4 &`  
      (replace the ip address and port with yours)

## Inspiration

* Frank Fu
    * https://www.youtube.com/@frankfu007
    * https://github.com/FrankFuAM and https://github.com/fuwei007
    * https://github.com/fuwei007/OpenAIAndroidRealtimeDemo
        * https://github.com/fuwei007/OpenAIAndroidRealtimeDemo/blob/main/app/src/main/java/com/navbot/aihelper/RealTimeActivity.kt
        * https://www.youtube.com/shorts/6mSSSK_whYk
    * https://youtu.be/oMKOtYQljM4 "Demo OpenAI Real-time API with WebRTC | React Native Demo"
        * https://github.com/fuwei007/OpenAIRealTimeAPIWebRTC-ReactNative
    * https://youtu.be/BZRRwocw71Q "Building .NET Component Using OpenAI Real-Time API Part 5|Support WebRTC | Conversation Control"
        * Shows VAD interruption working ok! :/
        * https://github.com/fuwei007/OpenAI-realtimeapi-dotnetsdk
        * https://github.com/FrankFuAM/OpenAI-realtimeapi-dotnetsdk
* https://github.com/akdeb/openai-realtime-console, fork of https://github.com/openai/openai-realtime-console
    * Comparison: https://github.com/openai/openai-realtime-console/compare/main...akdeb:openai-realtime-console:main
* https://github.com/CadiZhang/realtime_vision_assistant, fork of https://github.com/openai/openai-realtime-console
