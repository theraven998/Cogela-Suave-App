# Room / Hilt generate code; their consumer rules + default Android rules cover them.
# Keep entity field names so Room column mapping survives shrinking.
-keepclassmembers class com.cogelasuave.data.local.entity.** { *; }

# Manifest-declared components (services/receivers) are kept by R8 automatically,
# but keep their members too — accessibility/device-admin/tile callbacks are invoked
# by the framework via reflection and must not be renamed/stripped.
-keep class com.cogelasuave.service.** { *; }

# Kotlin coroutines/metadata + Compose are handled by bundled rules; suppress noisy
# notes for missing optional classes pulled by libraries.
-dontwarn org.jetbrains.annotations.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
