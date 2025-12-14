package com.jieyin.addiction

import com.jieyin.addiction.algorithm.AddictionScoreCalculator
import com.jieyin.addiction.model.ActivityRecord
import com.jieyin.addiction.model.ActivityType
import com.jieyin.addiction.model.ScoreLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AddictionScoreCalculatorTest {
    
    private lateinit var calculator: AddictionScoreCalculator
    
    @Before
    fun setup() {
        calculator = AddictionScoreCalculator()
    }
    
    @Test
    fun testBaseScore() {
        val records = emptyList<ActivityRecord>()
        val score = calculator.calculateScore(records)
        assertEquals(60.0, score, 0.01)
    }
    
    @Test
    fun testSuccessAddsPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = 1000000)
        )
        val score = calculator.calculateScore(records)
        assertTrue("Success should add points", score > 60.0)
    }
    
    @Test
    fun testFailureReducesPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.FAILURE, timestamp = 1000000)
        )
        val score = calculator.calculateScore(records)
        assertTrue("Failure should reduce points", score < 60.0)
    }
    
    @Test
    fun testMultipleFailuresIncreasePenalty() {
        val baseTime = System.currentTimeMillis()
        val singleFailure = listOf(
            ActivityRecord(type = ActivityType.FAILURE, timestamp = baseTime)
        )
        val multipleFailures = listOf(
            ActivityRecord(type = ActivityType.FAILURE, timestamp = baseTime),
            ActivityRecord(type = ActivityType.FAILURE, timestamp = baseTime + 3600000) // 1 hour later
        )
        
        val scoreSingle = calculator.calculateScore(singleFailure)
        val scoreMultiple = calculator.calculateScore(multipleFailures)
        
        assertTrue("Multiple failures should have greater penalty", scoreMultiple < scoreSingle)
    }
    
    @Test
    fun testExerciseAddsPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.EXERCISE, duration = 30) // 30 minutes
        )
        val score = calculator.calculateScore(records)
        assertTrue("Exercise should add points", score > 60.0)
    }
    
    @Test
    fun testExerciseAdds0Point1() {
        val records = listOf(
            ActivityRecord(type = ActivityType.EXERCISE, duration = 30)
        )
        val score = calculator.calculateScore(records)
        assertEquals("Exercise should add 0.1 points", 60.1, score, 0.01)
    }
    
    @Test
    fun testSleepScoreAbove60AddsPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.SLEEP, duration = 80) // 睡眠得分80分
        )
        val score = calculator.calculateScore(records)
        // (80 - 60) * 0.01 = 0.2
        assertEquals("Sleep score 80 should add 0.2 points", 60.2, score, 0.01)
    }
    
    @Test
    fun testSleepScoreBelow60ReducesPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.SLEEP, duration = 40) // 睡眠得分40分
        )
        val score = calculator.calculateScore(records)
        // (40 - 60) * 0.01 = -0.2
        assertEquals("Sleep score 40 should reduce by 0.2 points", 59.8, score, 0.01)
    }
    
    @Test
    fun testScoreBoundaries() {
        // Test minimum boundary
        val manyFailures = (1..20).map { 
            ActivityRecord(type = ActivityType.FAILURE, timestamp = System.currentTimeMillis() + it * 1000)
        }
        val minScore = calculator.calculateScore(manyFailures)
        assertTrue("Score should not go below 0", minScore >= 0.0)
        
        // Test maximum boundary  
        val manySuccesses = mutableListOf<ActivityRecord>()
        var timestamp = 1000000L
        for (i in 1..20) {
            manySuccesses.add(ActivityRecord(type = ActivityType.SUCCESS, timestamp = timestamp))
            timestamp += 86400000L * 7 // 7 days apart
        }
        val maxScore = calculator.calculateScore(manySuccesses)
        assertTrue("Score should not exceed 100", maxScore <= 100.0)
    }
    
    @Test
    fun testScoreLevel_Level1() {
        val level = calculator.getScoreLevel(15.0)
        assertEquals(ScoreLevel.LEVEL_1, level)
    }
    
    @Test
    fun testScoreLevel_Level2() {
        val level = calculator.getScoreLevel(30.0)
        assertEquals(ScoreLevel.LEVEL_2, level)
    }
    
    @Test
    fun testScoreLevel_Level3() {
        val level = calculator.getScoreLevel(50.0)
        assertEquals(ScoreLevel.LEVEL_3, level)
    }
    
    @Test
    fun testScoreLevel_Level4() {
        val level = calculator.getScoreLevel(70.0)
        assertEquals(ScoreLevel.LEVEL_4, level)
    }
    
    @Test
    fun testScoreLevel_Level5() {
        val level = calculator.getScoreLevel(85.0)
        assertEquals(ScoreLevel.LEVEL_5, level)
    }
    
    @Test
    fun testScoreLevel_Level6() {
        val level = calculator.getScoreLevel(97.0)
        assertEquals(ScoreLevel.LEVEL_6, level)
    }
    
    @Test
    fun testSuccessIntervalScoring() {
        val baseTime = 1000000000L
        val oneHourLater = baseTime + 3600000L // 1 hour in milliseconds
        
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = baseTime),
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = oneHourLater)
        )
        
        val score = calculator.calculateScore(records)
        // Base (60) + first success (0.01) + second success with 1 hour interval (0.01 more)
        assertTrue("Score should reflect time interval between successes", score > 60.0)
    }
    
    @Test
    fun testSuccessPointsPerHour() {
        val baseTime = 1000000000L
        val twoHoursLater = baseTime + 2 * 3600000L // 2 hours in milliseconds
        
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = baseTime),
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = twoHoursLater)
        )
        
        val score = calculator.calculateScore(records)
        // Base (60) + first success (0.01) + second success with 2 hour interval (0.02)
        assertEquals("Score should add 0.01 per hour", 60.03, score, 0.01)
    }
    
    @Test
    fun testExerciseBlockedAfterFailure() {
        val baseTime = System.currentTimeMillis()
        val records = listOf(
            ActivityRecord(type = ActivityType.FAILURE, timestamp = baseTime),
            ActivityRecord(type = ActivityType.EXERCISE, duration = 30, timestamp = baseTime + 1000)
        )
        
        // Failure penalty is 0.1, exercise should not add points due to same-day failure
        val score = calculator.calculateScore(records)
        assertTrue("Exercise after failure should not add points", score < 60.0)
    }
    
    @Test
    fun testSleepDeductionIgnoresFailure() {
        val baseTime = System.currentTimeMillis()
        val records = listOf(
            ActivityRecord(type = ActivityType.FAILURE, timestamp = baseTime),
            ActivityRecord(type = ActivityType.SLEEP, duration = 40, timestamp = baseTime + 1000) // 扣分
        )
        
        // Failure penalty + sleep deduction should both apply
        val score = calculator.calculateScore(records)
        // 60 - 0.1 (failure) - 0.2 (sleep 40 score) = 59.7
        assertTrue("Sleep deduction should apply even after failure", score < 59.9)
    }
    
    @Test
    fun testMixedActivities() {
        val baseTime = System.currentTimeMillis() - 86400000L // 1 day ago
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = baseTime),
            ActivityRecord(type = ActivityType.EXERCISE, duration = 30, timestamp = baseTime + 7200000),
            ActivityRecord(type = ActivityType.SLEEP, duration = 80, timestamp = baseTime + 86400000)
        )
        
        val score = calculator.calculateScore(records)
        assertTrue("Mixed positive activities should increase score", score > 60.0)
    }
}
