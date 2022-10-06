package viby.arcgaugetest.ui.gauge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import viby.arcgaugetest.databinding.FragmentGaugeBinding

class GaugeFragment : Fragment() {

    private var _binding: FragmentGaugeBinding? = null
    private val binding get() = _binding!!

    private val gaugeViewModel: GaugeViewModel by lazy {
        ViewModelProvider(this)[GaugeViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGaugeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHelperViews()

        binding.arcGauge.setupArcGauge(
            newRange = gaugeViewModel.range,
            newAlarmLevel = gaugeViewModel.alarmLevel,
            newWarningLevel = gaugeViewModel.warningLevel,
            newUnit = gaugeViewModel.unit,
            newLabel = gaugeViewModel.label
        )

        gaugeViewModel.isGaugeActive.observe(viewLifecycleOwner) {
            if (it) {
                binding.arcGauge.resume()
            } else {
                binding.arcGauge.pause()
            }
        }

        gaugeViewModel.sensorValue.observe(viewLifecycleOwner) {
            if (it > -1) {
                binding.arcGauge.setValue(it)
            }
        }

        binding.arcGauge.restore(
            gaugeViewModel.isGaugeActive.value ?: false,
            gaugeViewModel.sensorValue.value ?: -1
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupHelperViews() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!binding.checkboxOnRelease.isChecked) {
                    gaugeViewModel.setSensorValue(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (binding.checkboxOnRelease.isChecked) {
                    seekBar?.let {
                        gaugeViewModel.setSensorValue(it.progress)
                    }
                }
            }
        })

        binding.switchOnOff.setOnCheckedChangeListener { _, isChecked ->
            gaugeViewModel.setGaugeStatus(isChecked)
        }
    }
}
