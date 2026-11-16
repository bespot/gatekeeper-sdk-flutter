# Applied to every app that uses this plugin.

# Optional dependencies of syslog4j, used by the SDK logger. Android never loads them.
-dontwarn com.sun.jna.**
-dontwarn org.apache.commons.pool.**
-dontwarn org.apache.log4j.**
-dontwarn org.joda.time.**

# Room 2.5 (used by WorkManager, which the SDK uses) keeps database classes but not their
# constructor. Room creates the database by calling that constructor.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
