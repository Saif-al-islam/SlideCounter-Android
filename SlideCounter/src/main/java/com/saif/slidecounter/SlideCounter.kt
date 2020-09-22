package com.saif.slidecounter

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.animation.ArgbEvaluatorCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.appcompat.widget.AppCompatTextView
import android.util.AttributeSet
import android.view.*
import android.view.animation.AnticipateInterpolator
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

class SlideCounter : ConstraintLayout, View.OnTouchListener
{
    lateinit var tv_counter: AppCompatTextView
    lateinit var iv_inc: ImageView
    lateinit var iv_dec: ImageView

    // Attrs;
    var minCounter = 0
    /* -1 : no maximum is Determined */
    var maxCounter = -1
    var stepCounter = 1
    var currentCounter = minCounter
    var counterInMilliSeconds: Long = 400
    var startColorCounter: Int = Color.WHITE
    var endColorCounter: Int = Color.WHITE
    //
    private val isLTR by lazy { isViewLTR() }
    private var xDelta: Float =  -1F
    private var startDelta: Float =  -1F
    private val counterHandler: Handler by lazy { Handler() }
    private lateinit var popWindowCounter:PopupWindow
    private lateinit var popTextCounter: TextView
    private var currentColor = 0
    private var isCounterDraggable = true
    private var listener: OnSlideCounterListener? = null

