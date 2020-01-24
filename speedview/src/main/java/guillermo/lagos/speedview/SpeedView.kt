package guillermo.lagos.speedview

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.ArrayList
import kotlin.math.roundToInt
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Created by Guillermo Lagos on 23/01/2020.
 */


class SpeedView : View {

    var maxSpeed = DEFAULT_MAX_SPEED
        set(maxSpeed) {
            require(maxSpeed > 0) { "non positive value specified as max speed." }
            field = maxSpeed
            invalidate()
        }
    var speed = 0.0
        set(speed) {
            var speed = speed
            require(speed >= 0) { "non positive value specified as a speed." }
            if (speed > this.maxSpeed)
                speed = this.maxSpeed
            field = speed
            invalidate()
        }
    var defaultColor = Color.rgb(180, 180, 180)
        set(defaultColor) {
            field = defaultColor
            invalidate()
        }
    var majorTickStep = DEFAULT_MAJOR_TICK_STEP
        set(majorTickStep) {
            require(majorTickStep > 0) { "non positive value specified as a major tick step." }
            field = majorTickStep
            invalidate()
        }
    var minorTicks = DEFAULT_MINOR_TICKS
        set(minorTicks) {
            field = minorTicks
            invalidate()
        }
    var labelConverter: LabelConverter? = null
        set(labelConverter) {
            field = labelConverter
            invalidate()
        }

    private val ranges = ArrayList<ColoredRange>()

    private var backgroundPaint: Paint? = null
    private var backgroundInnerPaint: Paint? = null
    private var maskPaint: Paint? = null
    private var needlePaint: Paint? = null
    private var ticksPaint: Paint? = null
    private var txtPaint: Paint? = null
    private var colorLinePaint: Paint? = null
    var labelTextSize: Int = 0
        set(labelTextSize) {
            field = labelTextSize
            if (txtPaint != null) {
                txtPaint!!.textSize = labelTextSize.toFloat()
                invalidate()
            }
        }

    private var mMask: Bitmap? = null

    constructor(context: Context) : super(context) {
        init()

        val density = resources.displayMetrics.density
        labelTextSize = Math.round(DEFAULT_LABEL_TEXT_SIZE_DP * density)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

        val density = resources.displayMetrics.density
        val attributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SpeedView,
            0, 0
        )

