package viby.arcgaugetest.ui.gauge

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GaugeViewModel : ViewModel() {

    val range = 10_000
    val warningLevel = (range.toFloat() / 100 * 25).toInt()
    val alarmLevel = (range.toFloat() / 100 * 90).toInt()
    val unit = "ppm"
    val label = "R744 (CO2)"

    val isGaugeActive = MutableLiveData(false)
    val sensorValue = MutableLiveData(-1)

    fun setGaugeStatus(status: Boolean) {
        isGaugeActive.value = status
    }

    fun setSensorValue(progress: Int) {
        sensorValue.value = (range.toFloat() / 100 * progress).toInt()
    }
}
