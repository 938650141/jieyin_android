package com.jieyin.addiction.algorithm

import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import java.util.Calendar

/**
 * Core algorithm for calculating addiction score
 * 
 * Rules:
 * - Base score: 60
 * - Success: Add points based on time interval from last success/failure (hours * 0.01)
 * - Failure: Deduct points based on failure frequency and recency (exponential growth)
 *   - When recording failure, exercise/sleep scores from the same day are offset (added to deduction)
 *   - Failure records from the past 30 days affect current deduction
 *   - Maximum deduction: 3 points
 * - Exercise: Add 0.1 points if exercised over 30 minutes (0 points if failure on same day)
 * - Sleep: Add/deduct points based on sleep score ((score - 60) * 0.01), deduction ignores failure
 *   - If there's a failure on same day, sleep addition is 0
 */
class AddictionScoreCalculator {
    
    companion object {
        private const val BASE_SCORE = 60.0
        private const val MIN_SCORE = 0.0
        private const val MAX_SCORE = 100.0
        
        // Scoring unit and daily limits
        private const val MAX_DAILY_ADDITION = 0.5  // Maximum points added per day
        private const val MAX_DAILY_DEDUCTION = 0.5  // Maximum points deducted per day (for sleep)
        private const val MAX_FAILURE_DEDUCTION = 3.0  // Maximum points deducted per failure (adjusted from 0.5)
        
        // Time constants
        private const val MILLIS_PER_HOUR = 1000L * 60 * 60
        
        // Time interval scoring (for Success) - 按小时 * 0.01
        private const val SUCCESS_POINTS_PER_HOUR = 0.01
        
        // Failure scoring - exponential growth
        private const val FAILURE_BASE_PENALTY = 0.10  // Base penalty 0.10
        private const val FAILURE_RECENT_WINDOW_DAYS = 30  // Recent window in days (one month)
        
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
        // Track exercise and sleep records with their scores for offset calculation
        val exerciseAndSleepRecords = mutableListOf<ActivityRecord>()
        
        for (record in sortedRecords) {
            when (record.type) {
                ActivityType.SUCCESS -> {
                    val points = calculateSuccessPoints(record.timestamp, lastSuccessOrFailureTime)
                    score += points
                    lastSuccessOrFailureTime = record.timestamp
                }
                
                ActivityType.FAILURE -> {
                    allFailures.add(record.timestamp)
                    // Calculate exercise/sleep offset from same day
                    val sameDayOffset = calculateSameDayExerciseSleepOffset(record.timestamp, exerciseAndSleepRecords)
                    val penalty = calculateFailurePenalty(record.timestamp, allFailures, sameDayOffset)
                    score -= penalty
                    lastSuccessOrFailureTime = record.timestamp
                }
                
                ActivityType.EXERCISE -> {
                    // 运动可以记录，但当天有失败则加分为0
                    val exercisePoints = if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                        calculateExercisePoints()
                    } else {
                        0.0
                    }
                    score += exercisePoints
                    // Track the exercise record (store actual potential points, not the applied 0)
                    exerciseAndSleepRecords.add(record)
                }
                
                ActivityType.SLEEP -> {
                    val sleepPoints = calculateSleepPoints(record.duration)
                    // 睡眠如果是扣分则无视当天有无失败
                    if (sleepPoints < 0) {
                        score += sleepPoints
                    } else if (!hasFailureOnSameDay(record.timestamp, allFailures)) {
                        // 加分时需要当天没有失败（可以记录但加分为0）
                        score += sleepPoints
                    }
                    // Track the sleep record
                    exerciseAndSleepRecords.add(record)
                }
            }
        }
        
