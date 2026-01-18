package expo.modules.floatingwindow

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FloatingWindowService : Service() {

    companion object {
        const val ACTION_START = "expo.modules.floatingwindow.ACTION_START"
        const val ACTION_STOP = "expo.modules.floatingwindow.ACTION_STOP"

        const val EXTRA_BUTTON_SIZE = "button_size"
        const val EXTRA_POSITION_X = "position_x"
        const val EXTRA_POSITION_Y = "position_y"

        private const val NOTIFICATION_ID = 9527
        private const val CHANNEL_ID = "floating_window_channel"

        var isRunning = false
            private set

        var instance: FloatingWindowService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var resultPanel: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null
    private var resultPanelParams: WindowManager.LayoutParams? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())

    private var buttonSize = "medium"
    private var initialX = -1
    private var initialY = -1

    // Touch handling for dragging
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var buttonStartX = 0
    private var buttonStartY = 0
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                buttonSize = intent.getStringExtra(EXTRA_BUTTON_SIZE) ?: "medium"
                initialX = intent.getIntExtra(EXTRA_POSITION_X, -1)
                initialY = intent.getIntExtra(EXTRA_POSITION_Y, -1)

                startForeground(NOTIFICATION_ID, createNotification())
                createFloatingButton()

                FloatingWindowModule.instance?.emitServiceStateChange(true)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingButton()
        removeResultPanel()
        stopScreenCapture()
        instance = null
        isRunning = false
        FloatingWindowModule.instance?.emitServiceStateChange(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "搜题悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示搜题悬浮窗通知"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Intent to stop service
        val stopIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("搜题助手运行中")
            .setContentText("点击悬浮按钮截取题目")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPendingIntent)
            .build()
    }

    private fun createFloatingButton() {
        if (floatingButton != null) return

        val buttonSizePx = when (buttonSize) {
            "small" -> 48
            "large" -> 72
            else -> 60 // medium
        }.dpToPx()

        // Create button container
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(buttonSizePx, buttonSizePx)
        }

        // Create circular button
        val button = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(button)

        floatingButton = container

        // Setup layout params
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        floatingButtonParams = WindowManager.LayoutParams(
            buttonSizePx,
            buttonSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (initialX >= 0) initialX else displayMetrics.widthPixels - buttonSizePx - 20.dpToPx()
            y = if (initialY >= 0) initialY else displayMetrics.heightPixels / 3
        }

        // Touch listener for drag and click
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    buttonStartX = floatingButtonParams?.x ?: 0
                    buttonStartY = floatingButtonParams?.y ?: 0
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        floatingButtonParams?.x = (buttonStartX + dx).toInt()
                        floatingButtonParams?.y = (buttonStartY + dy).toInt()
                        windowManager?.updateViewLayout(floatingButton, floatingButtonParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onFloatingButtonClick()
                    } else {
                        // Save position
                        floatingButtonParams?.let { params ->
                            saveButtonPosition(params.x, params.y)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingButton, floatingButtonParams)
    }

    private fun removeFloatingButton() {
        floatingButton?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View not attached
            }
        }
        floatingButton = null
    }

    private fun onFloatingButtonClick() {
        FloatingWindowModule.instance?.emitButtonClick()

        // If we have media projection, capture screen
        if (mediaProjection != null) {
            captureScreen()
        } else {
            // Need to request permission first
            // This will be handled by the JS side calling requestScreenCapture
            FloatingWindowModule.instance?.emitError(
                "CAPTURE_NOT_READY",
                "请先授权屏幕录制权限"
            )
        }
    }

    fun startScreenCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Now capture immediately
        captureScreen()
    }

    private fun captureScreen() {
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Hide floating button temporarily
        floatingButton?.visibility = View.INVISIBLE

        handler.postDelayed({
            try {
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    handler
                )

                // Wait a bit for the image to be captured
                handler.postDelayed({
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)

                        // Crop to actual screen size
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        bitmap.recycle()

                        // Save to temp file
                        val tempFile = File(cacheDir, "screenshot_${UUID.randomUUID()}.png")
                        FileOutputStream(tempFile).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }

                        image.close()
                        stopScreenCapture()

                        // Show button again
                        floatingButton?.visibility = View.VISIBLE

                        // Launch crop activity
                        launchCropActivity(tempFile.absolutePath)
                    } else {
                        floatingButton?.visibility = View.VISIBLE
                        FloatingWindowModule.instance?.emitError(
                            "CAPTURE_FAILED",
                            "截图失败，请重试"
                        )
                    }
                }, 100)
            } catch (e: Exception) {
                floatingButton?.visibility = View.VISIBLE
                FloatingWindowModule.instance?.emitError(
                    "CAPTURE_FAILED",
                    "截图失败: ${e.message}"
                )
            }
        }, 100)
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        // Don't release mediaProjection so we can capture again
    }

    private fun launchCropActivity(imagePath: String) {
        val intent = Intent(this, ImageCropActivity::class.java).apply {
            putExtra(ImageCropActivity.EXTRA_IMAGE_PATH, imagePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // Result panel methods
    fun showResultPanel(content: String) {
        handler.post {
            if (resultPanel == null) {
                createResultPanel()
            }
            updateResultContent(content)
            resultPanel?.visibility = View.VISIBLE
        }
    }

    fun updateResultPanel(content: String) {
        handler.post {
            updateResultContent(content)
        }
    }

    fun hideResultPanel() {
        handler.post {
            resultPanel?.visibility = View.GONE
        }
    }

    fun setResultLoading(loading: Boolean) {
        handler.post {
            resultPanel?.findViewById<ProgressBar>(android.R.id.progress)?.visibility =
                if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun createResultPanel() {
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        val panelWidth = (displayMetrics.widthPixels * 0.9).toInt()
        val panelHeight = (displayMetrics.heightPixels * 0.6).toInt()

        // Create panel layout
        val panel = FrameLayout(this).apply {
            setBackgroundColor(0xFAFFFFFF.toInt())
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        // Close button
        val closeButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            layoutParams = FrameLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx(),
                Gravity.END or Gravity.TOP
            )
            setOnClickListener {
                hideResultPanel()
                FloatingWindowModule.instance?.emitResultClose()
            }
        }
        panel.addView(closeButton)

        // Content text view
        val contentView = TextView(this).apply {
            id = android.R.id.text1
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 40.dpToPx()
            }
        }
        panel.addView(contentView)

        // Loading indicator
        val progressBar = ProgressBar(this).apply {
            id = android.R.id.progress
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                48.dpToPx(),
                48.dpToPx(),
                Gravity.CENTER
            )
        }
        panel.addView(progressBar)

        resultPanel = panel

        resultPanelParams = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(resultPanel, resultPanelParams)
    }

    private fun updateResultContent(content: String) {
        resultPanel?.findViewById<TextView>(android.R.id.text1)?.text = content
    }

    private fun removeResultPanel() {
        resultPanel?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View not attached
            }
        }
        resultPanel = null
    }

    private fun saveButtonPosition(x: Int, y: Int) {
        val prefs = getSharedPreferences("floating_window", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("position_x", x)
            .putInt("position_y", y)
            .apply()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
