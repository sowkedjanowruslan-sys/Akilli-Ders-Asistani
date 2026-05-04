package com.example.akillidersasistani

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.akillidersasistani.models.LessonModel
import com.google.firebase.database.FirebaseDatabase

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // Firebase'den verileri çek ve alarmları geri yükle
            val db = FirebaseDatabase.getInstance().getReference("user_schedule")
            db.get().addOnSuccessListener { snapshot ->
                for (data in snapshot.children) {
                    val lesson = data.getValue(LessonModel::class.java)
                    lesson?.let {
                        // Alarmları tekrar kur
                        AlarmUtils.scheduleLessonAlarm(context, it, true)
                        AlarmUtils.scheduleLessonAlarm(context, it, false)
                    }
                }
            }
        }
    }
}