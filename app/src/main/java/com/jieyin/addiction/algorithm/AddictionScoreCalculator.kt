package com.jieyin.addiction.algorithm

import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import kotlin.math.max
import kotlin.math.min

/**
 * Core algorithm for calculating addiction score
 * 
 * Rules:
 * - Base score: 60
 * - Success: Add points based on time interval from last success/failure
 * - Failure: Deduct points based on failure count and recent failure density
 * - Reading: Add points based on duration
 * - Exercise: Add points based on duration
 * - Sleep: Add/deduct points based on sleep duration
 */
class AddictionScoreCalculator {
    
    companion object {
        private const val BASE_SCORE = 60.0
        private const val MIN_SCORE = 0.0
        private const val MAX_SCORE = 100.0
        
        // Scoring unit and daily limits
        private const val SCORE_UNIT = 0.01  // Design constraint: All scoring in multiples of 0.01 (not enforced programmatically)
        private const val MAX_DAILY_ADDITION = 0.5  // Maximum points added per day
        private const val MAX_DAILY_DEDUCTION = 0.5  // Maximum points deducted per day
        
        // Time interval scoring (for Success)
        private const val HOURS_PER_DAY = 24.0
        private const val SUCCESS_POINTS_PER_DAY = 0.05  // 0.05 points per day of abstinence
        private const val SUCCESS_MAX_POINTS = 0.5  // Maximum 0.5 points per success
        
        // Failure scoring
        private const val FAILURE_BASE_PENALTY = 0.10  // Base penalty 0.10
        private const val FAILURE_RECENT_WINDOW_DAYS = 7  // Recent window in days
        private const val FAILURE_DENSITY_PENALTY = 0.05  // Additional 0.05 per recent failure
        private const val FAILURE_CUMULATIVE_PENALTY = 0.01  // Additional 0.01 per total failure
        
        // Activity duration scoring (minutes)
        private const val READING_POINTS_PER_30MIN = 0.05  // 0.05 per 30 minutes
        private const val EXERCISE_POINTS_PER_30MIN = 0.06  // 0.06 per 30 minutes
        
        // Sleep scoring (hours)
        private const val OPTIMAL_SLEEP_MIN = 7.0
        private const val OPTIMAL_SLEEP_MAX = 9.0
        private const val SLEEP_POINTS_OPTIMAL = 0.05  // 0.05 for optimal sleep
        private const val SLEEP_PENALTY_PER_HOUR_UNDER = 0.02  // 0.02 penalty per hour under
        private const val SLEEP_PENALTY_PER_HOUR_OVER = 0.01  // 0.01 penalty per hour over
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
        var failureCount = 0
        val recentFailures = mutableListOf<Long>()
        
        for (record in sortedRecords) {
            when (record.type) {
                ActivityType.SUCCESS -> {
                    score += calculateSuccessPoints(record.timestamp, lastSuccessOrFailureTime)
                    lastSuccessOrFailureTime = record.timestamp
                    // Reset failure tracking on success to provide psychological fresh start
                    // This encourages positive behavior and prevents past failures from
                    // permanently weighing down the score
                    failureCount = 0
                    recentFailures.clear()
                }
                
                ActivityType.FAILURE -> {
                    failureCount++
                    recentFailures.add(record.timestamp)
                    // Clean old failures outside recent window
                    cleanOldFailures(recentFailures, record.timestamp)
                    score -= calculateFailurePenalty(failureCount, recentFailures.size)
                    lastSuccessOrFailureTime = record.timestamp
                }
                
                ActivityType.READING -> {
                    score += calculateReadingPoints(record.duration)
                }
                
                ActivityType.EXERCISE -> {
                    score += calculateExercisePoints(record.duration)
                }
                
                ActivityType.SLEEP -> {
                    score += calculateSleepPoints(record.duration)
                }
            }
        }
        
        // Clamp score to valid range
        return max(MIN_SCORE, min(MAX_SCORE, score))
    }
    
