# Minification is disabled for the dogfood build; these rules are here so a
# future release build keeps kotlinx-serialization models and Ktor working.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class id.kenang.**$$serializer { *; }
-keepclassmembers class id.kenang.** {
    *** Companion;
}
-keepclasseswithmembers class id.kenang.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
