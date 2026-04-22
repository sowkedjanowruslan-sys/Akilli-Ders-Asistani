package com.example.akillidersasistani

/**
 * Uygulama genelinde kullanılan tüm sabitleri (Firebase düğümleri,
 * SharedPreferences anahtarları ve Durum kodları) burada tutuyoruz.
 * Bu sayede yazım hatalarının (Typos) önüne geçiyoruz.
 */
object StudentEvents {

    // --- 1. FIREBASE DÜĞÜM İSİMLERİ (Database Nodes) ---
    const val DB_SESSIONS = "sessions"
    const val DB_USERS = "users"
    const val DB_FEEDBACK_LIST = "feedback_list"
    const val DB_DETAILED_FEEDBACK = "detailed_feedback"
    const val DB_ACTIVE = "active"
    const val DB_STATUS = "status"
    const val DB_IS_ANONYMOUS = "isAnonymous"
    const val DB_TEACHER_NAME = "teacher_name"
    const val DB_SUBTITLES = "subtitles" // Canlı altyazı verisi için
    const val DB_IS_SUBTITLES_ACTIVE = "isSubtitlesActive" // Altyazı switch kontrolü

    // --- 2. SAYAÇLAR VE ZAMANLAMA (İstatistik & Raporlama) ---
    const val KEY_UNDERSTOOD_COUNT = "understoodCount"
    const val KEY_REPEAT_COUNT = "repeatCount"
    const val KEY_NOT_UNDERSTOOD_COUNT = "notUnderstoodCount"
    const val KEY_LAST_TIMESTAMP = "lastTimestamp"

    // --- 3. DİL KODLARI ---
    const val LANG_TR = "tr"
    const val LANG_EN = "en"
    const val LANG_DE = "de"
    const val LANG_RU = "ru"

    // --- 4. GERİ BİLDİRİM TÜRLERİ (Feedback Types) ---
    const val TYPE_UNDERSTOOD = "understood"
    const val TYPE_REPEAT = "repeat"
    const val TYPE_NOT_UNDERSTOOD = "not_understood"
    const val TYPE_HAND_RAISE = "hand_raise" // Söz isteme
    const val TYPE_SLOW = "slow"             // Yavaşla uyarısı
    const val TYPE_FAST = "fast"             // Hızlan uyarısı
    const val TYPE_EMERGENCY = "emergency"   // Acil durum

    // --- 5. MESAJLAŞMA VE ÜYE DURUMLARI (Status) ---
    const val STATUS_WAITING = "waiting"   // Hoca henüz bakmadı
    const val STATUS_ANSWERED = "answered" // Hoca "Tamam" dedi (Okundu)
    const val STATUS_ONLINE = "online"     // Öğrenci derste aktif
    const val STATUS_OFFLINE = "offline"   // Öğrenci dersten çıktı

    // --- 6. SHARED PREFERENCES ANAHTARLARI (Hafıza) ---
    const val PREFS_SETTINGS = "Settings"
    const val PREFS_USER_PROFILE = "UserProfile"
    const val PREFS_TEACHER_PROFILE = "TeacherProfile"

    // Hafıza altındaki özel anahtarlar (Keys)
    const val KEY_LANG = "app_lang"
    const val KEY_VOICE = "voice_enabled"
    const val KEY_STUDENT_NO = "ogrenci_no"
    const val KEY_AD_SOYAD = "ad_soyad"
    const val KEY_GENDER = "cinsiyet"
    const val KEY_PHOTO_STUDENT = "student_photo"
    const val KEY_PHOTO_TEACHER = "teacher_photo"
    const val KEY_LAST_CODE = "last_session_code"
    const val KEY_UNI_NAME = "university_name" // Rapor için okul adı
    const val KEY_CLASS_NAME = "classroom_name" // Rapor için sınıf adı
}