        // Clamp score to valid range
        return max(MIN_SCORE, min(MAX_SCORE, score))
    }
    
    /**
     * Calculate the offset for failure deduction from same-day exercise and sleep records
     * This is used to offset any gains from exercise/sleep on the same day as a failure
     */
    private fun calculateSameDayExerciseSleepOffset(failureTime: Long, exerciseAndSleepRecords: List<ActivityRecord>): Double {
        var offset = 0.0
        
        for (record in exerciseAndSleepRecords) {
            if (isSameDay(failureTime, record.timestamp)) {
                when (record.type) {
                    ActivityType.EXERCISE -> {
                        offset += EXERCISE_POINTS
                    }
                    ActivityType.SLEEP -> {
                        val sleepPoints = calculateSleepPoints(record.duration)
                        // Only offset positive sleep points (gains)
                        if (sleepPoints > 0) {
                            offset += sleepPoints
                        }
                    }
                    else -> {}
                }
            }
        }
        
        return offset
    }
    
    /**
     * Check if two timestamps are on the same day
     */
    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    /**
     * Calculate score change for a specific record
     */
    fun calculateScoreChange(record: ActivityRecord, allRecords: List<ActivityRecord>): Double {
        val previousRecords = allRecords.filter { it.timestamp < record.timestamp }.sortedBy { it.timestamp }
        val allFailures = previousRecords.filter { it.type == ActivityType.FAILURE }.map { it.timestamp }.toMutableList()
        val exerciseAndSleepRecords = previousRecords.filter { 
            it.type == ActivityType.EXERCISE || it.type == ActivityType.SLEEP 
        }
        
        return when (record.type) {
            ActivityType.SUCCESS -> {
                val lastSuccessOrFailure = previousRecords
                    .filter { it.type == ActivityType.SUCCESS || it.type == ActivityType.FAILURE }
                    .maxByOrNull { it.timestamp }
                calculateSuccessPoints(record.timestamp, lastSuccessOrFailure?.timestamp)
            }
            
            ActivityType.FAILURE -> {
                allFailures.add(record.timestamp)
                // Calculate exercise/sleep offset from same day
                val sameDayOffset = calculateSameDayExerciseSleepOffset(record.timestamp, exerciseAndSleepRecords)
                -calculateFailurePenalty(record.timestamp, allFailures, sameDayOffset)
            }
            
            ActivityType.EXERCISE -> {
                // 运动可以记录，但当天有失败则加分为0
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
                    // 当天有失败，可以记录但加分为0
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
        val intervalHours = (intervalMillis / MILLIS_PER_HOUR)  // 不满一小时部分不计入
        
        // Award points based on hours of abstinence
        val points = min(intervalHours * SUCCESS_POINTS_PER_HOUR, MAX_DAILY_ADDITION)
        return points
    }
    
    /**
     * Calculate penalty for a failure event
     * Uses exponential growth based on:
     * - Time since last failure (shorter interval = higher penalty)
     * - Number of failures in the past 30 days (more failures = higher penalty)
     * Also includes same-day exercise/sleep offset to cancel out those gains
     */
    private fun calculateFailurePenalty(currentTime: Long, allFailures: List<Long>, sameDayOffset: Double = 0.0): Double {
        var penalty = FAILURE_BASE_PENALTY
        
        // 最近30天内的失败次数
        val windowMillis = FAILURE_RECENT_WINDOW_DAYS * 24 * MILLIS_PER_HOUR
        val recentFailures = allFailures.filter { currentTime - it <= windowMillis }
        val failureCount = recentFailures.size
        
        // Exponential penalty based on failure count in 30-day window
        // Formula: base * (1.5 ^ (count - 1)) for each additional failure
        if (failureCount > 1) {
            val exponentialFactor = Math.pow(1.5, (failureCount - 1).toDouble())
            penalty = FAILURE_BASE_PENALTY * exponentialFactor
        }
        
        // 上次失败距离越近扣分越多 (exponential based on time proximity)
        val otherFailures = allFailures.filter { it < currentTime }
        if (otherFailures.isNotEmpty()) {
            val lastFailure = otherFailures.max()
            val hoursSinceLastFailure = (currentTime - lastFailure).toDouble() / MILLIS_PER_HOUR
            
            // Exponential time-based penalty: the closer the last failure, the higher the multiplier
            // Uses decay function: penalty multiplier = e^(-hours/24) gives higher multiplier for recent failures
            if (hoursSinceLastFailure < 24 * 7) { // Within 7 days
                val timeMultiplier = 1.0 + exp(-hoursSinceLastFailure / 24.0) // Ranges from ~2 (immediate) to ~1 (7 days)
                penalty *= timeMultiplier
            }
        }
        
        // Add same-day exercise/sleep offset to cancel out those gains
        penalty += sameDayOffset
        
        // Cap total penalty at max failure deduction (3 points)
        return min(penalty, MAX_FAILURE_DEDUCTION)
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