    constructor(context: Context?) : super(context)
    {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    {
        val attrArray = context?.theme?.obtainStyledAttributes(attrs, R.styleable.SlideCounter, 0, 0)
        var radiusCounter = 30F
        try
        {
            // TextView Attrs:
            attrArray?.let {
                maxCounter = it.getInteger(R.styleable.SlideCounter_max_counter
                        , -1)
                minCounter = it.getInteger(R.styleable.SlideCounter_min_counter
                        , 0)
                currentCounter = it.getInteger(R.styleable.SlideCounter_init_counter
                        , 0)
                stepCounter = it.getInteger(R.styleable.SlideCounter_step_counter
                        , 1)
                radiusCounter = it.getDimension(R.styleable.SlideCounter_radius_counter
                        , 30F)
                counterInMilliSeconds = it.getInteger(R.styleable.SlideCounter_counter_in_milli_seconds
                        , 400).toLong()
                startColorCounter = it.getColor(R.styleable.SlideCounter_start_color_counter
                        , Color.WHITE)
                endColorCounter = it.getColor(R.styleable.SlideCounter_end_color_counter
                        , Color.WHITE)
            }
        }
        finally {
            attrArray?.recycle()
        }

        init(radiusCounter)
    }

    private fun init(radiusCounter: Float = 30F)
    {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        background = ResourcesCompat.getDrawable(resources, R.drawable.slide_counter_background, null)
        isSaveEnabled = true

        LayoutInflater.from(context).inflate(R.layout.slide_counter, this, true).apply {
            tv_counter =  findViewById(R.id.tv_counter)
            iv_inc =  findViewById(R.id.iv_inc)
            iv_dec = findViewById(R.id.iv_dec)
        }

        iv_inc.setOnClickListener {
            if (maxCounter != -1 && currentCounter + stepCounter > maxCounter)
                return@setOnClickListener

            increaseCounter()
        }

        iv_dec.setOnClickListener {
            if (currentCounter - stepCounter < minCounter)
                return@setOnClickListener

            decreaseCounter()
        }

        initViews(radiusCounter)
    }

    private fun initViews(radiusCounter: Float)
    {
        if (stepCounter <= 0)
            stepCounter = 1

        if (maxCounter != -1 && maxCounter < minCounter)
            throw IllegalStateException("maximum number of the counter must be larger than minimum or equal -1 for infinite")

        // TextView:
        with(tv_counter)
        {
            text = currentCounter.toString()
            setOnTouchListener(this@SlideCounter)
            background = GradientDrawable().apply {
                cornerRadius = radiusCounter
                setColor(startColorCounter)
            }
        }
    }


    override fun onTouch(v: View?, event: MotionEvent?): Boolean
    {
        if (v == null || event == null || !isCounterDraggable)
            return true

        val xTouch = event.rawX
        val maxRight = width - v.width.toFloat()

        when(event.action)
        {
            MotionEvent.ACTION_DOWN ->
            {
                startDelta = v.x
                xDelta = v.x - xTouch

                showPopWindow()
            }
            MotionEvent.ACTION_MOVE ->
            {
                if (v.x > 0 && v.x < maxRight)
                {
                    val xView = xTouch + xDelta
                    var colorFraction = (v.x / maxRight) * 2
                    if (colorFraction >= 1) colorFraction -= 1
                    else colorFraction = 1 - colorFraction

                    currentColor = ArgbEvaluatorCompat.getInstance().evaluate(colorFraction, startColorCounter, endColorCounter)
                    changeCounterBackground(currentColor)

                    if (xView <= 0)
                    {
                        v.x = 0F
                        if (isLTR)
                            startDecreaseCounter()
                        else
                            startIncreaseCounter()
                    }
                    else if (xView >= maxRight)
                    {
                        v.x = maxRight
                        if (isLTR)
                            startIncreaseCounter()
                        else
                            startDecreaseCounter()
                    }
                    else
                        v.x = xView
                }
            }
            MotionEvent.ACTION_UP ->
            {
                if (::popWindowCounter.isInitialized)
                    popWindowCounter.dismiss()

                stopCounter()
                v.animate()
                        .setInterpolator(AnticipateInterpolator(0.9f))
                        .setDuration(300)
                        .x(startDelta)
                        .setListener(object : AnimationListener {
                            override fun onAnimationEnd(p0: Animator?) {
                                isCounterDraggable = true
                            }
                            override fun onAnimationStart(p0: Animator?) {
                                isCounterDraggable = false
                            }
                        })
                        .start()
                ValueAnimator.ofInt(currentColor, startColorCounter)
                        .apply {
                            setDuration(300)
                            setEvaluator(ArgbEvaluatorCompat())
                            addUpdateListener {
                                changeCounterBackground(it.getAnimatedValue() as Int) }
                            start()
                        }
            }
        }

        return true
    }

    private fun changeCounterBackground(color: Int) =
            (tv_counter.background as GradientDrawable).setColor(color)


    private val increaseCounterRunnable by lazy { Runnable {
        if (maxCounter != -1 && currentCounter + stepCounter > maxCounter)
            return@Runnable

        increaseCounter()
        popTextCounter.text = currentCounter.toString()

        startIncreaseCounter(counterInMilliSeconds)
    } }

    private val decreaseCounterRunnable by lazy { Runnable {
        if (currentCounter - stepCounter < minCounter)
            return@Runnable

        decreaseCounter()
        popTextCounter.text = currentCounter.toString()

        startDecreaseCounter(counterInMilliSeconds)
    } }

    private fun startIncreaseCounter(delay: Long = 0)
    {
        counterHandler.postDelayed(increaseCounterRunnable, delay)
    }

    private fun startDecreaseCounter(delay: Long = 0)
    {
        counterHandler.postDelayed(decreaseCounterRunnable, delay)
    }

    private fun stopCounter()
    {
        counterHandler.removeCallbacks(increaseCounterRunnable)
        counterHandler.removeCallbacks(decreaseCounterRunnable)
    }

    /** @return true if the View is Left to Right */
    private fun isViewLTR() = resources.configuration.layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR


    private fun showPopWindow()
    {
        if (!::popTextCounter.isInitialized)
            popTextCounter = TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.white))
                background = ContextCompat.getDrawable(context, R.drawable.rect_corner_slide_counter_tooltip)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
        if (!::popWindowCounter.isInitialized)
        {
            popWindowCounter = PopupWindow(popTextCounter, ViewGroup.LayoutParams.WRAP_CONTENT
                    , ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        popTextCounter.text = currentCounter.toString()
        popWindowCounter.showAtLocation(this, Gravity.CENTER_HORIZONTAL, 0, -(height/2)-20)
    }


    private fun increaseCounter()
    {
        currentCounter += stepCounter
        tv_counter.text = currentCounter.toString()

        listener?.onSliderValueChanged(currentCounter)
    }

    private fun decreaseCounter()
    {
        currentCounter -= stepCounter
        tv_counter.text = currentCounter.toString()

        listener?.onSliderValueChanged(currentCounter)
    }

    fun setSlideCounterListener(listener: OnSlideCounterListener)
    {
        this.listener = listener
    }

    override fun onSaveInstanceState(): Parcelable?
    {
        val state =  SavedState(super.onSaveInstanceState()!!)
        state.currentCounter = currentCounter
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?)
    {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)

        currentCounter = savedState.currentCounter
        tv_counter.text = currentCounter.toString()
    }

    private class SavedState : BaseSavedState
    {
        var currentCounter: Int = 0

        constructor(superState: Parcelable) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            currentCounter = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(currentCounter)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    interface AnimationListener : Animator.AnimatorListener
    {
        override fun onAnimationRepeat(p0: Animator?) {
        }

        override fun onAnimationEnd(p0: Animator?) {
        }

        override fun onAnimationCancel(p0: Animator?) {
        }

        override fun onAnimationStart(p0: Animator?) {
        }
    }

    interface OnSlideCounterListener
    {
        /**
         * This Function is called every time the Current value of the counter change
         * @param value the current value of the view
         * */
        fun onSliderValueChanged(value: Int)
    }
}