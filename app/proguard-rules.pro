# --- MASTER SWITCH ---
-ignorewarnings

# --- APP PROTECTION ---
-keep class com.example.expensebuilder.viewmodel.** { *; }

# --- NETWORK PROTECTION ---
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# (Note: Apache POI rules are gone because we replaced it with Native CSV)