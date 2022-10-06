package viby.arcgaugetest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
    ArcProgressBar представляет собой дугу в 270°. Диапазон 0..range соответствует этой дуге.
    Я решил не заморачиваться и установил цену деления в 0,01°. После этого я опять решил не
    заморачиваться с Float и округлениями и представил шкалу 0°-270° с шагом 0,01° через
    значения типа Int от 0 до 27_000.

    Мы конвертируем значение value (которое дожно входить в 0..range) в значение targetAngle.
    Затем анимируем изменения с шагом ANIMATION_STEP и периодом анимации ANIMATION_DELAY, что означает,
    что шкала будет меняться на ANIMATION_STEP (в 0,01°) каждые ANIMATION_DELAY миллисекунд.

    Текущее значение угла на ArcProgressBar хранится в currentAngle (в 0,01°).

    Какие данные мы должны получать:
        - range (Int)
        - warningValue (Int)
        - alarmValue (Int)
        - units
        - value (Int)
 */

class ArcGauge : View {

    companion object {
        // VM stands for Vector Model
        private const val VM_VIEW_SIZE = 360F
        private const val VM_BIG_CIRCLE_RADIUS = 80F
        private const val VM_ARC_RADIUS = 95F
        private const val VM_ARC_THICKNESS = 20F
        private const val VM_LEVEL_TEXT_OFFSET_ABOVE_CIRCLE = 5F
        private const val VM_BIG_TEXT_SIZE = 25F
        private const val VM_SMALL_TEXT_SIZE = 20F

        private const val ANIMATION_STEP = 10 // unit is degree/100
        private const val ANIMATION_DELAY = 1L // unit is ms

        private const val LEVEL_CIRCLE_POSITION_RADIUS =
            140F // расстояние от центра до центра круга уровня
    }

    private val neutralColor = Color.parseColor("#FFCCCCCC")
    private val greenColor = Color.parseColor("#FF99CC33")
    private val yellowColor = Color.parseColor("#FFFAA402")
    private val redColor = Color.parseColor("#FFCC3300")

    //region Глобальные параметры индикатор
    private var range = -1
    private var warningLevel = -1
    private var alarmLevel = -1
    private var unit = ""
    private var label = ""
    private var value = -1
    private var isActive = false
    //endregion

    //region Coroutine stuff
    private var animationJob: Job = Job()
    //endregion

