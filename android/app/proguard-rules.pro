# youtubedl-android инициализируется через JNI, имена классов не обфусцируем.
-keep class com.yausername.** { *; }
-keep class org.apache.commons.** { *; }

# commons-compress обращается к XZ-кодекам, которых нет в поставке youtubedl-android
# (нужны только для .xz/.7z, в приложении не используются).
-dontwarn org.tukaani.xz.**

# Room / Hilt
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx.serialization (JSON export/import)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.localplayer.**$$serializer { *; }
-keepclassmembers class com.example.localplayer.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.localplayer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
