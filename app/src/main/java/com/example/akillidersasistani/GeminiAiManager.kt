package com.example.akillidersasistani

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

class GeminiAiManager {




    private val apiKey = "API-KEY"

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f // Yaratıcılık seviyesi
        }
    )

    /**
     * Dersi Analiz Eden Dev Prompt
     */
    suspend fun analyzeLesson(rawText: String): LessonAnalysisResponse {
        val prompt = """
            Sen profesyonel bir üniversite hocası ve eğitim asistanısın. 
            Aşağıdaki dökümünü analiz et ve tam olarak şu bölümleri oluştur:
            
            1. ÖZET: Dersi 5 ana maddede çok net bir şekilde özetle.
            2. TAHMİNLER: 'Bu dersin sınavında hoca şuradan kesin soru sorar' dediğin 3 kritik noktayı belirt.
            3. NEGATİF BİLGİ: Bu konuda nelerin o tanıma girmediğini, nelerin karıştırılmaması gerektiğini 'HANGİSİ DEĞİLDİR' mantığıyla açıkla.
            4. TABLO: Dersteki terimleri ve anlamlarını içeren bir liste hazırla.
            
            İçeriği ayırırken aralarına '---' işareti koy ki ben onları ayırabileyim.
            
            DERS METNİ:
            $rawText
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val parts = response.text?.split("---") ?: listOf("", "", "", "")

            LessonAnalysisResponse(
                summary = parts.getOrNull(0) ?: "Özet bulunamadı.",
                predictions = parts.getOrNull(1) ?: "Tahmin yapılamadı.",
                nonExamples = parts.getOrNull(2) ?: "Bilgi bulunamadı."
            )
        } catch (e: Exception) {
            LessonAnalysisResponse("Hata: ${e.message}", "", "")
        }
    }

    data class LessonAnalysisResponse(
        val summary: String,
        val predictions: String,
        val nonExamples: String
    )
}
