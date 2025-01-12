
# OpenAI OpenAPI Specification

From https://github.com/openai/openai-openapi/blob/master/openapi.yaml

TODO: Create template to process only realtime API?
https://openapi-generator.tech/docs/templating/

1. curl -o openapi.yaml https://raw.githubusercontent.com/openai/openai-openapi/refs/heads/master/openapi.yaml

## Generate Whole OpenAI API

1. time openapi-generator generate -i openapi.yaml -g kotlin  -o ./openai-kotlin-client --skip-validate-spec --additional-properties=artifactId=openai-kotlin-client,artifactVersion=0.0.1,dateLibrary=kotlinx-datetime,groupId=com.openai,library=multiplatform,packageName=com.openai,serializationLibrary=kotlinx_serialization

   time openapi-generator generate -i openapi.yaml -g kotlin  -o ./openai-kotlin-client --skip-validate-spec --additional-properties=artifactId=openai-kotlin-client,artifactVersion=0.0.1,dateLibrary=kotlinx-datetime,groupId=com.openai,library=multiplatform,packageName=com.openai


(< 5 seconds on MacBook Pro M4 Pro)
2. cd openai-kotlin-client
3. chmod +x gradlew
4. ./gradlew assemble






## Just OpenAI "realtime" (Template)

1. openapi-generator-cli author template -g kotlin -o ./custom-templates
2. ...
3. 

## OpenAI Realtime API

* openai/src/main/kotlin/org/openapitools/client/apis/RealtimeApi.kt
* openai/src/main/kotlin/org/openapitools/client/models/Realtime*.kt
```
% ls -la openai/src/main/kotlin/org/openapitools/client/models/Realtime* 
-rw-r--r--  1 pv  staff  2592 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventConversationItemCreate.kt
-rw-r--r--  1 pv  staff  1715 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventConversationItemDelete.kt
-rw-r--r--  1 pv  staff  2854 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventConversationItemTruncate.kt
-rw-r--r--  1 pv  staff  2248 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventInputAudioBufferAppend.kt
-rw-r--r--  1 pv  staff  1412 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventInputAudioBufferClear.kt
-rw-r--r--  1 pv  staff  1848 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventInputAudioBufferCommit.kt
-rw-r--r--  1 pv  staff  1720 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventResponseCancel.kt
-rw-r--r--  1 pv  staff  2086 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventResponseCreate.kt
-rw-r--r--  1 pv  staff  1814 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeClientEventSessionUpdate.kt
-rw-r--r--  1 pv  staff  5979 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeConversationItem.kt
-rw-r--r--  1 pv  staff  2443 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeConversationItemContentInner.kt
-rw-r--r--  1 pv  staff  2886 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponse.kt
-rw-r--r--  1 pv  staff  7723 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseCreateParams.kt
-rw-r--r--  1 pv  staff   855 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseCreateParamsConversation.kt
-rw-r--r--  1 pv  staff   768 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseCreateParamsMaxResponseOutputTokens.kt
-rw-r--r--  1 pv  staff  1732 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseCreateParamsToolsInner.kt
-rw-r--r--  1 pv  staff  3377 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseStatusDetails.kt
-rw-r--r--  1 pv  staff   894 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseStatusDetailsError.kt
-rw-r--r--  1 pv  staff  2114 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseUsage.kt
-rw-r--r--  1 pv  staff  1215 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseUsageInputTokenDetails.kt
-rw-r--r--  1 pv  staff  1000 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeResponseUsageOutputTokenDetails.kt
-rw-r--r--  1 pv  staff  1508 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationCreated.kt
-rw-r--r--  1 pv  staff   929 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationCreatedConversation.kt
-rw-r--r--  1 pv  staff  2373 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemCreated.kt
-rw-r--r--  1 pv  staff  1645 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemDeleted.kt
-rw-r--r--  1 pv  staff  2708 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemInputAudioTranscriptionCompleted.kt
-rw-r--r--  1 pv  staff  2318 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemInputAudioTranscriptionFailed.kt
-rw-r--r--  1 pv  staff  1211 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemInputAudioTranscriptionFailedError.kt
-rw-r--r--  1 pv  staff  2316 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventConversationItemTruncated.kt
-rw-r--r--  1 pv  staff  1444 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventError.kt
-rw-r--r--  1 pv  staff  1496 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventErrorError.kt
-rw-r--r--  1 pv  staff  1355 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventInputAudioBufferCleared.kt
-rw-r--r--  1 pv  staff  2000 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventInputAudioBufferCommitted.kt
-rw-r--r--  1 pv  staff  2830 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventInputAudioBufferSpeechStarted.kt
-rw-r--r--  1 pv  staff  2238 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventInputAudioBufferSpeechStopped.kt
-rw-r--r--  1 pv  staff  1795 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventRateLimitsUpdated.kt
-rw-r--r--  1 pv  staff  1690 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventRateLimitsUpdatedRateLimitsInner.kt
-rw-r--r--  1 pv  staff  2129 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseAudioDelta.kt
-rw-r--r--  1 pv  staff  2033 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseAudioDone.kt
-rw-r--r--  1 pv  staff  2229 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseAudioTranscriptDelta.kt
-rw-r--r--  1 pv  staff  2339 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseAudioTranscriptDone.kt
-rw-r--r--  1 pv  staff  2352 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseContentPartAdded.kt
-rw-r--r--  1 pv  staff  1643 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseContentPartAddedPart.kt
-rw-r--r--  1 pv  staff  2318 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseContentPartDone.kt
-rw-r--r--  1 pv  staff  1639 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseContentPartDonePart.kt
-rw-r--r--  1 pv  staff  1452 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseCreated.kt
-rw-r--r--  1 pv  staff  1513 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseDone.kt
-rw-r--r--  1 pv  staff  2273 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseFunctionCallArgumentsDelta.kt
-rw-r--r--  1 pv  staff  2354 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseFunctionCallArgumentsDone.kt
-rw-r--r--  1 pv  staff  1880 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseOutputItemAdded.kt
-rw-r--r--  1 pv  staff  1921 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseOutputItemDone.kt
-rw-r--r--  1 pv  staff  2102 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseTextDelta.kt
-rw-r--r--  1 pv  staff  2185 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventResponseTextDone.kt
-rw-r--r--  1 pv  staff  1482 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventSessionCreated.kt
-rw-r--r--  1 pv  staff  1392 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeServerEventSessionUpdated.kt
-rw-r--r--  1 pv  staff  7565 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSession.kt
-rw-r--r--  1 pv  staff  8569 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionCreateRequest.kt
-rw-r--r--  1 pv  staff  2612 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionCreateRequestTurnDetection.kt
-rw-r--r--  1 pv  staff  6711 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionCreateResponse.kt
-rw-r--r--  1 pv  staff  1354 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionCreateResponseClientSecret.kt
-rw-r--r--  1 pv  staff  2300 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionCreateResponseTurnDetection.kt
-rw-r--r--  1 pv  staff  1155 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionInputAudioTranscription.kt
-rw-r--r--  1 pv  staff   547 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionModel.kt
-rw-r--r--  1 pv  staff  2569 Jan 10 14:08 openai/src/main/kotlin/org/openapitools/client/models/RealtimeSessionTurnDetection.kt
```