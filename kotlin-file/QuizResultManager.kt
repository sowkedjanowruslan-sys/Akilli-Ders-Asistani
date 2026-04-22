package com.example.akillidersasistani

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.*
import java.io.File
import java.util.*

class QuizResultManager(private val activity: AppCompatActivity, private val db: DatabaseReference) {

    private val quizResultQueue: Queue<DataSnapshot> = LinkedList()
    private var isQuizDialogShowing = false
    private var quizListener: ChildEventListener? = null
    private var currentSessionCode: String? = null

    // Sınav sonuçlarını dinlemeye başla
    fun startListening(sessionCode: String) {
        this.currentSessionCode = sessionCode

        // Eğer zaten bir dinleyici varsa önce onu temizle
        stopListening()

        quizListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (activity.isFinishing || activity.isDestroyed) return

                // Kuyruğa ekle ve işletmeyi başlat
                quizResultQueue.add(snapshot)
                processNextResult()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("sessions").child(sessionCode).child("quiz_results")
            .addChildEventListener(quizListener!!)
    }

    // Dinleyiciyi durdur (Ders bittiğinde çağırılmalı)
    fun stopListening() {
        currentSessionCode?.let { code ->
            quizListener?.let { listener ->
                db.child("sessions").child(code).child("quiz_results").removeEventListener(listener)
            }
        }
        quizResultQueue.clear()
        isQuizDialogShowing = false
    }

    private fun processNextResult() {
        if (isQuizDialogShowing || quizResultQueue.isEmpty()) return

        val nextSnapshot = quizResultQueue.poll() ?: return
        showResultDialog(nextSnapshot)
    }

    private fun showResultDialog(snapshot: DataSnapshot) {
        isQuizDialogShowing = true

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_student_quiz_detail, null)

        // UI Bileşenlerini Bağla
        val imgStudent = dialogView.findViewById<ShapeableImageView>(R.id.imgDetailStudent)
        val txtName = dialogView.findViewById<TextView>(R.id.txtDetailName)
        val txtScore = dialogView.findViewById<TextView>(R.id.txtDetailScore)
        val txtAnswers = dialogView.findViewById<TextView>(R.id.txtDetailAnswers)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDetail)

        // Verileri Firebase'den Çek
        val name = snapshot.child("studentName").value?.toString() ?: "Bilinmeyen Öğrenci"
        val score = snapshot.child("score").value?.toString() ?: "0"
        val no = snapshot.child("studentNo").value?.toString() ?: "---"
        val dept = snapshot.child("studentDept").value?.toString() ?: "Bölüm Yok"
        val photo = snapshot.child("studentPhoto").value?.toString()

        txtName.text = name
        txtScore.text = "PUAN: $score / 100"

        // Detaylı Seçim Listesi Oluştur
        val detailBuilder = StringBuilder()
        detailBuilder.append("🆔 No: $no | 🏫 Bölüm: $dept\n")
        detailBuilder.append("────────────────────\n\n")

        snapshot.child("selections").children.forEachIndexed { index, data ->
            detailBuilder.append("Soru ${index + 1}:  [ ${data.value} ]\n")
        }
        txtAnswers.text = detailBuilder.toString()

        // Fotoğrafı Yükle
        if (!photo.isNullOrEmpty()) {
            val file = File(photo)
            if (file.exists()) {
                imgStudent.setImageURI(Uri.fromFile(file))
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener {
            dialog.dismiss()
            isQuizDialogShowing = false

            // 500ms sonra bir sonraki sonucu kontrol et
            Handler(Looper.getMainLooper()).postDelayed({
                processNextResult()
            }, 500)
        }
        dialog.show()
    }
}