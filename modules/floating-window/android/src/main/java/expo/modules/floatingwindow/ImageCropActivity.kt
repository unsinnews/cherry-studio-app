package expo.modules.floatingwindow

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageCropActivity : Activity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
    }

    private var imagePath: String? = null
    private var bitmap: Bitmap? = null
    private var cropView: CropOverlayView? = null
    private var imageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath == null) {
            finish()
            return
        }

        bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap == null) {
            FloatingWindowModule.instance?.emitError("CROP_FAILED", "无法加载截图")
            finish()
            return
        }

        createUI()
    }

    private fun createUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // Image view
        imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
        }
        rootLayout.addView(imageView)

        // Crop overlay
        cropView = CropOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(cropView)

        // Button container at bottom
        val buttonContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(60),
                android.view.Gravity.BOTTOM
            )
            setBackgroundColor(0x80000000.toInt())
        }

        // Cancel button
        val cancelButton = Button(this).apply {
            text = "取消"
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(100),
                dpToPx(48),
                android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dpToPx(20)
            }
            setOnClickListener {
                FloatingWindowModule.instance?.emitError("CROP_CANCELLED", "用户取消裁剪")
                finish()
            }
        }
        buttonContainer.addView(cancelButton)

        // Confirm button
        val confirmButton = Button(this).apply {
            text = "确认"
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(100),
                dpToPx(48),
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dpToPx(20)
            }
            setOnClickListener {
                cropAndSave()
            }
        }
        buttonContainer.addView(confirmButton)

        rootLayout.addView(buttonContainer)

        setContentView(rootLayout)
    }

    private fun cropAndSave() {
        val cropRect = cropView?.getCropRect() ?: return
        val bmp = bitmap ?: return
        val iv = imageView ?: return

        // Calculate the actual crop coordinates on the bitmap
        val imageMatrix = iv.imageMatrix
        val drawable = iv.drawable ?: return

        val intrinsicWidth = drawable.intrinsicWidth.toFloat()
        val intrinsicHeight = drawable.intrinsicHeight.toFloat()

        val values = FloatArray(9)
        imageMatrix.getValues(values)

        val scaleX = values[0]
        val scaleY = values[4]
        val transX = values[2]
        val transY = values[5]

        // Convert crop rect from view coordinates to bitmap coordinates
        val bitmapLeft = ((cropRect.left - transX) / scaleX).toInt().coerceIn(0, bmp.width)
        val bitmapTop = ((cropRect.top - transY) / scaleY).toInt().coerceIn(0, bmp.height)
        val bitmapRight = ((cropRect.right - transX) / scaleX).toInt().coerceIn(0, bmp.width)
        val bitmapBottom = ((cropRect.bottom - transY) / scaleY).toInt().coerceIn(0, bmp.height)

        val cropWidth = (bitmapRight - bitmapLeft).coerceAtLeast(1)
        val cropHeight = (bitmapBottom - bitmapTop).coerceAtLeast(1)

        try {
            val croppedBitmap = Bitmap.createBitmap(
                bmp,
                bitmapLeft,
                bitmapTop,
                cropWidth,
                cropHeight
            )

            // Save to file
            val outputFile = File(cacheDir, "cropped_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Emit success
            FloatingWindowModule.instance?.emitCropComplete(
                "file://${outputFile.absolutePath}",
                cropWidth,
                cropHeight
            )

            croppedBitmap.recycle()
        } catch (e: Exception) {
            FloatingWindowModule.instance?.emitError("CROP_FAILED", "裁剪失败: ${e.message}")
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap?.recycle()
        bitmap = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Custom view for crop selection overlay
     */
    inner class CropOverlayView(context: android.content.Context) : View(context) {

        private val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        private val dimPaint = Paint().apply {
            color = 0x80000000.toInt()
            style = Paint.Style.FILL
        }

        private val cornerPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }

        private var cropRect = RectF()
        private var touchMode = TouchMode.NONE
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        private val cornerSize = dpToPx(24).toFloat()
        private val minCropSize = dpToPx(50).toFloat()

        private enum class TouchMode {
            NONE, DRAG, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR
        }

        init {
            // Initialize with center crop rect
            post {
                val padding = width * 0.1f
                cropRect.set(
                    padding,
                    height * 0.2f,
                    width - padding,
                    height * 0.8f
                )
                invalidate()
            }
        }

        fun getCropRect(): RectF = cropRect

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Draw dim overlay around crop area
            // Top
            canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
            // Bottom
            canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
            // Left
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
            // Right
            canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

            // Draw crop rectangle border
            canvas.drawRect(cropRect, paint)

            // Draw corner handles
            val cs = cornerSize / 2

            // Top-left corner
            canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cornerSize, cropRect.top, cornerPaint)
            canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cornerSize, cornerPaint)

            // Top-right corner
            canvas.drawLine(cropRect.right - cornerSize, cropRect.top, cropRect.right, cropRect.top, cornerPaint)
            canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cornerSize, cornerPaint)

            // Bottom-left corner
            canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cornerSize, cropRect.bottom, cornerPaint)
            canvas.drawLine(cropRect.left, cropRect.bottom - cornerSize, cropRect.left, cropRect.bottom, cornerPaint)

            // Bottom-right corner
            canvas.drawLine(cropRect.right - cornerSize, cropRect.bottom, cropRect.right, cropRect.bottom, cornerPaint)
            canvas.drawLine(cropRect.right, cropRect.bottom - cornerSize, cropRect.right, cropRect.bottom, cornerPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    touchMode = detectTouchMode(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    when (touchMode) {
                        TouchMode.DRAG -> {
                            val newLeft = (cropRect.left + dx).coerceIn(0f, width - cropRect.width())
                            val newTop = (cropRect.top + dy).coerceIn(0f, height - cropRect.height())
                            cropRect.offsetTo(newLeft, newTop)
                        }
                        TouchMode.RESIZE_TL -> {
                            cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropSize)
                            cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropSize)
                        }
                        TouchMode.RESIZE_TR -> {
                            cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, width.toFloat())
                            cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropSize)
                        }
                        TouchMode.RESIZE_BL -> {
                            cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropSize)
                            cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, height.toFloat())
                        }
                        TouchMode.RESIZE_BR -> {
                            cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, width.toFloat())
                            cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, height.toFloat())
                        }
                        TouchMode.NONE -> {}
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    touchMode = TouchMode.NONE
                    return true
                }
            }
            return false
        }

        private fun detectTouchMode(x: Float, y: Float): TouchMode {
            val touchRadius = cornerSize * 1.5f

            // Check corners first
            if (isNear(x, y, cropRect.left, cropRect.top, touchRadius)) return TouchMode.RESIZE_TL
            if (isNear(x, y, cropRect.right, cropRect.top, touchRadius)) return TouchMode.RESIZE_TR
            if (isNear(x, y, cropRect.left, cropRect.bottom, touchRadius)) return TouchMode.RESIZE_BL
            if (isNear(x, y, cropRect.right, cropRect.bottom, touchRadius)) return TouchMode.RESIZE_BR

            // Check if inside crop rect for drag
            if (cropRect.contains(x, y)) return TouchMode.DRAG

            return TouchMode.NONE
        }

        private fun isNear(x: Float, y: Float, targetX: Float, targetY: Float, radius: Float): Boolean {
            val dx = x - targetX
            val dy = y - targetY
            return dx * dx + dy * dy <= radius * radius
        }
    }
}
