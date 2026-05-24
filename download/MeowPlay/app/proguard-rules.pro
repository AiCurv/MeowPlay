# Add project specific ProGuard rules here.

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# NanoHTTPD
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
