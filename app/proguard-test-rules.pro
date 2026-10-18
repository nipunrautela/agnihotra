# Keep JUnit discovery in the optimized test APK. These rules apply only to
# instrumentation tests; the application itself still uses full release R8.
-keepattributes *Annotation*
-keep class com.nipunrautela.agnihotra.**Test { *; }
-keep class org.junit.** { *; }
