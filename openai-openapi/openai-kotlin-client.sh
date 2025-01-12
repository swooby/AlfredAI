# https://openapi-generator.tech/docs/generators/kotlin/

time \
openapi-generator generate -i openapi.yaml -g kotlin  -o ./openai-kotlin-client --skip-validate-spec \
--additional-properties=artifactId=openai-kotlin-client,artifactVersion=0.0.1,groupId=com.openai,packageName=com.openai
#--additional-properties=artifactId=openai-kotlin-client,artifactVersion=0.0.1,dateLibrary=kotlinx-datetime,groupId=com.openai,library=multiplatform,packageName=com.openai

cd ./openai-kotlin-client
chmod +x ./gradlew
./gradlew build

# Fixes:
# 1. AudioApi.kt:
#   1. change `AudioResponseFormat? = json` to `AudioResponseFormat? = AudioResponseFormat.json`
#   2. change `timestampGranularities?.value` to `timestampGranularities`
# 2. replace a couple of `data class Foo() {}` with `class Foo`


