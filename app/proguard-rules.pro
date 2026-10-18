# Room, Compose, and Firebase supply their own consumer rules.

# WorkManager creates input mergers reflectively. Glance cannot render widgets if
# full-mode R8 removes their no-argument constructors (seen in the Play build).
-keep,allowoptimization class * extends androidx.work.InputMerger {
    public <init>();
}
-keep,allowoptimization class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
