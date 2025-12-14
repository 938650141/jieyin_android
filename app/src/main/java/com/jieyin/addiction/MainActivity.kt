package com.jieyin.addiction

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jieyin.addiction.algorithm.AddictionScoreCalculator
import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import com.jieyin.addiction.storage.ActivityStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var storage: ActivityStorage
    private lateinit var calculator: AddictionScoreCalculator
    
    private lateinit var scoreTextView: TextView
    private lateinit var levelTextView: TextView
    private lateinit var historyContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize storage and calculator
        storage = ActivityStorage(this)
        calculator = AddictionScoreCalculator()
        
        // Initialize views
        scoreTextView = findViewById(R.id.scoreTextView)
        levelTextView = findViewById(R.id.levelTextView)
        historyContainer = findViewById(R.id.historyContainer)
        
        // Setup buttons - 成功、失败、运动、睡眠四个按钮
        findViewById<Button>(R.id.btnSuccess).setOnClickListener { addSuccessRecord() }
        findViewById<Button>(R.id.btnFailure).setOnClickListener { addFailureRecord() }
        findViewById<Button>(R.id.btnExercise).setOnClickListener { showExerciseConfirmDialog() }
        findViewById<Button>(R.id.btnSleep).setOnClickListener { showSleepScoreDialog() }
        findViewById<Button>(R.id.btnClearData).setOnClickListener { clearAllData() }
        
        // Update display
        updateDisplay()
    }
    
    private fun addSuccessRecord() {
        val record = ActivityRecord(type = ActivityType.SUCCESS)
        val allRecords = storage.getAllRecords()
        val scoreChange = calculator.calculateScoreChange(record, allRecords)
        val recordWithScore = record.copy(scoreChange = scoreChange)
        storage.saveRecord(recordWithScore)
        updateDisplay()
    }
    
    private fun addFailureRecord() {
        val record = ActivityRecord(type = ActivityType.FAILURE)
        val allRecords = storage.getAllRecords()
        val scoreChange = calculator.calculateScoreChange(record, allRecords)
        val recordWithScore = record.copy(scoreChange = scoreChange)
        storage.saveRecord(recordWithScore)
        updateDisplay()
    }
    
    /**
     * 运动确认对话框 - 确认是否运动超过30分钟
     */
    private fun showExerciseConfirmDialog() {
        // 检查当天是否有失败记录
        if (hasFailureToday()) {
            Toast.makeText(this, "今日已有失败记录，运动不能加分", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("确认运动")
            .setMessage("今日运动是否超过30分钟？")
            .setPositiveButton("是") { _, _ ->
                val record = ActivityRecord(type = ActivityType.EXERCISE, duration = 30)
                val allRecords = storage.getAllRecords()
                val scoreChange = calculator.calculateScoreChange(record, allRecords)
                val recordWithScore = record.copy(scoreChange = scoreChange)
                storage.saveRecord(recordWithScore)
                updateDisplay()
            }
            .setNegativeButton("否", null)
            .show()
    }
    
    /**
     * 睡眠得分输入对话框 - 输入百分制得分
     */
    private fun showSleepScoreDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_duration, null)
        val editText = dialogView.findViewById<EditText>(R.id.durationEditText)
        editText.hint = "例如: 75"
        
        AlertDialog.Builder(this)
            .setTitle("睡眠得分（百分制）")
            .setMessage("请输入睡眠得分（0-100），得分高于60加分，低于60扣分")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val scoreStr = editText.text.toString()
                if (scoreStr.isNotEmpty()) {
                    val sleepScore = scoreStr.toIntOrNull() ?: 0
                    if (sleepScore in 0..100) {
                        // 检查是否是扣分
                        val isDeduction = sleepScore < 60
                        
                        // 扣分无视当天失败，但加分时如果当天有失败则不能加分
                        if (!isDeduction && hasFailureToday()) {
                            Toast.makeText(this, "今日已有失败记录，睡眠不能加分", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        
                        val record = ActivityRecord(type = ActivityType.SLEEP, duration = sleepScore)
                        val allRecords = storage.getAllRecords()
                        val scoreChange = calculator.calculateScoreChange(record, allRecords)
                        val recordWithScore = record.copy(scoreChange = scoreChange)
                        storage.saveRecord(recordWithScore)
                        updateDisplay()
                    } else {
                        Toast.makeText(this, "请输入0-100之间的得分", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 检查当天是否有失败记录
     */
    private fun hasFailureToday(): Boolean {
        val records = storage.getAllRecords()
        val today = Calendar.getInstance()
        val todayYear = today.get(Calendar.YEAR)
        val todayDay = today.get(Calendar.DAY_OF_YEAR)
        
        // Reuse a single Calendar instance for checking each failure
        val recordCal = Calendar.getInstance()
        
        return records.any { record ->
            if (record.type != ActivityType.FAILURE) return@any false
            recordCal.timeInMillis = record.timestamp
            todayYear == recordCal.get(Calendar.YEAR) &&
            todayDay == recordCal.get(Calendar.DAY_OF_YEAR)
        }
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
        
        // Set color based on level - 六个等级
        val color = when(level) {
            ScoreLevel.LEVEL_1 -> Color.parseColor("#D32F2F")  // 严重成瘾 - 红色
            ScoreLevel.LEVEL_2 -> Color.parseColor("#E64A19")  // 重度成瘾 - 深橙色
            ScoreLevel.LEVEL_3 -> Color.parseColor("#F57C00")  // 中度成瘾 - 橙色
            ScoreLevel.LEVEL_4 -> Color.parseColor("#FBC02D")  // 轻度成瘾 - 黄色
            ScoreLevel.LEVEL_5 -> Color.parseColor("#388E3C")  // 即将戒除 - 绿色
            ScoreLevel.LEVEL_6 -> Color.parseColor("#1976D2")  // 已戒除 - 蓝色
        }
        levelTextView.setTextColor(color)
        
        // Update history
        updateHistory(records)
    }
    
    private fun updateHistory(records: List<ActivityRecord>) {
        historyContainer.removeAllViews()
        
        if (records.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "暂无记录"
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            historyContainer.addView(emptyText)
            return
        }
        
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        
        // Show last 10 records
        records.sortedByDescending { it.timestamp }
            .take(10)
            .forEach { record ->
                val recordView = createRecordView(record, dateFormat)
                historyContainer.addView(recordView)
            }
    }
    
    private fun createRecordView(record: ActivityRecord, dateFormat: SimpleDateFormat): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val date = dateFormat.format(Date(record.timestamp))
        val (typeName, emoji) = when(record.type) {
            ActivityType.SUCCESS -> Pair("成功", "✅")
            ActivityType.FAILURE -> Pair("失败", "❌")
            ActivityType.EXERCISE -> Pair("运动", "🏃")
            ActivityType.SLEEP -> Pair("睡眠 ${record.duration}分", "😴")
        }
        
        // 显示加分/扣分情况
        val scoreChangeText = when {
            record.scoreChange > 0 -> "+${String.format("%.2f", record.scoreChange)}"
            record.scoreChange < 0 -> String.format("%.2f", record.scoreChange)
            else -> "0"
        }
        
        val scoreColor = when {
            record.scoreChange > 0 -> Color.parseColor("#388E3C")  // 绿色
            record.scoreChange < 0 -> Color.parseColor("#D32F2F")  // 红色
            else -> Color.GRAY
        }
        
        // 记录信息文本
        val infoText = TextView(this).apply {
            text = "$emoji $date $typeName [$scoreChangeText]"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        container.addView(infoText)
        
        // 检查是否可以修改（一周内）
        val canModify = storage.canModifyRecord(record.id)
        
        // 修改按钮
        val editBtn = Button(this).apply {
            text = "改"
            textSize = 12f
            isEnabled = canModify
            alpha = if (canModify) 1f else 0.5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            setOnClickListener {
                if (canModify) {
                    showEditRecordDialog(record)
                } else {
                    Toast.makeText(this@MainActivity, "超过一周的记录不能修改", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 删除按钮
        val deleteBtn = Button(this).apply {
            text = "删"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 4
            }
            setOnClickListener {
                showDeleteConfirmDialog(record)
            }
        }
        
        container.addView(editBtn)
        container.addView(deleteBtn)
        
        return container
    }
    
    /**
     * 显示修改记录对话框
     */
    private fun showEditRecordDialog(record: ActivityRecord) {
        when (record.type) {
            ActivityType.SLEEP -> {
                val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_duration, null)
                val editText = dialogView.findViewById<EditText>(R.id.durationEditText)
                editText.hint = "例如: 75"
                editText.setText(record.duration.toString())
                
                AlertDialog.Builder(this)
                    .setTitle("修改睡眠得分（百分制）")
                    .setView(dialogView)
                    .setPositiveButton("确定") { _, _ ->
                        val scoreStr = editText.text.toString()
                        if (scoreStr.isNotEmpty()) {
                            val sleepScore = scoreStr.toIntOrNull() ?: 0
                            if (sleepScore in 0..100) {
                                val updatedRecord = record.copy(duration = sleepScore)
                                storage.updateRecord(record.id, updatedRecord)
                                recalculateAllScores()
                                updateDisplay()
                            } else {
                                Toast.makeText(this, "请输入0-100之间的得分", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            else -> {
                // 成功、失败、运动记录不需要修改数值
                Toast.makeText(this, "该类型记录无法修改数值", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(record: ActivityRecord) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这条记录吗？删除后将重新计算评分。")
            .setPositiveButton("确定") { _, _ ->
                storage.deleteRecord(record.id)
                recalculateAllScores()
                updateDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 重新计算所有记录的得分变化
     */
    private fun recalculateAllScores() {
        val records = storage.getAllRecords().sortedBy { it.timestamp }
        val updatedRecords = mutableListOf<ActivityRecord>()
        
        for (record in records) {
            val scoreChange = calculator.calculateScoreChange(record, updatedRecords)
            val updatedRecord = record.copy(scoreChange = scoreChange)
            updatedRecords.add(updatedRecord)
        }
        
        storage.saveAllRecords(updatedRecords)
    }
}
