package com.jieyin.addiction

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jieyin.addiction.algorithm.AddictionScoreCalculator
import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import com.jieyin.addiction.storage.ActivityStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var storage: ActivityStorage
    private lateinit var calculator: AddictionScoreCalculator
    
    private lateinit var scoreTextView: TextView
    private lateinit var levelTextView: TextView
    private lateinit var historyTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize storage and calculator
        storage = ActivityStorage(this)
        calculator = AddictionScoreCalculator()
        
        // Initialize views
        scoreTextView = findViewById(R.id.scoreTextView)
        levelTextView = findViewById(R.id.levelTextView)
        historyTextView = findViewById(R.id.historyTextView)
        
        // Setup buttons
        findViewById<Button>(R.id.btnSuccess).setOnClickListener { addSuccessRecord() }
        findViewById<Button>(R.id.btnFailure).setOnClickListener { addFailureRecord() }
        findViewById<Button>(R.id.btnReading).setOnClickListener { showDurationDialog(ActivityType.READING) }
        findViewById<Button>(R.id.btnExercise).setOnClickListener { showDurationDialog(ActivityType.EXERCISE) }
        findViewById<Button>(R.id.btnSleep).setOnClickListener { showDurationDialog(ActivityType.SLEEP) }
        findViewById<Button>(R.id.btnClearData).setOnClickListener { clearAllData() }
        
        // Update display
        updateDisplay()
    }
    
    private fun addSuccessRecord() {
        val record = ActivityRecord(type = ActivityType.SUCCESS)
        storage.saveRecord(record)
        updateDisplay()
    }
    
    private fun addFailureRecord() {
        val record = ActivityRecord(type = ActivityType.FAILURE)
        storage.saveRecord(record)
        updateDisplay()
    }
    
    private fun showDurationDialog(type: ActivityType) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_duration, null)
        val editText = dialogView.findViewById<EditText>(R.id.durationEditText)
        
        val title = when(type) {
            ActivityType.READING -> "读书时长（分钟）"
            ActivityType.EXERCISE -> "运动时长（分钟）"
            ActivityType.SLEEP -> "睡眠时长（分钟）"
            else -> "时长（分钟）"
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val durationStr = editText.text.toString()
                if (durationStr.isNotEmpty()) {
                    val duration = durationStr.toIntOrNull() ?: 0
                    if (duration > 0) {
                        val record = ActivityRecord(type = type, duration = duration)
                        storage.saveRecord(record)
                        updateDisplay()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun clearAllData() {
        AlertDialog.Builder(this)
            .setTitle("确认")
            .setMessage("确定要清空所有数据吗？")
            .setPositiveButton("确定") { _, _ ->
                storage.clearAllRecords()
                updateDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateDisplay() {
        val records = storage.getAllRecords()
        val score = calculator.calculateScore(records)
        val level = calculator.getScoreLevel(score)
        
        // Update score display
        scoreTextView.text = String.format("%.2f", score)
        levelTextView.text = level.description
        
        // Set color based on level
        val color = when(level) {
            ScoreLevel.CRITICAL -> Color.parseColor("#D32F2F")
            ScoreLevel.WARNING -> Color.parseColor("#F57C00")
            ScoreLevel.GOOD -> Color.parseColor("#388E3C")
            ScoreLevel.EXCELLENT -> Color.parseColor("#1976D2")
        }
        levelTextView.setTextColor(color)
        
        // Update history
        updateHistory(records)
    }
    
    private fun updateHistory(records: List<ActivityRecord>) {
        if (records.isEmpty()) {
            historyTextView.text = "暂无记录"
            return
        }
        
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val historyText = StringBuilder()
        
        // Show last 10 records
        records.sortedByDescending { it.timestamp }
            .take(10)
            .forEach { record ->
                val date = dateFormat.format(Date(record.timestamp))
                val typeName = when(record.type) {
                    ActivityType.SUCCESS -> "成功"
                    ActivityType.FAILURE -> "失败"
                    ActivityType.READING -> "读书 ${record.duration}分钟"
                    ActivityType.EXERCISE -> "运动 ${record.duration}分钟"
                    ActivityType.SLEEP -> "睡眠 ${record.duration}分钟"
                }
                historyText.append("$date - $typeName\n")
            }
        
        historyTextView.text = historyText.toString()
    }
}
