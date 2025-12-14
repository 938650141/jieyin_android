package com.jieyin.addiction.algorithm

import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import kotlin.math.max
import kotlin.math.min
import java.util.Calendar

/**
 * Core algorithm for calculating addiction score
 * 
 * Rules:
 * - Base score: 60
 * - Success: Add points based on time interval from last success/failure (hours * 0.01)
 * - Failure: Deduct points based on failure frequency and recency
 * - Exercise: Add 0.1 points if exercised over 30 minutes (only if no failure today)
 * - Sleep: Add/deduct points based on sleep score ((score - 60) * 0.01), deduction ignores failure
 */
class AddictionScoreCalculator {
    
    companion object {
        private const val BASE_SCORE = 60.0
        private const val MIN_SCORE = 0.0
        private const val MAX_SCORE = 100.0
        
        // Scoring unit and daily limits
        private const val MAX_DAILY_ADDITION = 0.5  // Maximum points added per day
        private const val MAX_DAILY_DEDUCTION = 0.5  // Maximum points deducted per day
        
        // Time interval scoring (for Success) - 按小时 * 0.01
        private const val SUCCESS_POINTS_PER_HOUR = 0.01
        
        // Failure scoring
        private const val FAILURE_BASE_PENALTY = 0.10  // Base penalty 0.10
        private const val FAILURE_RECENT_WINDOW_DAYS = 7  // Recent window in days
        
        // Exercise scoring - 超过30分钟就加0.1分
        private const val EXERCISE_POINTS = 0.1
        
        // Sleep scoring - (百分制得分 - 60) * 0.01
        private const val SLEEP_SCORE_MULTIPLIER = 0.01
        private const val SLEEP_SCORE_THRESHOLD = 60
    }
    
