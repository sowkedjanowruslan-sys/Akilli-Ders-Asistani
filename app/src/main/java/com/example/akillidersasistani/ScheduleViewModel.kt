package com.example.akillidersasistani

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.akillidersasistani.models.LessonModel
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class ScheduleViewModel : ViewModel() {

    // Firebase Referansı
    private val db = FirebaseDatabase.getInstance().getReference("user_schedule")

    // Bugünün ders listesini tutan LiveData
    private val _todayLessons = MutableLiveData<List<LessonModel>>()
    val todayLessons: LiveData<List<LessonModel>> = _todayLessons

    // Kayıt işleminin sonucunu (Başarılı/Hatalı) bildiren LiveData
    private val _lessonAddStatus = MutableLiveData<Boolean?>()
    val lessonAddStatus: LiveData<Boolean?> = _lessonAddStatus

    fun fetchTodayLessons() {
        // 1. Sistemden gün ismini al (Örn: Cuma)
        val sdf = SimpleDateFormat("EEEE", Locale("tr"))
        val todayRaw = sdf.format(Date()).trim() // Boşlukları temizle

        // 2. İlk harf büyük, gerisi küçük formatına zorla (cuma -> Cuma)
        val formattedToday = todayRaw.lowercase().replaceFirstChar { it.uppercase() }

        // DEBUG İÇİN: Telefonun hangi günü aradığını Logcat'e yazar
        android.util.Log.d("ProgramHata", "Telefonun aradığı gün: '$formattedToday'")

        db.orderByChild("gun").equalTo(formattedToday).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<LessonModel>()
                if (snapshot.exists()) {
                    for (data in snapshot.children) {
                        val lesson = data.getValue(LessonModel::class.java)
                        lesson?.let {
                            list.add(it.copy(id = data.key ?: ""))
                        }
                    }
                } else {
                    android.util.Log.d("ProgramHata", "Firebase'de '$formattedToday' günü için hiç ders bulunamadı.")
                }

                // Başlangıç saatine göre sırala
                _todayLessons.value = list.sortedBy { it.baslangicSaati }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseError", "Hata: ${error.message}")
            }
        })
    }
    fun addLesson(lesson: LessonModel) {
        // ID kontrolü: Eğer dersin ID'si varsa onu kullan, yoksa Firebase'den yeni anahtar al.
        val refId = if (lesson.id.isEmpty()) {
            db.push().key ?: return
        } else {
            lesson.id
        }

        // Veriyi belirlenen ID ile Firebase'e kaydet (id alanını da güncelleyerek)
        db.child(refId).setValue(lesson.copy(id = refId)).addOnCompleteListener { task ->
            // Kayıt sonucunu Activity'ye bildir
            _lessonAddStatus.value = task.isSuccessful
        }
    }

    /**
     * LiveData durumunu sıfırlar (Activity tekrar açıldığında eski sonucun tetiklenmesini önler).
     */
    fun resetStatus() {
        _lessonAddStatus.value = null
    }

    /**
     * Belirtilen ID'ye sahip dersi siler.
     */
    fun deleteLesson(id: String) {
        if (id.isNotEmpty()) {
            db.child(id).removeValue()
        }
    } }
