# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.datalaw.lexa.store.** {
    *** Companion;
}
-keepclasseswithmembers class app.datalaw.lexa.store.** {
    kotlinx.serialization.KSerializer serializer(...);
}
