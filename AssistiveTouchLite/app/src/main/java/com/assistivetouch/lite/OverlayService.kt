package com.assistivetouch.lite

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null
    private var volumePanelView: View? = null

    // --- Idle dim / edge-peek state ---
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { peekToEdge() }
    private var bubbleAnimator: ValueAnimator? = null
    private var isPeeking = false
    private var isOnRightSide = false

    private val IDLE_DELAY_MS = 3000L
    private val ANIM_DURATION = 220L
    private val NORMAL_ALPHA = 1.0f
    private val DIM_ALPHA = 0.45f

    private val bubbleWidthPx: Int by lazy { (60 * resources.displayMetrics.density).toInt() }
    private val peekVisiblePx: Int by lazy { (18 * resources.displayMetrics.density).toInt() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        showBubble()
        scheduleIdle()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacksAndMessages(null)
        bubbleAnimator?.cancel()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        menuView?.let { runCatching { windowManager.removeView(it) } }
        volumePanelView?.let { runCatching { windowManager.removeView(it) } }
    }

    private fun startForegroundNotification() {
        val channelId = "assistive_touch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Assistive Touch", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistive Touch running")
            .setContentText("Tap the floating button for power & volume controls")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun screenWidthPx(): Int = resources.displayMetrics.widthPixels

    // ---------- Bubble ----------

    private fun showBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        windowManager.addView(bubbleView, params)

        val bubbleButton = bubbleView!!.findViewById<ImageButton>(R.id.bubbleButton)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        bubbleButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelIdle()
                    bubbleAnimator?.cancel()
                    if (isPeeking) {
                        params.x = if (isOnRightSide) screenWidthPx() - bubbleWidthPx else 0
                        bubbleView!!.alpha = NORMAL_ALPHA
                        runCatching { windowManager.updateViewLayout(bubbleView, params) }
                        isPeeking = false
                        isDrag = true
                    } else {
                        isDrag = false
                    }
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isDrag = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { windowManager.updateViewLayout(bubbleView, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        toggleMenu()
                    } else {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun animateBubble(targetX: Int, targetAlpha: Float, onEnd: (() -> Unit)? = null) {
        val view = bubbleView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        bubbleAnimator?.cancel()

        val startX = params.x
        val startAlpha = view.alpha

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = ANIM_DURATION
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            params.x = (startX + (targetX - startX) * fraction).toInt()
            view.alpha = startAlpha + (targetAlpha - startAlpha) * fraction
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        bubbleAnimator = animator
        animator.start()
    }

    private fun snapToNearestEdge() {
        val view = bubbleView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val screenWidth = screenWidthPx()
        val bubbleCenterX = params.x + bubbleWidthPx / 2
        isOnRightSide = bubbleCenterX > screenWidth / 2
        val targetX = if (isOnRightSide) screenWidth - bubbleWidthPx else 0
        animateBubble(targetX, NORMAL_ALPHA) {
            scheduleIdle()
        }
    }

    private fun peekToEdge() {
        if (menuView != null || volumePanelView != null) return
        val targetX = if (isOnRightSide) screenWidthPx() - peekVisiblePx else -(bubbleWidthPx - peekVisiblePx)
        isPeeking = true
        animateBubble(targetX, DIM_ALPHA)
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, IDLE_DELAY_MS)
    }

    private fun cancelIdle() {
        idleHandler.removeCallbacks(idleRunnable)
    }

    // ---------- Menu ----------

    private fun toggleMenu() {
        if (menuView != null) {
            runCatching { windowManager.removeView(menuView) }
            menuView = null
            hideVolumePanel()
            scheduleIdle()
            return
        }
        cancelIdle()

        val inflater = LayoutInflater.from(this)
        menuView = inflater.inflate(R.layout.overlay_menu, null)

        val bubbleParams = bubbleView!!.layoutParams as WindowManager.LayoutParams

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleParams.x
        params.y = bubbleParams.y + 120

        windowManager.addView(menuView, params)

        menuView!!.findViewById<ImageButton>(R.id.btnPower).setOnClickListener {
            val service = PowerAccessibilityService.instance
            if (service != null) {
                service.showPowerMenu()
            } else {
                Toast.makeText(
                    this,
                    "Turn on the Accessibility Service first (open the app)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        menuView!!.findViewById<ImageButton>(R.id.btnVolume).setOnClickListener {
            toggleVolumePanel(params)
        }
    }

    private fun toggleVolumePanel(anchorParams: WindowManager.LayoutParams) {
        if (volumePanelView != null) {
            hideVolumePanel()
            return
        }
        cancelIdle()

        val inflater = LayoutInflater.from(this)
        volumePanelView = inflater.inflate(R.layout.overlay_volume_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = anchorParams.x
        params.y = anchorParams.y + 120

        windowManager.addView(volumePanelView, params)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        volumePanelView!!.findViewById<ImageButton>(R.id.btnVolUp).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
            )
        }
        volumePanelView!!.findViewById<ImageButton>(R.id.btnVolDown).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
            )
        }
        volumePanelView!!.findViewById<ImageButton>(R.id.btnMute).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI
            )
        }
        volumePanelView!!.findViewById<ImageButton>(R.id.btnRingUp).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
            )
        }
        volumePanelView!!.findViewById<ImageButton>(R.id.btnRingDown).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_RING, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun hideVolumePanel() {
        volumePanelView?.let { runCatching { windowManager.removeView(it) } }
        volumePanelView = null
    }
}
