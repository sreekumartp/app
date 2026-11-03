# kotlinx.serialization: keep generated serializers for persisted config and rule packs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.anchor.** {
    *** Companion;
}
-keepclasseswithmembers class app.anchor.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.anchor.**$$serializer { *; }
