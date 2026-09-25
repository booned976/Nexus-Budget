# The Anthropic SDK and Jackson (de)serialize API models with reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Optional dependencies referenced by Jackson and the SDK that don't exist on Android.
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn io.swagger.v3.oas.annotations.**
-dontwarn com.github.victools.jsonschema.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.standardwebhooks.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# kotlinx.serialization models used by the bank connectors.
-keepclassmembers class com.nexusbudget.connectors.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nexusbudget.connectors.**$$serializer { *; }
