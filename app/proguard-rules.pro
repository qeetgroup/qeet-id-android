# R8 rules for the release build. The libraries bring their own (kotlinx.serialization, OkHttp, Firebase, CameraX,
# Compose, Navigation); the app serialises only with generated serializers, so it needs no reflection rules.

# Keep line numbers for readable stack traces, without the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
