# Pleos Vehicle SDK — IPC/AIDL·Gson 모델 보존
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**
# android.car (AAOS 시스템 라이브러리, 런타임 제공)
-dontwarn android.car.**

# Gson 2.10.x에는 consumer 규칙이 없어 R8 full mode에서 TypeToken 익명 서브클래스의
# 제네릭 시그니처가 제거됨 → SDK 내부 fromJsonOrNull()이
# "TypeToken must be created with a type argument"로 실패 (ODOMETER·EV_SOC 등 전부 FAILED).
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
