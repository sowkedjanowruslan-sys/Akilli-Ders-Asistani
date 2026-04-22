package com.example.akillidersasistani

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ProjectManager(private val activity: AppCompatActivity, private val db: DatabaseReference) {

    private var selectedDeadline: Long = 0
    private val PREF_NAME = "ProjectSettings"
    private val KEY_IS_FIRST_TIME = "isFirstTimeProject"

    // --- ANA FONKSİYON: TeacherActivity'den bu çağırılır ---
    fun checkAndShowProjectDialog(sessionCode: String, currentTopic: String) {
        val sharedPref = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isFirstTime = sharedPref.getBoolean(KEY_IS_FIRST_TIME, true)

        if (isFirstTime) {
            // İlk defa basılıyorsa tanıtım ekranını aç
            showIntroDialog {
                sharedPref.edit().putBoolean(KEY_IS_FIRST_TIME, false).apply()
                showCreateProjectDialog(sessionCode, currentTopic)
            }
        } else {
            // Zaten biliyorsa direkt oluşturma ekranını aç
            showCreateProjectDialog(sessionCode, currentTopic)
        }
    }

    // --- TANITIM EKRANI ---
    private fun showIntroDialog(onStart: () -> Unit) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_project_intro, null)
        val dialog = MaterialAlertDialogBuilder(activity).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnIntroStart).setOnClickListener {
            dialog.dismiss()
            onStart()
        }
        dialog.show()
    }

    // --- PROJE OLUŞTURMA EKRANI ---
    private fun showCreateProjectDialog(sessionCode: String, currentTopic: String) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_create_project, null)

        val edtTitle = dialogView.findViewById<TextInputEditText>(R.id.edtProjectTitle)
        val edtDesc = dialogView.findViewById<TextInputEditText>(R.id.edtProjectDesc)
        val btnAi = dialogView.findViewById<MaterialButton>(R.id.btnAiGenerateProject)
        val btnPublish = dialogView.findViewById<MaterialButton>(R.id.btnPublishProject)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelProject)
        val txtDate = dialogView.findViewById<TextView>(R.id.txtDeadline)
        val progress = dialogView.findViewById<View>(R.id.progressProjectLoading)

        edtTitle.setText(currentTopic)

        val dialog = MaterialAlertDialogBuilder(activity).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        txtDate.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(activity, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, 23, 59)
                selectedDeadline = cal.timeInMillis
                txtDate.text = "📅 Son Teslim: $d/${m + 1}/$y"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        // --- GELİŞMİŞ AI MANTIĞI ---
        btnAi.setOnClickListener {
            val title = edtTitle.text.toString().trim()
            val currentText = edtDesc.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(activity, "Lütfen önce proje başlığı girin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            btnAi.isEnabled = false

            // Prompt belirleme (Boşsa oluştur, doluysa güzelleştir)
            val finalPrompt = if (currentText.isEmpty()) {
                "$title konusu için üniversite düzeyinde kapsamlı bir proje yönergesi hazırla. Amaç, yöntem ve 100 üzerinden değerlendirme kriterleri (Rubrik) içersin."
            } else {
                "Aşağıdaki proje taslağını bir hoca olarak yazdım. Lütfen bu metni analiz et, akademik dili profesyonelleştir ve varsa eksik kısımları (kriterler, süre vb.) detaylandırarak metni geliştir:\n\n$currentText"
            }

            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val model = GenerativeModel("gemini-2.5-flash", "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao")
                    val response = model.generateContent(finalPrompt)
                    withContext(Dispatchers.Main) {
                        edtDesc.setText(response.text)
                        progress.visibility = View.GONE
                        btnAi.isEnabled = true
                        Toast.makeText(activity, if (currentText.isEmpty()) "Taslak oluşturuldu ✨" else "Taslak iyileştirildi ✨", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        btnAi.isEnabled = true
                        Toast.makeText(activity, "AI Hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnPublish.setOnClickListener {
            val title = edtTitle.text.toString().trim()
            val desc = edtDesc.text.toString().trim()

            if (title.isNotEmpty() && selectedDeadline > 0) {
                val projectData = mapOf(
                    "title" to title,
                    "description" to desc,
                    "deadline" to selectedDeadline,
                    "timestamp" to System.currentTimeMillis()
                )
                db.child("sessions").child(sessionCode).child("projects").push().setValue(projectData)
                    .addOnSuccessListener {
                        Toast.makeText(activity, "Proje yayınlandı! 🚀", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
            } else {
                Toast.makeText(activity, "Başlık ve tarih eksik!", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}