    /**
     * Calculate points for a success event based on time since last success/failure
     */
    private fun calculateSuccessPoints(currentTime: Long, lastTime: Long?): Double {
        if (lastTime == null) {
            return SUCCESS_POINTS_PER_DAY  // First success gets base points
        }
        
        val intervalMillis = currentTime - lastTime
        val intervalDays = intervalMillis / (1000.0 * 60 * 60 * HOURS_PER_DAY)
        
        // Award points based on days of abstinence, capped at max daily addition
        val points = min(intervalDays * SUCCESS_POINTS_PER_DAY, SUCCESS_MAX_POINTS)
        return points
    }
    
    /**
     * Calculate penalty for a failure event
     */
    private fun calculateFailurePenalty(totalFailures: Int, recentFailures: Int): Double {
        // Base penalty increases with total failure count (multiples of 0.01)
        val basePenalty = FAILURE_BASE_PENALTY + (totalFailures * FAILURE_CUMULATIVE_PENALTY)
        
        // Additional penalty for recent failure density
        val densityPenalty = if (recentFailures > 1) {
            (recentFailures - 1) * FAILURE_DENSITY_PENALTY
        } else {
            0.0
        }
        
        // Cap total penalty at max daily deduction
        return min(basePenalty + densityPenalty, MAX_DAILY_DEDUCTION)
    }
    
    /**
     * Remove failures outside the recent window
     */
    private fun cleanOldFailures(failures: MutableList<Long>, currentTime: Long) {
        val windowMillis = FAILURE_RECENT_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        failures.removeAll { currentTime - it > windowMillis }
    }
    
    /**
     * Calculate points for reading activity
     */
    private fun calculateReadingPoints(durationMinutes: Int): Double {
        val periods = durationMinutes / 30.0
        val points = periods * READING_POINTS_PER_30MIN
        // Cap at max daily addition
        return min(points, MAX_DAILY_ADDITION)
    }
    
    /**
     * Calculate points for exercise activity
     */
    private fun calculateExercisePoints(durationMinutes: Int): Double {
        val periods = durationMinutes / 30.0
        val points = periods * EXERCISE_POINTS_PER_30MIN
        // Cap at max daily addition
        return min(points, MAX_DAILY_ADDITION)
    }
    
    /**
     * Calculate points for sleep (can be positive or negative)
     */
    private fun calculateSleepPoints(durationMinutes: Int): Double {
        val hours = durationMinutes / 60.0
        
        val points = when {
            hours in OPTIMAL_SLEEP_MIN..OPTIMAL_SLEEP_MAX -> {
                // Optimal sleep range
                SLEEP_POINTS_OPTIMAL
            }
            hours < OPTIMAL_SLEEP_MIN -> {
                // Too little sleep - penalty
                val hoursOff = OPTIMAL_SLEEP_MIN - hours
                -(hoursOff * SLEEP_PENALTY_PER_HOUR_UNDER)
            }
            else -> {
                // Too much sleep - smaller penalty
                val hoursOff = hours - OPTIMAL_SLEEP_MAX
                -(hoursOff * SLEEP_PENALTY_PER_HOUR_OVER)
            }
        }
        
        // Cap at max daily addition/deduction
        return if (points > 0) {
            min(points, MAX_DAILY_ADDITION)
        } else {
            max(points, -MAX_DAILY_DEDUCTION)
        }
    }
    
    /**
     * Get the score level for a given score
     */
    fun getScoreLevel(score: Double): ScoreLevel {
        return when {
            score >= ScoreLevel.EXCELLENT.min -> ScoreLevel.EXCELLENT
            score >= ScoreLevel.GOOD.min -> ScoreLevel.GOOD
            score >= ScoreLevel.WARNING.min -> ScoreLevel.WARNING
            else -> ScoreLevel.CRITICAL
        }
    }
}
