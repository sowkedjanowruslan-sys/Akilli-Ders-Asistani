package com.example.akillidersasistani

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.akillidersasistani.models.LessonModel
import java.util.*

object AlarmUtils {

    private const val TAG = "AlarmEngine"

    // ID Çakışmasını önlemek için sabit offsetler
    private const val ID_OFFSET_START = 0
    private const val ID_OFFSET_REMINDER = 100000

    /**
     * Ders bildirimlerini planlar (Hem başlangıç hem 1 saat öncesi)
     */
    fun scheduleLessonAlarm(context: Context, lesson: LessonModel, isOneHourBefore: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Intent ve İçerik Hazırlama
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            if (isOneHourBefore) {
                putExtra("title", "Dersine 1 Saat Kaldı! ⏳")
                putExtra("message", "${lesson.dersAdi} birazdan başlıyor. Yer: ${lesson.sinif}")
            } else {
                putExtra("title", "Ders Başladı! 📖")
                putExtra("message", "${lesson.dersAdi} şu an başladı. İyi dersler!")
            }
            // Hangi ders olduğunu anlamak için ID'yi de ekleyelim
            putExtra("lesson_id", lesson.id)
        }

        // 2. Benzersiz ID Üretimi (Ders ID'sinin hash'i kullanılır)
        // 1 saat öncesi ile başlangıç vaktinin ID'leri farklı olmalı
        val baseId = lesson.id.hashCode()
        val requestId = if (isOneHourBefore) baseId + ID_OFFSET_REMINDER else baseId + ID_OFFSET_START

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Hassas Zaman Hesaplama
        val calendar = Calendar.getInstance(java.util.Locale("tr")).apply {
            timeInMillis = System.currentTimeMillis() // Önce şu anı al

            // Seçilen günü ayarla
            set(Calendar.DAY_OF_WEEK, getDayOfWeek(lesson.gun))

            // Saati ayarla
            val timeParts = lesson.baslangicSaati.split(":")
            if (timeParts.size == 2) {
                set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                set(Calendar.MINUTE, timeParts[1].toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Eğer 1 saat öncesi ise süreyi geri çek
            if (isOneHourBefore) {
                add(Calendar.HOUR_OF_DAY, -1)
            }

            // KRİTİK: Eğer hesaplanan zaman GEÇMİŞTE ise (Örn: Bugün Pazartesi 10:00 ve biz 09:00'a kurduk)
            // Alarmı 1 hafta sonraya (Gelecek haftaki aynı güne) kurar.
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 7)
            }
        }

        // 4. Alarmı Sisteme Kaydetme (Android Sürüm Kontrolleriyle)
        try {
            when {
                // Android 12 (API 31) ve sonrası: canScheduleExactAlarms kontrolü şart
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    } else {
                        // İzin yoksa normal alarm kur (Birkaç dk sapabilir ama uygulama çökmez)
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                }
                // Android 6.0 ve 11 arası
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
                // Çok eski sürümler
                else -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
            Log.d(TAG, "Alarm Başarıyla Kuruldu: ${lesson.dersAdi} | Tip: ${if(isOneHourBefore) "1 Saat Önce" else "Başlangıç"} | Vakit: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Güvenlik Hatası: Exact Alarm yetkisi eksik. Normal alarmla devam ediliyor.")
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    /**
     * Gün metnini takvim sabitlerine çevirir
     */
    private fun getDayOfWeek(gun: String): Int {
        return when (gun.lowercase(java.util.Locale("tr")).trim()) {
            "pazartesi" -> Calendar.MONDAY
            "salı" -> Calendar.TUESDAY
            "çarşamba" -> Calendar.WEDNESDAY
            "perşembe" -> Calendar.THURSDAY
            "cuma" -> Calendar.FRIDAY
            "cumartesi" -> Calendar.SATURDAY
            "pazar" -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }

     fun cancelLessonAlarm(context: Context, lessonId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        val baseId = lessonId.hashCode()
        // Her iki hatırlatıcı tipini de temizle
        val requestIds = listOf(baseId + ID_OFFSET_START, baseId + ID_OFFSET_REMINDER)

        for (id in requestIds) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        Log.d(TAG, "Ders Alarmları İptal Edildi (ID: $lessonId)")
    }
}