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
    fun testReadingAddsPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.READING, duration = 60) // 60 minutes
        )
        val score = calculator.calculateScore(records)
        assertTrue("Reading should add points", score > 60.0)
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
    fun testOptimalSleepAddsPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.SLEEP, duration = 480) // 8 hours = 480 minutes
        )
        val score = calculator.calculateScore(records)
        assertTrue("Optimal sleep should add points", score > 60.0)
    }
    
    @Test
    fun testInsufficientSleepReducesPoints() {
        val records = listOf(
            ActivityRecord(type = ActivityType.SLEEP, duration = 300) // 5 hours = 300 minutes
        )
        val score = calculator.calculateScore(records)
        assertTrue("Insufficient sleep should reduce points", score < 60.0)
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
    fun testScoreLevel_Critical() {
        val level = calculator.getScoreLevel(45.0)
        assertEquals(ScoreLevel.CRITICAL, level)
    }
    
    @Test
    fun testScoreLevel_Warning() {
        val level = calculator.getScoreLevel(70.0)
        assertEquals(ScoreLevel.WARNING, level)
    }
    
    @Test
    fun testScoreLevel_Good() {
        val level = calculator.getScoreLevel(85.0)
        assertEquals(ScoreLevel.GOOD, level)
    }
    
    @Test
    fun testScoreLevel_Excellent() {
        val level = calculator.getScoreLevel(97.0)
        assertEquals(ScoreLevel.EXCELLENT, level)
    }
    
    @Test
    fun testSuccessIntervalScoring() {
        val baseTime = 1000000000L
        val oneDayLater = baseTime + 86400000L // 1 day in milliseconds
        
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = baseTime),
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = oneDayLater)
        )
        
        val score = calculator.calculateScore(records)
        // Base (60) + first success (~2) + second success with 1 day interval (~2 more)
        assertTrue("Score should reflect time interval between successes", score > 62.0)
    }
    
    @Test
    fun testMixedActivities() {
        val baseTime = System.currentTimeMillis()
        val records = listOf(
            ActivityRecord(type = ActivityType.SUCCESS, timestamp = baseTime),
            ActivityRecord(type = ActivityType.READING, duration = 30, timestamp = baseTime + 3600000),
            ActivityRecord(type = ActivityType.EXERCISE, duration = 45, timestamp = baseTime + 7200000),
            ActivityRecord(type = ActivityType.SLEEP, duration = 480, timestamp = baseTime + 86400000)
        )
        
        val score = calculator.calculateScore(records)
        assertTrue("Mixed positive activities should increase score significantly", score > 65.0)
    }
}
