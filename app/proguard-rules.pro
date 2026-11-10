# Keep kotlinx.serialization classes and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rawedit.app.**$$serializer { *; }
-keepclassmembers class com.rawedit.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.rawedit.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
}

# Media3 Transformer & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
