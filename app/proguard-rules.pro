-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.foundit.app.web.** { *; }
-keep class com.foundit.app.notifications.** { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
