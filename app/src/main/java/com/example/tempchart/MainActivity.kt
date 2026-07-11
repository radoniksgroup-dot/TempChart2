
package com.example.tempchart

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var infoText: TextView

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val body = intent?.getStringExtra("sms_body") ?: return
            parseAndDraw(body)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.chart)
        infoText = findViewById(R.id.infoText)

        // درخواست مجوز پیامک
        val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) {
            ActivityCompat.requestPermissions(this, perms, 1)
        }

        // ثبت گیرنده داخلی
        val filter = IntentFilter("TEMP_SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
        }
    }

    // فرمت پیامک:
    // S3 start=00:00 step=1h
    // 24.5,25.1,24.8,...
    private fun parseAndDraw(sms: String) {
        try {
            val lines = sms.trim().split("\n")
            val header = lines[0].trim()
            val dataLine = lines.getOrElse(1) { "" }.trim()

            // استخراج شماره سنسور
            val sensor = Regex("S(\\d+)").find(header)?.groupValues?.get(1) ?: "?"

            // استخراج زمان شروع
            val startTime = Regex("start=([0-9:]+)").find(header)?.groupValues?.get(1) ?: "00:00"

            // استخراج فاصله زمانی
            val step = Regex("step=(\\S+)").find(header)?.groupValues?.get(1) ?: "1h"

            // پارس اعداد دما
            val temps = dataLine.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }

            if (temps.isEmpty()) {
                infoText.text = "پیامک دریافت شد ولی داده دمایی پیدا نشد."
                return
            }

            infoText.text = "سنسور $sensor | شروع: $startTime | فاصله: $step | تعداد: ${temps.size}"

            drawChart(temps, startTime, step)

        } catch (e: Exception) {
            infoText.text = "خطا در خواندن پیامک: ${e.message}"
        }
    }

    private fun drawChart(temps: List<Float>, startTime: String, step: String) {
        val entries = ArrayList<Entry>()
        for (i in temps.indices) {
            entries.add(Entry(i.toFloat(), temps[i]))
        }

        val dataSet = LineDataSet(entries, "دما (سلسیوس)")
        dataSet.setDrawCircles(true)
        dataSet.circleRadius = 3f
        dataSet.lineWidth = 2f
        dataSet.setDrawValues(false)

        chart.data = LineData(dataSet)

        // ساخت برچسب‌های محور افقی بر اساس زمان شروع و فاصله
        val labels = buildTimeLabels(startTime, step, temps.size)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.labelRotationAngle = -45f

        chart.description.isEnabled = false
        chart.invalidate()
    }

    // زمان شروع مثل 00:00 و step مثل 1h یا 30m
    private fun buildTimeLabels(startTime: String, step: String, count: Int): List<String> {
        val labels = ArrayList<String>()
        try {
            val parts = startTime.split(":")
            var hour = parts[0].toInt()
            var minute = parts.getOrElse(1) { "0" }.toInt()

            // تبدیل step به دقیقه
            val stepMinutes = when {
                step.endsWith("h") -> (step.dropLast(1).toIntOrNull() ?: 1) * 60
                step.endsWith("m") -> step.dropLast(1).toIntOrNull() ?: 60
                else -> 60
            }

            for (i in 0 until count) {
                labels.add(String.format("%02d:%02d", hour, minute))
                minute += stepMinutes
                hour += minute / 60
                minute %= 60
                hour %= 24
            }
        } catch (e: Exception) {
            for (i in 0 until count) labels.add(i.toString())
        }
        return labels
    }
}
