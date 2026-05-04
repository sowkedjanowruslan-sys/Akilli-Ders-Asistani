package com.example.akillidersasistani.models
import java.io.Serializable

data class LessonModel(
    val id: String = "",
    val dersAdi: String = "",
    val egitmen: String = "",
    val sinif: String = "",
    val gun: String = "",
    val baslangicSaati: String = "",
    val bitisSaati: String = "",
    val timestamp: Long = 0
) : Serializable