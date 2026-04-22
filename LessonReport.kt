package com.example.akillidersasistani

data class LessonReport(
    val reportId: String = "",
    val lessonName: String = "",
    val date: Long = 0,
    val totalStudents: Int = 0,
    val understoodPercent: Int = 0,
    val repeatCount: Int = 0,
    val notUnderstoodCount: Int = 0,
    val averageSpeed: String = "normal",
    val transcript: String = "",
    val sessionCode: String = ""
)