package com.example.akillidersasistani

data class FeedbackModel(
    var userId: String = "",    // val değil var olmalı
    var safeId: String = "",
    var type: String = "",      // val değil var olmalı
    val name: String = "",
    val no: String = "",
    val gender: String = "erkek",
    val department: String = "", // Branş/Bölüm bilgisi
    val photo: String? = null,
    val isAnonymous: Boolean = false,
    val understoodCount: Int = 0,
    val repeatCount: Int = 0,
    val notUnderstoodCount: Int = 0,
    val lastTimestamp: Long = 0,
    val speedFeedback: String = "normal",
    val isSubtitlesOpen: Boolean = false,
    val status: String = "LIVE"
)