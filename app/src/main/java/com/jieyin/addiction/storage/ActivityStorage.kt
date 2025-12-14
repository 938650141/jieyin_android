package com.jieyin.addiction.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jieyin.addiction.model.ActivityRecord

/**
 * Storage manager for activity records using SharedPreferences
 */
class ActivityStorage(context: Context) {
    
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "jieyin_records"
        private const val KEY_RECORDS = "activity_records"
    }
    
    /**
     * Save a new activity record
     */
    fun saveRecord(record: ActivityRecord) {
        val records = getAllRecords().toMutableList()
        records.add(record)
        saveAllRecords(records)
    }
    
    /**
     * Get all activity records
     */
    fun getAllRecords(): List<ActivityRecord> {
        val json = sharedPreferences.getString(KEY_RECORDS, null) ?: return emptyList()
        val type = object : TypeToken<List<ActivityRecord>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    /**
     * Save all records (overwrite)
     */
    fun saveAllRecords(records: List<ActivityRecord>) {
        val json = gson.toJson(records)
        sharedPreferences.edit().putString(KEY_RECORDS, json).apply()
    }
    
    /**
     * Clear all records
     */
    fun clearAllRecords() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * Delete a specific record
     */
    fun deleteRecord(recordId: Long) {
        val records = getAllRecords().toMutableList()
        records.removeAll { it.id == recordId }
        saveAllRecords(records)
    }
    
    /**
     * Update a specific record
     */
    fun updateRecord(recordId: Long, updatedRecord: ActivityRecord) {
        val records = getAllRecords().toMutableList()
        val index = records.indexOfFirst { it.id == recordId }
        if (index != -1) {
            records[index] = updatedRecord.copy(id = recordId)
            saveAllRecords(records)
        }
    }
    
    /**
     * Check if record can be modified (within 7 days)
     */
    fun canModifyRecord(recordId: Long): Boolean {
        val record = getAllRecords().find { it.id == recordId } ?: return false
        val oneWeekMillis = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - record.timestamp <= oneWeekMillis
    }
}
