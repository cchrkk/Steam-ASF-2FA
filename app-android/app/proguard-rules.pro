# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class tk.chrk.qrloginapprover.data.** {
    *** Companion;
}
-keepclasseswithmembers class tk.chrk.qrloginapprover.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
