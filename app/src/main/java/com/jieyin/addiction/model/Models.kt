package com.jieyin.addiction.model

import java.util.Date

/**
 * Activity types for tracking
 */
enum class ActivityType {
    SUCCESS,    // 成功
    FAILURE,    // 失败
    EXERCISE,   // 运动
    SLEEP       // 睡眠
}

/**
 * Activity record
 */
data class ActivityRecord(
    val id: Long = System.currentTimeMillis(),
    val type: ActivityType,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Int = 0,  // Duration in minutes (for Exercise) or sleep score (for Sleep)
    val scoreChange: Double = 0.0  // 记录加分或扣分
)

/**
 * Score level ranges - 六个等级
 */
enum class ScoreLevel(val min: Double, val max: Double, val description: String) {
    LEVEL_1(0.0, 19.99, "严重成瘾"),
    LEVEL_2(20.0, 39.99, "重度成瘾"),
    LEVEL_3(40.0, 59.99, "中度成瘾"),
    LEVEL_4(60.0, 79.99, "轻度成瘾"),
    LEVEL_5(80.0, 94.99, "即将戒除"),
    LEVEL_6(95.0, 100.0, "已戒除")
}
