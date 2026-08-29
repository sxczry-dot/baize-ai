# Tink（AndroidX Security Crypto 依赖）引用的编译期注解，运行时不存在，忽略即可
-dontwarn com.google.errorprone.annotations.**

# Retrofit 与 kotlinx.serialization 的规则
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.deepseek.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.deepseek.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
