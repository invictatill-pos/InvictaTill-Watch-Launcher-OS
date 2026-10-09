# HealthSync WatchApp ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.healthsync.watch.data.** { *; }
-keep class com.healthsync.watch.service.WorkoutTrackingService$* { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Bluetooth
-keep class android.bluetooth.** { *; }

# Sensors
-keep class android.hardware.Sensor { *; }
-keep class android.hardware.SensorEvent { *; }
-keep class android.hardware.SensorEventListener { *; }
-keep class android.hardware.SensorManager { *; }
