# Room and Compose ship their own consumer rules; nothing app-specific is needed yet.
# Strip any android.util.Log calls that might be added later, as a safety net for privacy.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