        try {
            maxSpeed = attributes.getFloat(R.styleable.SpeedView_maxSpeed, DEFAULT_MAX_SPEED.toFloat()).toDouble()
            speed = attributes.getFloat(R.styleable.SpeedView_speed, 0f).toDouble()
            labelTextSize = attributes.getDimensionPixelSize(R.styleable.SpeedView_labelTextSize, (DEFAULT_LABEL_TEXT_SIZE_DP * density).roundToInt())
        } finally {
            attributes.recycle()
        }
        init()
    }


    fun setSpeed(progress: Double, duration: Long, startDelay: Long): ValueAnimator {
        var progress = progress
        require(progress > 0) { "non positive value specified as a speed." }

        if (progress > this.maxSpeed)
            progress = this.maxSpeed

        val va = ValueAnimator.ofObject(
            TypeEvaluator<Double> { fraction, startValue, endValue -> startValue!! + fraction * (endValue!! - startValue) },
            java.lang.Double.valueOf(speed),
            java.lang.Double.valueOf(progress)
        )

        va.duration = duration
        va.startDelay = startDelay
        va.addUpdateListener { animation ->
            val value = animation.animatedValue as Double
            if (value != null)
                speed = value
        }
        va.start()
        return va
    }

    @TargetApi(11)
    fun setSpeed(progress: Double, animate: Boolean): ValueAnimator {
        return setSpeed(progress, 1500, 200)
    }

    fun clearColoredRanges() {
        ranges.clear()
        invalidate()
    }

    fun addColoredRange(begin: Double, end: Double, color: Int) {
        var begin = begin
        var end = end
        require(begin < end) { "incorrect number range specified" }
        if (begin < -5.0 / 160 * this.maxSpeed)
            begin = -5.0 / 160 * this.maxSpeed
        if (end > this.maxSpeed * (5.0 / 160 + 1))
            end = this.maxSpeed * (5.0 / 160 + 1)
        ranges.add(ColoredRange(color, begin, end))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        drawBackground(canvas)
        drawTicks(canvas)
        drawNeedle(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width: Int
        var height: Int


        width = if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
            widthSize
        } else {
            -1
        }


        height = if (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
            heightSize
        } else {
            -1
        }

        if (height >= 0 && width >= 0) {
            width = height.coerceAtMost(width)
            height = width / 2
        } else if (width >= 0) {
            height = width / 2
        } else if (height >= 0) {
            width = height * 2
        } else {
            width = 0
            height = 0
        }

        setMeasuredDimension(width, height)
    }

    private fun drawNeedle(canvas: Canvas) {
        val oval = getOval(canvas, 1f)
        val radius = oval.width() * 0.35f + 10
        val smallOval = getOval(canvas, 0.1f)

        val angle = 10 + (speed / maxSpeed * 160).toFloat()
        canvas.drawLine(
            (oval.centerX() + cos((180 - angle) / 180 * Math.PI) * smallOval.width().toDouble() * 0.5).toFloat(),
            (oval.centerY() - sin(angle / 180 * Math.PI) * smallOval.width().toDouble() * 0.5).toFloat(),
            (oval.centerX() + cos((180 - angle) / 180 * Math.PI) * radius).toFloat(),
            (oval.centerY() - sin(angle / 180 * Math.PI) * radius).toFloat(),
            needlePaint!!
        )


        canvas.drawArc(smallOval, 180f, 180f, true, backgroundPaint!!)
    }

    private fun drawTicks(canvas: Canvas) {
        val availableAngle = 160f
        val majorStep = (this.majorTickStep / this.maxSpeed * availableAngle).toFloat()
        val minorStep = majorStep / (1 + this.minorTicks)

        val majorTicksLength = 30f
        val minorTicksLength = majorTicksLength / 2

        val oval = getOval(canvas, 1f)
        val radius = oval.width() * 0.35f

        var currentAngle = 10f
        var curProgress = 0.0
        while (currentAngle <= 170) {

            canvas.drawLine(
                (oval.centerX() + cos((180 - currentAngle) / 180 * Math.PI) * (radius - majorTicksLength / 2)).toFloat(),
                (oval.centerY() - sin(currentAngle / 180 * Math.PI) * (radius - majorTicksLength / 2)).toFloat(),
                (oval.centerX() + cos((180 - currentAngle) / 180 * Math.PI) * (radius + majorTicksLength / 2)).toFloat(),
                (oval.centerY() - sin(currentAngle / 180 * Math.PI) * (radius + majorTicksLength / 2)).toFloat(),
                ticksPaint!!
            )

            for (i in 1..this.minorTicks) {
                val angle = currentAngle + i * minorStep
                if (angle >= 170 + minorStep / 2) {
                    break
                }
                canvas.drawLine(
                    (oval.centerX() + cos((180 - angle) / 180 * Math.PI) * radius).toFloat(),
                    (oval.centerY() - sin(angle / 180 * Math.PI) * radius).toFloat(),
                    (oval.centerX() + cos((180 - angle) / 180 * Math.PI) * (radius + minorTicksLength)).toFloat(),
                    (oval.centerY() - sin(angle / 180 * Math.PI) * (radius + minorTicksLength)).toFloat(),
                    ticksPaint!!
                )
            }

            if (this.labelConverter != null) {

                canvas.save()
                canvas.rotate(180 + currentAngle, oval.centerX(), oval.centerY())
                val txtX = oval.centerX() + radius + majorTicksLength / 2 + 8f
                val txtY = oval.centerY()
                canvas.rotate(+90f, txtX, txtY)
                canvas.drawText(
                    this.labelConverter!!.getLabelFor(curProgress, this.maxSpeed),
                    txtX,
                    txtY,
                    txtPaint!!
                )
                canvas.restore()
            }

            currentAngle += majorStep
            curProgress += this.majorTickStep
        }

        val smallOval = getOval(canvas, 0.7f)
        colorLinePaint!!.color = this.defaultColor
        canvas.drawArc(smallOval, 185f, 170f, false, colorLinePaint!!)

        for (range in ranges) {
            colorLinePaint!!.color = range.color
            canvas.drawArc(
                smallOval,
                (190 + range.begin / this.maxSpeed * 160).toFloat(),
                ((range.end - range.begin) / this.maxSpeed * 160).toFloat(),
                false,
                colorLinePaint!!
            )
        }
    }

    private fun getOval(canvas: Canvas, factor: Float): RectF {
        val oval: RectF
        val canvasWidth = canvas.width - paddingLeft - paddingRight
        val canvasHeight = canvas.height - paddingTop - paddingBottom

        if (canvasHeight * 2 >= canvasWidth) {
            oval = RectF(0f, 0f, canvasWidth * factor, canvasWidth * factor)
        } else {
            oval = RectF(
                0f,
                0f,
                canvasHeight.toFloat() * 2f * factor,
                canvasHeight.toFloat() * 2f * factor
            )
        }

        oval.offset(
            (canvasWidth - oval.width()) / 2 + paddingLeft,
            (canvasHeight * 2 - oval.height()) / 2 + paddingTop
        )

        return oval
    }

    private fun drawBackground(canvas: Canvas) {
        val oval = getOval(canvas, 1f)
        canvas.drawArc(oval, 180f, 180f, true, backgroundPaint!!)

        val innerOval = getOval(canvas, 0.9f)
        canvas.drawArc(innerOval, 180f, 180f, true, backgroundInnerPaint!!)

        val mask = Bitmap.createScaledBitmap(
            mMask!!,
            (oval.width() * 1.1).toInt(),
            (oval.height() * 1.1).toInt() / 2,
            true
        )
        canvas.drawBitmap(
            mask,
            oval.centerX() - oval.width() * 1.1f / 2,
            oval.centerY() - oval.width() * 1.1f / 2,
            maskPaint
        )
    }

    private fun init() {
        if (!isInEditMode) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint!!.style = Paint.Style.STROKE
        backgroundPaint!!.color = Color.parseColor("#CBCCD1")

        backgroundInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundInnerPaint!!.style = Paint.Style.FILL
        backgroundInnerPaint!!.color = Color.parseColor("#584EE5")

        txtPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        txtPaint!!.color = Color.parseColor("#000000")
        txtPaint!!.textSize = this.labelTextSize.toFloat()
        txtPaint!!.textAlign = Paint.Align.CENTER
        txtPaint!!.isFakeBoldText = true

        mMask =  drawableToBitmap(ContextCompat.getDrawable(context,
            R.drawable.bg_circle_speedview
        )!!)
        mMask = Bitmap.createBitmap(mMask!!, 0, 0, mMask!!.width, mMask!!.height / 2)

        maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint!!.isDither = true

        ticksPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ticksPaint!!.strokeWidth = 4.0f
        ticksPaint!!.style = Paint.Style.STROKE
        ticksPaint!!.color = this.defaultColor

        colorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        colorLinePaint!!.style = Paint.Style.STROKE
        colorLinePaint!!.strokeWidth = 5f
        colorLinePaint!!.color = this.defaultColor

        needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        needlePaint!!.strokeWidth = 5f
        needlePaint!!.style = Paint.Style.STROKE
        needlePaint!!.color = Color.parseColor("#584EE5")
    }


    interface LabelConverter {

        fun getLabelFor(progress: Double, maxProgress: Double): String

    }

    class ColoredRange(var color: Int, var begin: Double, var end: Double)

    companion object {

        private val TAG = SpeedView::class.java.simpleName

        val DEFAULT_MAX_SPEED = 100.0
        val DEFAULT_MAJOR_TICK_STEP = 20.0
        val DEFAULT_MINOR_TICKS = 1
        val DEFAULT_LABEL_TEXT_SIZE_DP = 14
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        var width = drawable.intrinsicWidth
        width = if (width > 0) width else 1
        var height = drawable.intrinsicHeight
        height = if (height > 0) height else 1

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

}
