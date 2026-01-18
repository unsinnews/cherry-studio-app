package expo.modules.floatingwindow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

class FloatingWindowConfig : Record {
    @Field
    val buttonSize: String = "medium"

    @Field
    val positionX: Int = -1

    @Field
    val positionY: Int = -1
}

class OverlayPermissionDeniedException : CodedException(
    "PERMISSION_DENIED",
    "Overlay permission is not granted. Please enable it in system settings.",
    null
)

class ServiceNotRunningException : CodedException(
    "SERVICE_ERROR",
    "Floating window service is not running",
    null
)

class FloatingWindowModule : Module() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val REQUEST_OVERLAY_PERMISSION = 1002

        // Static reference for service communication
        var instance: FloatingWindowModule? = null
            private set

        // Pending promises for async operations
        var pendingScreenCapturePromise: Promise? = null
    }

    private val context: Context
        get() = requireNotNull(appContext.reactContext)

    private val currentActivity: Activity?
        get() = appContext.currentActivity

    override fun definition() = ModuleDefinition {
        Name("FloatingWindow")

        // Define events that can be sent to JS
        Events(
            "onCropComplete",
            "onError",
            "onButtonClick",
            "onServiceStateChange",
            "onResultClose"
        )

        OnCreate {
            instance = this@FloatingWindowModule
        }

        OnDestroy {
            instance = null
        }

        // Check if overlay permission is granted
        AsyncFunction("hasOverlayPermission") { promise: Promise ->
            val hasPermission = Settings.canDrawOverlays(context)
            promise.resolve(hasPermission)
        }

        // Request overlay permission (opens system settings)
        AsyncFunction("requestOverlayPermission") { promise: Promise ->
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("PERMISSION_ERROR", "Failed to open overlay settings: ${e.message}", e)
            }
        }

        // Start floating window service
        AsyncFunction("startService") { config: FloatingWindowConfig?, promise: Promise ->
            try {
                if (!Settings.canDrawOverlays(context)) {
                    promise.reject(OverlayPermissionDeniedException())
                    return@AsyncFunction
                }

                val serviceIntent = Intent(context, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_START
                    putExtra(FloatingWindowService.EXTRA_BUTTON_SIZE, config?.buttonSize ?: "medium")
                    putExtra(FloatingWindowService.EXTRA_POSITION_X, config?.positionX ?: -1)
                    putExtra(FloatingWindowService.EXTRA_POSITION_Y, config?.positionY ?: -1)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("SERVICE_ERROR", "Failed to start service: ${e.message}", e)
            }
        }

        // Stop floating window service
        AsyncFunction("stopService") { promise: Promise ->
            try {
                val serviceIntent = Intent(context, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_STOP
                }
                context.stopService(serviceIntent)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("SERVICE_ERROR", "Failed to stop service: ${e.message}", e)
            }
        }

        // Check if service is running
        AsyncFunction("isServiceRunning") { promise: Promise ->
            val isRunning = FloatingWindowService.isRunning
            promise.resolve(isRunning)
        }

        // Request screen capture permission
        AsyncFunction("requestScreenCapture") { promise: Promise ->
            try {
                val activity = currentActivity
                if (activity == null) {
                    promise.reject("ACTIVITY_ERROR", "No activity available", null)
                    return@AsyncFunction
                }

                pendingScreenCapturePromise = promise

                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = projectionManager.createScreenCaptureIntent()

                activity.startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
            } catch (e: Exception) {
                pendingScreenCapturePromise = null
                promise.reject("CAPTURE_ERROR", "Failed to request screen capture: ${e.message}", e)
            }
        }

        // Show result panel
        AsyncFunction("showResult") { content: String, promise: Promise ->
            try {
                FloatingWindowService.instance?.showResultPanel(content)
                    ?: throw ServiceNotRunningException()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("RESULT_ERROR", "Failed to show result: ${e.message}", e)
            }
        }

        // Update result panel content
        AsyncFunction("updateResult") { content: String, promise: Promise ->
            try {
                FloatingWindowService.instance?.updateResultPanel(content)
                    ?: throw ServiceNotRunningException()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("RESULT_ERROR", "Failed to update result: ${e.message}", e)
            }
        }

        // Hide result panel
        AsyncFunction("hideResult") { promise: Promise ->
            try {
                FloatingWindowService.instance?.hideResultPanel()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("RESULT_ERROR", "Failed to hide result: ${e.message}", e)
            }
        }

        // Set loading state
        AsyncFunction("setResultLoading") { loading: Boolean, promise: Promise ->
            try {
                FloatingWindowService.instance?.setResultLoading(loading)
                    ?: throw ServiceNotRunningException()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("RESULT_ERROR", "Failed to set loading: ${e.message}", e)
            }
        }

        // Save position
        AsyncFunction("savePosition") { x: Int, y: Int, promise: Promise ->
            try {
                val prefs = context.getSharedPreferences("floating_window", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("position_x", x)
                    .putInt("position_y", y)
                    .apply()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("STORAGE_ERROR", "Failed to save position: ${e.message}", e)
            }
        }

        // Get saved position
        AsyncFunction("getPosition") { promise: Promise ->
            try {
                val prefs = context.getSharedPreferences("floating_window", Context.MODE_PRIVATE)
                val x = prefs.getInt("position_x", -1)
                val y = prefs.getInt("position_y", -1)
                promise.resolve(mapOf("x" to x, "y" to y))
            } catch (e: Exception) {
                promise.reject("STORAGE_ERROR", "Failed to get position: ${e.message}", e)
            }
        }

        // Handle activity result for screen capture permission
        OnActivityResult { _, payload ->
            val (requestCode, resultCode, data) = payload
            if (requestCode == REQUEST_MEDIA_PROJECTION) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Permission granted, pass to service
                    FloatingWindowService.instance?.startScreenCapture(resultCode, data)
                    pendingScreenCapturePromise?.resolve(null)
                } else {
                    // Permission denied
                    pendingScreenCapturePromise?.reject(
                        "CAPTURE_CANCELLED",
                        "Screen capture permission denied",
                        null
                    )
                }
                pendingScreenCapturePromise = null
            }
        }
    }

    // Methods called from native code to emit events to JS
    fun emitCropComplete(imagePath: String, width: Int, height: Int) {
        sendEvent("onCropComplete", mapOf(
            "imagePath" to imagePath,
            "width" to width,
            "height" to height
        ))
    }

    fun emitError(code: String, message: String) {
        sendEvent("onError", mapOf(
            "code" to code,
            "message" to message
        ))
    }

    fun emitButtonClick() {
        sendEvent("onButtonClick", mapOf(
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun emitServiceStateChange(isRunning: Boolean) {
        sendEvent("onServiceStateChange", mapOf(
            "isRunning" to isRunning
        ))
    }

    fun emitResultClose() {
        sendEvent("onResultClose", emptyMap<String, Any>())
    }
}