    //region Вспомогательные проперти для рисования уровней warning и alarm
    private val levelMarks =
        mapOf(LevelMarkType.WARNING to LevelMarkData(), LevelMarkType.ALARM to LevelMarkData())
    private val levelPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        strokeWidth = 5F
        style = Paint.Style.STROKE
    }
    private val levelTextPaint = TextPaint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        strokeWidth = 5f
    }
    //endregion

    //region TextPaint для текста внутри большого круга (значение и единицы измерения)
    private val bigCircleTextPaint = TextPaint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        strokeWidth = 5f
    }
    //endregion

    //region Helpers for coloring and styling
    private val helperPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        strokeWidth = 5f
    }
    var currentColor = neutralColor
    //endregion

    private var scale = 1F // global vector model scaling factor
    private var warningDrawable: Drawable? = null
    private var alarmDrawable: Drawable? = null
    private val center = PointF(0F, 0F)
    private var bigCircleRadius = VM_BIG_CIRCLE_RADIUS
    private var arcRadius = VM_ARC_RADIUS
    private var arcThickness = VM_ARC_THICKNESS
    private var arcRect = RectF()
    private var arcStart = PointF()
    private var arcEnd = PointF()
    private var targetAngle = 0 // unit is degree/100
    private var currentAngle = 0 // unit is degree/100
    private var bigTextSize = VM_BIG_TEXT_SIZE
    private var smallTextSize = VM_SMALL_TEXT_SIZE

    //region constructors and init
    constructor(context: Context?) : super(context) {}

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {}

    init {
        warningDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_warning)
        alarmDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_alarm)
    }
    //endregion

    //region Public methods
    /* TODO: make all parameters optional, so it's possible to change only one gauge parameter,
       but it will take a lot of 'if's.
       It's also should be possible to skip any of Warning or Alarm levels or set them later.
       So the situation when newly set Alarm level is lower when Warning level should be handled.
       In case of any conflict, Alarm level overrides Warning level.
       Another special situation is when newly set range is less than any of levels. */
    /**
     * первоначальная настройка индикатора (первоначальная и последующая)
     */
    fun setupArcGauge(
        newRange: Int,
        newAlarmLevel: Int,
        newWarningLevel: Int,
        newUnit: String = "",
        newLabel: String = ""
    ) {
        if (newRange == range && newAlarmLevel == alarmLevel &&
            newWarningLevel == warningLevel && newUnit == unit && newLabel == label
        ) {
            return
        }

        if (newRange < 1) return
        range = newRange

        alarmLevel = if (newAlarmLevel in 0..range) {
            newAlarmLevel
        } else {
            range + 1
        }
        calculateLevelPosition(LevelMarkType.ALARM, alarmLevel)

        warningLevel = if (newWarningLevel in 0..range && newWarningLevel < alarmLevel) {
            newWarningLevel
        } else {
            range + 1
        }
        calculateLevelPosition(LevelMarkType.WARNING, warningLevel)

        unit = newUnit
        label = newLabel

        invalidate()
    }

    /** Устанавливает новое значение индикатора */
    fun setValue(newValue: Int) {
        if (newValue == value) return

        if (!isActive) return
        // при первом получении данных переводим в активное состояние
        // if (value == -1) isActive = true

        value = when {
            newValue > range -> range
            newValue < 0 -> 0
            else -> newValue
        }
        targetAngle = calculateCentiDegreeAngle(value)
        startAnimation()
    }

    fun restore(isActive: Boolean, lastValue: Int) {
        this.isActive = isActive
        if (lastValue in 0..range) {
            value = lastValue
            targetAngle = calculateCentiDegreeAngle(value)
            currentAngle = targetAngle
        }
        invalidate()
    }

    /** Переводит индикаторв режим paused (всё серое, текущее значение не отображается) */
    fun pause() {
        isActive = false
        invalidate()
    }

    /** Выводит из режима paused и восстанавливает работу индикатора */
    fun resume() {
        isActive = true
        invalidate()
    }
    //endregion

    //region Overridden methods
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        scale = minOf(width, height).toFloat() / VM_VIEW_SIZE

        // данные для центрального круга
        bigCircleRadius = VM_BIG_CIRCLE_RADIUS * scale
        center.x = (VM_VIEW_SIZE / 2) * scale
        center.y = (VM_VIEW_SIZE / 2) * scale

        // данные для фона арки
        arcRadius = VM_ARC_RADIUS * scale
        arcThickness = VM_ARC_THICKNESS * scale
        arcRect.apply {
            left = center.x - arcRadius
            top = center.y - arcRadius
            right = center.x + arcRadius
            bottom = center.y + arcRadius
        }
        val delta = arcRadius * sin(PI.toFloat() / 4)
        arcStart.apply {
            x = center.x - delta
            y = center.y + delta
        }
        arcEnd.apply {
            x = center.x + delta
            y = center.y + delta
        }

        // данные для уровней warning и alarm
        calculateLevelPosition(LevelMarkType.ALARM, alarmLevel)
        calculateLevelPosition(LevelMarkType.WARNING, warningLevel)

        // размер текста
        smallTextSize = (VM_SMALL_TEXT_SIZE * scale).toSp()
        levelTextPaint.textSize = smallTextSize
        bigTextSize = (VM_BIG_TEXT_SIZE * scale).toSp()
        bigCircleTextPaint.textSize = bigTextSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // in case if the gauge is not set up
        if (range < 0) return

        currentColor = getColor(currentAngle)
        drawBigCircle(canvas)
        drawArcBackground(canvas)
        if (isActive && value > -1) drawArcValue(canvas)
        drawLevelMarks(canvas, LevelMarkType.WARNING)
        drawLevelMarks(canvas, LevelMarkType.ALARM)
        drawBigCircleText(canvas)
    }

    override fun onDetachedFromWindow() {
        if (animationJob.isActive) animationJob.cancel()
        super.onDetachedFromWindow()
    }
    //endregion

    //region Вспомогательные функции для рисования
    private fun drawBigCircleText(canvas: Canvas) {
        val valueText = if (isActive && value > -1) {
            "$value${if (unit.isBlank()) "" else " $unit"}"
        } else {
            "---"
        }
        canvas.drawText(
            valueText,
            center.x,
            center.y + bigTextSize,
            bigCircleTextPaint
        )

        if (isActive && value > -1) {
            canvas.drawText(
                label,
                center.x,
                center.y - smallTextSize,
                levelTextPaint
            )
        }
    }

    /** Рисует арку разного цвета и длиной соответственно значению */
    private fun drawArcValue(canvas: Canvas) {
        helperPaint.apply {
            color = currentColor
            strokeWidth = arcThickness
            style = Paint.Style.STROKE
        }
        canvas.drawArc(arcRect, 135F, currentAngle / 100F, false, helperPaint)

        helperPaint.apply {
            strokeWidth = 0F
            style = Paint.Style.FILL
        }

        // start arc cap
        canvas.drawArc(
            arcStart.x - arcThickness / 2,
            arcStart.y - arcThickness / 2,
            arcStart.x + arcThickness / 2,
            arcStart.y + arcThickness / 2,
            315F,
            180F,
            true,
            helperPaint
        )
        // если достигли максимума, то надо рисовать второй круг
        if (currentAngle == 27_000) {
            canvas.drawArc(
                arcEnd.x - arcThickness / 2,
                arcEnd.y - arcThickness / 2,
                arcEnd.x + arcThickness / 2,
                arcEnd.y + arcThickness / 2,
                45F,
                180F,
                true,
                helperPaint
            )
        }
    }

    /**  Рисует основу арки - серый цвет */
    private fun drawArcBackground(canvas: Canvas) {
        helperPaint.apply {
            color = neutralColor
            strokeWidth = arcThickness
            style = Paint.Style.STROKE
        }
        canvas.drawArc(arcRect, 135F, 270F, false, helperPaint)

        helperPaint.apply {
            strokeWidth = 0F
            style = Paint.Style.FILL
        }
        canvas.drawCircle(arcStart.x, arcStart.y, arcThickness / 2, helperPaint)
        canvas.drawCircle(arcEnd.x, arcEnd.y, arcThickness / 2, helperPaint)
    }

    /**  Рисует указатели уровней warning и alarm */
    private fun drawLevelMarks(canvas: Canvas, levelMarkType: LevelMarkType) {
        levelMarks[levelMarkType]?.takeIf { it.toShow }?.let {
            canvas.drawLine(
                it.lineStartX,
                it.lineStartY,
                it.lineEndX,
                it.lineEndY,
                levelPaint
            )
            helperPaint.apply {
                color = neutralColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(it.circleCenterX, it.circleCenterY, it.circleRadius, helperPaint)
            canvas.drawCircle(it.circleCenterX, it.circleCenterY, it.circleRadius, levelPaint)

            // Drawing warning or alarm icon inside the circle
            when (levelMarkType) {
                LevelMarkType.WARNING -> warningDrawable
                LevelMarkType.ALARM -> alarmDrawable
            }?.apply {
                setBounds(
                    (it.circleCenterX - it.circleRadius * 0.6).toInt(),
                    (it.circleCenterY - it.circleRadius * 0.6).toInt(),
                    (it.circleCenterX + it.circleRadius * 0.6).toInt(),
                    (it.circleCenterY + it.circleRadius * 0.6).toInt()
                )
                draw(canvas)
            }

            // Drawing text level value above the circle
            canvas.drawText(
                when (levelMarkType) {
                    LevelMarkType.WARNING -> warningLevel.toString()
                    LevelMarkType.ALARM -> alarmLevel.toString()
                },
                it.circleCenterX,
                it.circleCenterY - it.circleRadius - VM_LEVEL_TEXT_OFFSET_ABOVE_CIRCLE * scale,
                levelTextPaint
            )
        }
    }

    /**  рисует центральный круг */
    private fun drawBigCircle(canvas: Canvas) {
        helperPaint.color = currentColor
        helperPaint.style = Paint.Style.FILL
        canvas.drawCircle(center.x, center.y, bigCircleRadius, helperPaint)
    }
    //endregion

    /** Рассчитать новые координаты для рисования уровня. */
    private fun calculateLevelPosition(levelMarkType: LevelMarkType, levelValue: Int) {
        if (levelValue !in 0..range) {
            levelMarks[levelMarkType]?.toShow = false
            return
        } else {
            levelMarks[levelMarkType]?.toShow = true
        }
        val angle = ((levelValue.toFloat() / range * 1.5 + 0.75) * -PI).toFloat()
        val sin = sin(angle)
        val cos = cos(angle)
        levelMarks[levelMarkType]?.lineStartX = center.x + 85 * scale * cos
        levelMarks[levelMarkType]?.lineStartY = center.y - 85 * scale * sin
        levelMarks[levelMarkType]?.lineEndX = center.x + 120 * scale * cos
        levelMarks[levelMarkType]?.lineEndY = center.y - 120 * scale * sin
        levelMarks[levelMarkType]?.circleCenterX = center.x + 140 * scale * cos
        levelMarks[levelMarkType]?.circleCenterY = center.y - 140 * scale * sin
        levelMarks[levelMarkType]?.circleRadius = 20 * scale
        levelMarks[levelMarkType]?.valueInCentiDegree = calculateCentiDegreeAngle(levelValue)
    }

    private fun startAnimation() {
        if (animationJob.isActive) animationJob.cancel()
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && currentAngle != targetAngle) {
                if (currentAngle < targetAngle) {
                    currentAngle += ANIMATION_STEP
                    if (currentAngle > targetAngle) currentAngle = targetAngle
                } else {
                    currentAngle -= ANIMATION_STEP
                    if (currentAngle < targetAngle) currentAngle = targetAngle
                }
                invalidate()
                delay(ANIMATION_DELAY)
            }
        }
    }

    /** Given that ProgressBar value is 0..range and ProgressBar angle is 0..270°,
     *  it calculates the angle for [value] in 0.01° units on the ProgressBar. */
    private fun calculateCentiDegreeAngle(value: Int): Int {
        return value * 27_000 / range
    }

    private fun getColor(centiDegreeValue: Int): Int {
        return when {
            !isActive || value < 0 -> neutralColor
            centiDegreeValue >= levelMarks[LevelMarkType.ALARM]!!.valueInCentiDegree -> redColor
            centiDegreeValue >= levelMarks[LevelMarkType.WARNING]!!.valueInCentiDegree -> yellowColor
            else -> greenColor
        }
    }

    // Helps calculate textSize for different view sizes
    private fun Float.toSp(): Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            context.resources.displayMetrics
        )
        return px / context.resources.displayMetrics.scaledDensity
    }

    //region private classes
    enum class LevelMarkType { WARNING, ALARM }

    data class LevelMarkData(
        var toShow: Boolean = false, // показывать или нет (если не входит в range и т.д.)
        var lineStartX: Float = 0f,
        var lineStartY: Float = 0f,
        var lineEndX: Float = 0f,
        var lineEndY: Float = 0f,
        var circleCenterX: Float = 0f,
        var circleCenterY: Float = 0f,
        var circleRadius: Float = 0f,
        var valueInCentiDegree: Int = -1
    )
    //endregion
}
