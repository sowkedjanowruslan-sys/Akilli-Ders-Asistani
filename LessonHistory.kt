package com.example.akillidersasistani


data class LessonHistory(
    val sessionId: String = "",
    val topic: String = "", // Örn: Newton Yasaları
    val date: Long = 0,     // Tarih (Milisaniye cinsinden)
    val totalStudents: Int = 0,
    val successRate: Int = 0, // % Başarı oranı
    val studentList: List<StudentSimpleModel> = listOf(),
    val projects: List<Map<String, Any>> = emptyList()

)

// Dersteki her bir öğrencinin o anki durumu
data class StudentSimpleModel(
    val name: String = "",
    val no: String = "",
    val department: String = "",
    val status: String = "" // "Anladı", "Anlamadı" veya "Tekrar"
)