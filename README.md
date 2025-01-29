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

## Frequently Unanswered Questions (FUQs)

* Q: Why not just write this in React as a webapp, like:  
   https://youtu.be/oMKOtYQljM4  
   A: Because I want to:
   1. control it remotely via a watch
   2. route audio to/from different devices

## TODOs
(Not necessarily in any order)
1. Localize strings (I committed the sin of hard coding strings)
2. Tests (another sin I committed)
3. Get `Stop` working better
4. **Standalone** `Wear` version (lower priority; requires adding tiles for settings, conversation, etc)
5. Add `text` input/output feature
6. Learn Tool/Function integration
7. Find way to integrate with Gmail/Tasks/Keep/etc
8. Find way to save conversations to https://chatgpt.com/ history
9. Implement a `VoiceInteractionService`? https://developer.android.com/reference/android/service/voice/VoiceInteractionService

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