    /**
     * Calculate the current addiction score based on activity records
     */
    fun calculateScore(records: List<ActivityRecord>): Double {
        if (records.isEmpty()) {
            return BASE_SCORE
        }
        
        // Sort records by timestamp (oldest first)
        val sortedRecords = records.sortedBy { it.timestamp }
        
        var score = BASE_SCORE
        
        // Process each record and calculate score changes
        var lastSuccessOrFailureTime: Long? = null
        val allFailures = mutableListOf<Long>()
        
        for (record in sortedRecords) {
            when (record.type) {
                ActivityType.SUCCESS -> {
                    val points = calculateSuccessPoints(record.timestamp, lastSuccessOrFailureTime)
                    score += points
                    lastSuccessOrFailureTime = record.timestamp
                }
                
                ActivityType.FAILURE -> {
                    allFailures.add(record.timestamp)
                    val penalty = calculateFailurePenalty(record.timestamp, allFailures)
                    score -= penalty
                    lastSuccessOrFailureTime = record.timestamp
                }
                
                ActivityType.EXERCISE -> {
                    // 运动只能在当天没有失败的情况下进行加分
                    if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                        score += calculateExercisePoints()
                    }
                }
                
                ActivityType.SLEEP -> {
                    val sleepPoints = calculateSleepPoints(record.duration)
                    // 睡眠如果是扣分则无视当天有无失败
                    if (sleepPoints < 0) {
                        score += sleepPoints
                    } else if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                        // 加分时需要当天没有失败
                        score += sleepPoints
                    }
                }
            }
        }
        
        // Clamp score to valid range
        return max(MIN_SCORE, min(MAX_SCORE, score))
    }
    
    /**
     * Calculate score change for a specific record
     */
    fun calculateScoreChange(record: ActivityRecord, allRecords: List<ActivityRecord>): Double {
        val previousRecords = allRecords.filter { it.timestamp < record.timestamp }.sortedBy { it.timestamp }
        val allFailures = previousRecords.filter { it.type == ActivityType.FAILURE }.map { it.timestamp }.toMutableList()
        
        return when (record.type) {
            ActivityType.SUCCESS -> {
                val lastSuccessOrFailure = previousRecords
                    .filter { it.type == ActivityType.SUCCESS || it.type == ActivityType.FAILURE }
                    .maxByOrNull { it.timestamp }
                calculateSuccessPoints(record.timestamp, lastSuccessOrFailure?.timestamp)
            }
            
            ActivityType.FAILURE -> {
                allFailures.add(record.timestamp)
                -calculateFailurePenalty(record.timestamp, allFailures)
            }
            
            ActivityType.EXERCISE -> {
                if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                    calculateExercisePoints()
                } else {
                    0.0
                }
            }
            
            ActivityType.SLEEP -> {
                val sleepPoints = calculateSleepPoints(record.duration)
                if (sleepPoints < 0) {
                    sleepPoints
                } else if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                    sleepPoints
                } else {
                    0.0
                }
            }
        }
    }
    
    /**
     * Check if there's a failure on the same day as the given timestamp
     */
    private fun hasFailureOnSameDay(timestamp: Long, failures: List<Long>): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        return failures.any { failureTime ->
            val cal2 = Calendar.getInstance().apply { timeInMillis = failureTime }
            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
    }
    
    /**
     * Calculate points for a success event based on time since last success/failure
     * 按小时为单位 * 0.01（不满一小时部分不计入）
     */
    private fun calculateSuccessPoints(currentTime: Long, lastTime: Long?): Double {
        if (lastTime == null) {
            return SUCCESS_POINTS_PER_HOUR  // First success gets base points
        }
        
        val intervalMillis = currentTime - lastTime
        val intervalHours = (intervalMillis / (1000.0 * 60 * 60)).toLong()  // 不满一小时部分不计入
        
        // Award points based on hours of abstinence
        val points = min(intervalHours * SUCCESS_POINTS_PER_HOUR, MAX_DAILY_ADDITION)
        return points
    }
    
    /**
     * Calculate penalty for a failure event
     * 基础扣分0.1，上次失败距离越近扣分越多，从现在开始过去的一段时间内失败次数越多扣分越多
     */
    private fun calculateFailurePenalty(currentTime: Long, allFailures: List<Long>): Double {
        var penalty = FAILURE_BASE_PENALTY
        
        // 最近7天内的失败次数
        val windowMillis = FAILURE_RECENT_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        val recentFailures = allFailures.filter { currentTime - it <= windowMillis }
        
        // 根据最近失败次数增加扣分
        if (recentFailures.size > 1) {
            penalty += (recentFailures.size - 1) * 0.02
        }
        
        // 上次失败距离越近扣分越多
        val otherFailures = allFailures.filter { it < currentTime }
        if (otherFailures.isNotEmpty()) {
            val lastFailure = otherFailures.maxOrNull()!!
            val hoursSinceLastFailure = (currentTime - lastFailure) / (1000.0 * 60 * 60)
            
            // 如果距离上次失败不到24小时，额外扣分
            if (hoursSinceLastFailure < 24) {
                penalty += 0.05
            } else if (hoursSinceLastFailure < 72) {
                penalty += 0.03
            }
        }
        
        // Cap total penalty at max daily deduction
        return min(penalty, MAX_DAILY_DEDUCTION)
    }
    
    /**
     * Calculate points for exercise activity
     * 超过30分钟就加0.1分
     */
    private fun calculateExercisePoints(): Double {
        return EXERCISE_POINTS
    }
    
    /**
     * Calculate points for sleep (can be positive or negative)
     * 输入睡眠得分（百分制），（睡眠得分-60）*0.01就是加分
     * 如果睡眠得分小于60就相当于扣分
     */
    private fun calculateSleepPoints(sleepScore: Int): Double {
        val points = (sleepScore - SLEEP_SCORE_THRESHOLD) * SLEEP_SCORE_MULTIPLIER
        
        // Cap at max daily addition/deduction
        return if (points > 0) {
            min(points, MAX_DAILY_ADDITION)
        } else {
            max(points, -MAX_DAILY_DEDUCTION)
        }
    }
    
    /**
     * Get the score level for a given score - 六个等级
     */
    fun getScoreLevel(score: Double): ScoreLevel {
        return when {
            score >= ScoreLevel.LEVEL_6.min -> ScoreLevel.LEVEL_6
            score >= ScoreLevel.LEVEL_5.min -> ScoreLevel.LEVEL_5
            score >= ScoreLevel.LEVEL_4.min -> ScoreLevel.LEVEL_4
            score >= ScoreLevel.LEVEL_3.min -> ScoreLevel.LEVEL_3
            score >= ScoreLevel.LEVEL_2.min -> ScoreLevel.LEVEL_2
            else -> ScoreLevel.LEVEL_1
        }
    }
}
