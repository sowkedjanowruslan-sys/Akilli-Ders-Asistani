package com.example.akillidersasistani

import android.Manifest
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import com.example.akillidersasistani.databinding.ActivityStudentBinding
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.Locale
import android.widget.TextView
import android.os.Environment
import java.text.SimpleDateFormat
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.google.ai.client.generativeai.type.content
class StudentActivity : AppCompatActivity() {
    private var recordingDialog: android.app.Dialog? = null
    private var secondsElapsed = 0
    private val timerHandler = Handler(Looper.getMainLooper())

    private lateinit var audioHandler: AudioRecorderHandler
    private var isRecording = false
    private val projectsList = mutableListOf<Map<String, Any>>()
    private var dialogSubtitleTextView: TextView? = null

    private var quizQuestions = mutableListOf<List<String>>() // Soru | A | B | C | D | Ans
    private var currentQuizIndex = 0
    private var quizScore = 0
    private var userSelections = mutableListOf<String>()
    private var adSoyad: String = ""
    private var ogrenciNo: String = ""
    private var cinsiyet: String = ""
    private var lastSubtitleText = ""
    private val fullTranscript = StringBuilder() // Tüm konuşmaları biriktirir
    private var sessionConfigListener: ValueEventListener? = null
    private var lastFeedbackTime: Long = 0
    private lateinit var binding: ActivityStudentBinding
    private var photoUri: String? = null
    private lateinit var audioManager: AudioManager
    private lateinit var db: DatabaseReference
    private var subtitleTimer: Handler? = null
    private var sessionCode: String? = null
    private val userId = UUID.randomUUID().toString()
    private var mediaPlayer: MediaPlayer? = null

    // Fotoğraf Seçici
    private val selectPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                // ADIM 1: Seçilen URI için Android'den KALICI izin iste (Bu satır atlamayı engeller)
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(selectedUri, takeFlags)

                // ADIM 2: Fotoğrafı kalıcı klasöre kopyala (Zaten sahip olduğun fonksiyon)
                val permanentPath = saveImageToInternalStorage(selectedUri)

                if (permanentPath != null) {
                    val fileUri = Uri.fromFile(File(permanentPath))
                    binding.imgAvatar.setImageURI(fileUri)

                    // SharedPreferences ve Firebase güncellemeleri
                    getSharedPreferences("UserProfile", Context.MODE_PRIVATE).edit()
                        .putString("student_photo", permanentPath).apply()

                    sessionCode?.let { code ->
                        db.child("sessions").child(code).child("users")
                            .child(userId).child("photo").setValue(permanentPath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("SecurityError", "Persistable permission hatası: ${e.message}")
                // Eğer kalıcı izin alınamazsa bile kopyalama işlemine devam etmeyi deneyebilir
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioHandler = AudioRecorderHandler(this)

        // 1. ViewBinding Başlatma (Hata buradaydı)
        binding = ActivityStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase.getInstance().reference
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 3. Verileri ve Görünümleri Yükle
        loadUserProfile()
        initViews()        // GRİ OLAN setupButtons() ARTIK RENKLENECEK
        setupProfileNavigation()
    }
    private fun showRecordingUI() {recordingDialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_audio_recording, null)
        recordingDialog?.setContentView(view)
        recordingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        recordingDialog?.setCancelable(false)

        val txtTimer = view.findViewById<TextView>(R.id.txtTimer)
        val btnStop = view.findViewById<Button>(R.id.btnStopRecording)

        // Zamanlayıcıyı Başlat
        secondsElapsed = 0
        startTimer(txtTimer)

        btnStop.setOnClickListener {
            stopRecordingProcess()
        }

        recordingDialog?.show()
    }

    private fun startTimer(textView: TextView) {
        timerHandler.post(object : Runnable {
            override fun run() {
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                textView.text = String.format("%02d:%02d", minutes, seconds)
                secondsElapsed++
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun stopRecordingProcess() {
        timerHandler.removeCallbacksAndMessages(null)
        audioHandler.stopRecording()
        recordingDialog?.dismiss()
        isRecording = false
        generateSmartNoteAnalysis() // AI Analizini başlat
    }
    private fun incrementFeedbackTotal() {
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt("total_feedback", 0)
        val newCount = currentCount + 1
        sharedPref.edit().putInt("total_feedback", newCount).apply()

    }
    private fun startQuizGame(rawContent: String) {
        // 1. Veriyi Parçala
        quizQuestions.clear()
        val lines = rawContent.split("\n").filter { it.contains("|") }
        lines.forEach { quizQuestions.add(it.split("|").map { item -> item.trim() }) }

        currentQuizIndex = 0
        quizScore = 0
        userSelections.clear()

        // 2. Dialog Oluştur
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quiz_game, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        updateQuizUI(dialogView, dialog)
        dialog.show()
    }
    private fun updateQuizUI(view: View, dialog: android.app.Dialog) {
        if (currentQuizIndex >= quizQuestions.size) return

        val question = quizQuestions[currentQuizIndex]
        val totalQuestions = quizQuestions.size

        // --- PROGRESS BAR GÜNCELLEME (KESİN ÇÖZÜM) ---
        val progressBar = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.quizProgressBar)

        // Çizginin doluluk oranını hesapla: (Mevcut Soru No / Toplam Soru) * 100
        // Örn: 5 soruda 1. soru %20, 2. soru %40, 3. soru %60... 5. soru %100 olur.
        val progressValue = ((currentQuizIndex + 1) * 100) / totalQuestions

        // Animasyonlu olarak ilerlet (Yumuşak uzama efekti)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(progressValue, true)
        } else {
            progressBar.progress = progressValue
        }
        // --------------------------------------------

        val txtProgress = view.findViewById<TextView>(R.id.txtQuizProgress)
        val txtScore = view.findViewById<TextView>(R.id.txtQuizScore)
        val txtQuestion = view.findViewById<TextView>(R.id.txtQuestionText)
        val btnA = view.findViewById<MaterialButton>(R.id.btnOptA)
        val btnB = view.findViewById<MaterialButton>(R.id.btnOptB)
        val btnC = view.findViewById<MaterialButton>(R.id.btnOptC)
        val btnD = view.findViewById<MaterialButton>(R.id.btnOptD)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitQuiz)

        // Soru No ve Puan Metinlerini Güncelle
        txtProgress.text = "Soru ${currentQuizIndex + 1}/$totalQuestions"
        txtScore.text = "Puan: $quizScore"
        txtQuestion.text = question[0]

        // Şıkları Yerleştir
        btnA.text = "A) ${question[1]}"
        btnB.text = "B) ${question[2]}"
        btnC.text = "C) ${question[3]}"
        btnD.text = "D) ${question[4]}"

        val correctAnswer = question[5].trim().uppercase()
        val buttons = listOf(btnA, btnB, btnC, btnD)
        val options = listOf("A", "B", "C", "D")

        buttons.forEachIndexed { index, button ->
            button.isEnabled = true
            button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E5E5EA")))
            button.setBackgroundColor(Color.TRANSPARENT)

            button.setOnClickListener {
                // Şık seçildiği an diğerlerini kilitle
                buttons.forEach { it.isEnabled = false }
                val selected = options[index]
                userSelections.add(selected)

                if (selected == correctAnswer) {
                    quizScore += 20
                    button.setBackgroundColor(Color.parseColor("#DCFCE7")) // Yeşil Arka Plan
                    button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#34C759"))) // Yeşil Kenarlık
                } else {
                    button.setBackgroundColor(Color.parseColor("#FEE2E2")) // Kırmızı Arka Plan
                    button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FF3B30"))) // Kırmızı Kenarlık

                    // Yanlış cevap verildiğinde doğru şıkkı yeşil göstererek öğrenciye bildir
                    val correctIndex = options.indexOf(correctAnswer)
                    if (correctIndex != -1) {
                        buttons[correctIndex].setBackgroundColor(Color.parseColor("#DCFCE7"))
                    }
                }

                // 1.5 saniye bekle ki öğrenci cevabı görsün, sonra sonraki soruya geç
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentQuizIndex < quizQuestions.size - 1) {
                        currentQuizIndex++
                        updateQuizUI(view, dialog)
                    } else {
                        // SINAV BİTTİ EKRANI
                        txtQuestion.text = "Tebrikler!\nSınavı Tamamladınız."
                        progressBar.setProgress(100, true) // Barı tamamen doldur

                        // Şıkları gizle ve Hocaya Gönder butonunu göster
                        buttons.forEach { it.visibility = View.GONE }
                        btnSubmit.visibility = View.VISIBLE
                        btnSubmit.setOnClickListener {
                            sendQuizResultToTeacher(quizScore, userSelections)
                            dialog.dismiss()
                        }
                    }
                }, 1500)
            }
        }
    }
    private fun sendQuizResultToTeacher(score: Int, selections: List<String>) {
        val code = sessionCode ?: return
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        // Öğrenci bilgilerini SharedPreferences'tan çek
        val adSoyad = sharedPref.getString("name", "Bilinmiyor")
        val ogrenciNo = sharedPref.getString("ogrenci_no", "---")
        val bolum = sharedPref.getString("bolum", "Bölüm Yok")
        val photo = sharedPref.getString("student_photo", null)

        val resultData = mapOf(
            "studentName" to adSoyad,
            "studentNo" to ogrenciNo,
            "studentDept" to bolum,
            "studentPhoto" to photo,
            "score" to score,
            "selections" to selections,
            "timestamp" to ServerValue.TIMESTAMP
        )

        // ÖNEMLİ: Her öğrenci kendi UUID (userId) altına kaydedilmeli
        db.child("sessions").child(code).child("quiz_results").child(userId).setValue(resultData)
            .addOnSuccessListener {
                Toast.makeText(this, "Sonuçlar Hocaya İletildi! ✅", Toast.LENGTH_SHORT).show()
            }
    }



    private fun startRecordingWithUI() {
        try {
            audioHandler.startRecording()
            isRecording = true
            showRecordingUI()
            // Pencereyi (Dialog) oluştur
            recordingDialog = android.app.Dialog(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_audio_recording, null)
            recordingDialog?.setContentView(dialogView)
            recordingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
            recordingDialog?.setCancelable(false)

            val txtTimer = dialogView.findViewById<TextView>(R.id.txtTimer)
            val btnStop = dialogView.findViewById<Button>(R.id.btnStopRecording)

            secondsElapsed = 0
            startTimer(txtTimer)

            btnStop.setOnClickListener {
                stopRecordingProcess()
            }

            recordingDialog?.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun initViews() {
        // --- GERİ BİLDİRİM BUTONLARI ---
        binding.btnUnderstood.setOnClickListener {
            triggerHapticFeedback(30)
            showDetailedFeedbackDialog("understood")
        }
        binding.btnRepeat.setOnClickListener {
            triggerHapticFeedback(30)
            showDetailedFeedbackDialog("repeat")
        }
        binding.btnNotUnderstood.setOnClickListener {
            triggerHapticFeedback(30)
            showDetailedFeedbackDialog("not_understood")
        }

        binding.btnStartRecord.setOnClickListener {
            if (sessionCode == null) {
                Toast.makeText(this, "Önce bir derse katılın!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isRecording) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecordingWithUI()
                } else {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        binding.imgAvatar.setOnClickListener {
            triggerHapticFeedback(30)
            val intent = Intent(this, StudentProfileActivity::class.java)
            intent.putExtra("from_main", true)
            startActivity(intent)
        }

        // Sağ üstteki 3 çizgili butona basınca menüyü aç
        binding.btnOpenMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }

// Menü içindeki Matematik Oyunu yazısına tıklandığında
        binding.navView.getHeaderView(0) // Eğer header varsa kullanılırdı, biz direkt ID ile erişelim:
        val btnMath = binding.navView.findViewById<View>(R.id.btnOpenMathGame)
        btnMath.setOnClickListener {
            //1. Önce sağdan açık olan menüyü kapatıyoruz (Görsel akıcılık için)
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)

            // 2. Yeni tam ekran paneli (Activity) başlatıyoruz
            val intent = Intent(this, MathGameActivity::class.java)
            startActivity(intent)

            // İsterseniz geçiş animasyonu da ekleyebilirsiniz (Apple stili için)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        binding.btnEmergency.setOnClickListener {
            triggerHapticFeedback(100)
            val code = sessionCode ?: binding.edtCode.text.toString().trim()

            if (code.isNotEmpty()) {
                val emergencyRef = FirebaseDatabase.getInstance().getReference("Emergencies").child(code)
                val alertData = mapOf(
                    "studentName" to adSoyad,
                    "status" to "active",
                    "timestamp" to ServerValue.TIMESTAMP
                )
                emergencyRef.setValue(alertData).addOnSuccessListener {
                    incrementFeedbackTotal() // Acil durum da bir geri bildirimdir
                    Toast.makeText(this, "Acil durum iletildi!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Önce bir derse katılın!", Toast.LENGTH_SHORT).show()
            }
        }

        // --- DERSE KATILMA ---
        binding.btnJoin.setOnClickListener {
            triggerHapticFeedback(50)
            val code = binding.edtCode.text.toString().trim().uppercase()
            if (code.length == 8) {
                joinSession(code)
            } else {
                Toast.makeText(this, "Kod 8 hane olmalı", Toast.LENGTH_SHORT).show()
            }
        }
        // StudentActivity.kt -> initViews() içinde
        binding.btnAiNote.setOnClickListener {
            if (sessionCode == null) {
                Toast.makeText(this, "Önce bir derse katılmalısınız!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            triggerHapticFeedback(40)

            // ARTIK DİREKT generateSmartNoteAnalysis() DEĞİL, KONTROLÜ ÇAĞIRIYORUZ
            checkAndShowSmartNoteIntro()
        }
        // --- AYARLAR VE DİĞERLERİ ---
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            triggerHapticFeedback(20)
            getSharedPreferences("Settings", Context.MODE_PRIVATE).edit()
                .putBoolean("sound_enabled", isChecked).apply()
        }

        binding.btnOpenSubtitles.setOnClickListener {
            triggerHapticFeedback(30)
            if (sessionCode != null) showSubtitlesDialog()
        }

        binding.btnDownloadLesson.setOnClickListener {
            triggerHapticFeedback(40)
            generateLessonPDF()
        }

        setButtonsEnabled(false)
    }
    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingWithUI()
        } else {
            Toast.makeText(this, "Mikrofon izni verilmedi!", Toast.LENGTH_SHORT).show()
        }
    }
    private fun checkAndShowSmartNoteIntro() {
        val sharedPref = getSharedPreferences("StudentAppPrefs", Context.MODE_PRIVATE)
        val isFirstTime = sharedPref.getBoolean("isFirstSmartNoteLaunch", true)

        if (isFirstTime) {
            // --- İLK KEZ AÇILIYOR: TANITIM DİYALOĞUNU GÖSTER ---
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_smart_note_intro, null)
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false) // Öğrenci okumadan geçmesin
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Tanıtım penceresindeki butona tıklama olayı
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSmartNoteIntroStart).setOnClickListener {
                triggerHapticFeedback(30)

                // Bir daha gösterme bilgisini kaydet
                sharedPref.edit().putBoolean("isFirstSmartNoteLaunch", false).apply()

                dialog.dismiss()

                // Tanıtım bitti, asıl analizi başlat
                generateSmartNoteAnalysis()
            }

            dialog.show()
        } else {
            // --- DAHA ÖNCE GÖRÜLMÜŞ: DİREKT ANALİZİ BAŞLAT ---
            generateSmartNoteAnalysis()
        }
    }
    private fun listenLiveSubtitles(code: String) {
        val liveRef = db.child("sessions").child(code).child("live_text")

        liveRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Activity hayatta mı kontrol et
                if (isFinishing || isDestroyed) return

                val text = snapshot.getValue(String::class.java) ?: ""

                if (text.isNotEmpty() && text != lastSubtitleText) {
                    runOnUiThread {
                        // A. ANA EKRANDAKİ KÜÇÜK KARTI GÜNCELLE
                        binding.txtLiveSubtitles.text = text
                        binding.cardSubtitles.visibility = View.VISIBLE

                        // B. TÜM VERİYİ BİRİKTİR (Duplicate/Tekrar kontrolü ile)
                        if (!fullTranscript.toString().contains(text)) {
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            fullTranscript.append("[$time] $text\n")
                        }

                        // C. EĞER PENCERE (DIALOG) AÇIKSA ORAYI DA ANINDA GÜNCELLE
                        dialogSubtitleTextView?.let { dialogTxt ->
                            dialogTxt.text = fullTranscript.toString()
                            // Parent olan ScrollView'u bul ve en aşağı kaydır
                            val scroll = dialogTxt.parent as? androidx.core.widget.NestedScrollView
                            scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
                        }
                    }

                    lastSubtitleText = text

                    // 5 saniye sessizlikte küçük kartı gizle
                    subtitleTimer?.removeCallbacksAndMessages(null)
                    subtitleTimer = Handler(Looper.getMainLooper())
                    subtitleTimer?.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            binding.cardSubtitles.visibility = View.GONE
                        }
                    }, 5000)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("STT_ERROR", "Firebase hatası: ${error.message}")
            }
        })
    }
    private fun generateLessonPDF() {
        val transcriptStr = fullTranscript.toString()
        if (transcriptStr.isEmpty()) {
            Toast.makeText(this, "Henüz ders notu birikmedi!", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val paint = Paint()
        paint.textSize = 12f

        // A4 kağıt boyutları
        val pageWidth = 595
        val pageHeight = 842
        var pageCount = 1

        // İlk Sayfayı Oluştur
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Başlık
        paint.isFakeBoldText = true
        paint.textSize = 16f
        canvas.drawText("DERS NOTLARI - $sessionCode", 50f, 50f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 12f
        var y = 100f // Başlangıç yüksekliği

        // Satır satır yazdır ve sayfa sonuna gelince yeni sayfa aç
        transcriptStr.split("\n").forEach { line ->
            // Eğer y koordinatı sayfa sınırını (800) geçerse yeni sayfa aç
            if (y > 800) {
                pdfDocument.finishPage(page) // Mevcut sayfayı bitir
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                page = pdfDocument.startPage(pageInfo) // Yeni sayfa başlat
                canvas = page.canvas
                y = 50f // Yeni sayfada en üstten başla
            }

            canvas.drawText(line, 50f, y, paint)
            y += 20f // Satır aralığı
        }

        pdfDocument.finishPage(page) // Son sayfayı bitir

        // PDF Kaydetme İşlemi (Downloads klasörüne)
        val fileName = "Ders_Notu_${sessionCode}_${System.currentTimeMillis()}.pdf"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
            Toast.makeText(this, "PDF İndirilenler klasörüne kaydedildi!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun triggerHapticFeedback(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(ms)
        }
    }
    private fun loadUserProfile() {
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        adSoyad = sharedPref.getString("name", "") ?: ""
        ogrenciNo = sharedPref.getString("ogrenci_no", "") ?: ""
        cinsiyet = sharedPref.getString("gender", "erkek") ?: "erkek"
        // "bolum" anahtarını kullandığından emin ol (StudentProfile ile aynı olmalı)
        val bolum = sharedPref.getString("bolum", "Bölüm Belirtilmemiş") ?: "Bölüm Belirtilmemiş"
        photoUri = sharedPref.getString("student_photo", null)

        // KONTROL: Eğer isim veya numara boşsa Profil sayfasına zorunlu yönlendir
        if (adSoyad.isEmpty() || ogrenciNo.isEmpty()) {
            val intent = Intent(this, StudentProfileActivity::class.java)
            intent.putExtra("from_main", false) // İlk kurulum olduğunu bildirir
            startActivity(intent)
            finish()
            return
        }

        // UI GÜNCELLEME
        binding.txtStudentName.text = adSoyad
        binding.txtStudentDetail.text = "$bolum | No: $ogrenciNo"

        if (!photoUri.isNullOrEmpty()) {
            val file = File(photoUri!!)
            if (file.exists()) {
                binding.imgAvatar.setImageURI(Uri.fromFile(file))
                binding.imgAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                // Dosya silindiyse veya bulunamadıysa cinsiyete göre varsayılan ikon
                val defaultIcon = if (cinsiyet == "kadın") R.drawable.ic_student_female else R.drawable.ic_student_male
                binding.imgAvatar.setImageResource(defaultIcon)
            }
        }
    }
    private val studentAiSystemInstruction = """
    Sen 'Akıllı Ders Asistanı' sisteminin öğrenci tarafındaki yapay zekasısın. 
    Görevin: Ders notlarını analiz etmek ve öğrencinin dersle ilgili sorularını yanıtlamaktır.

    KESİN KURALLAR:
    1. Sadece ders içerikleri, akademik konular ve bilimsel bilgiler hakkında konuş.
    2. Siyaset, cinsellik, şiddet, kumar, küfür veya yasa dışı içeriklerle ilgili hiçbir soruya asla cevap verme.
    3. Türkiye Cumhuriyeti yasalarına, ahlak kurallarına ve milli eğitim vizyonuna aykırı içerik üretme.
    4. Eğer öğrenci ders dışı veya yasaklı bir konudan bahsederse, kibarca: 'Üzgünüm, ben sadece derslerinle ilgili konularda yardımcı olabilirim. Lütfen dersle ilgili bir soru sor.' cevabını ver.
    5. Görsel oluşturma veya ödev yapma (hazıra konma) değil, konuyu açıklama odaklı davran.
""".trimIndent()

    private fun generateSmartNoteAnalysis() {
        val transcript = fullTranscript.toString()
        if (transcript.isEmpty()) {
            Toast.makeText(this, "Henüz analiz edilecek ders notu birikmedi!", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Yapay Zeka Ders Notlarını Güvenli Bir Şekilde Analiz Ediyor...")
            setCancelable(false)
            show()
        }

        val prompt = """
    Aşağıdaki ders metnini analiz et. Sadece eğitim odaklı, kritik noktaları belirten bir özet çıkar:
    DERS METNİ: $transcript
    
    Lütfen şu formatta cevap ver:
    KRİTİK YERLER: [Önemli kavramlar]
    BÖLÜMLER:
    Konu Başlığı | Kısa Özet
    TAVSİYE: [Çalışma önerisi]
""".trimIndent()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash", // Senin istediğin versiyon
                    apiKey = "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao",
                    systemInstruction = content { text(studentAiSystemInstruction) }
                )

                val response = generativeModel.generateContent(prompt)
                val resultText = response.text ?: "Analiz başarısız."

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showSmartNoteDialog(resultText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@StudentActivity, "Bağlantı hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSmartNoteDialog(aiResult: String) {
        // 1. Layout'u şişir
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_smart_note, null)

        //2. ID'leri bağla
        val txtCritical = dialogView.findViewById<TextView>(R.id.txtCriticalPoints)
        val tableTopics = dialogView.findViewById<TableLayout>(R.id.tableTopics)
        val txtPlaceholder = dialogView.findViewById<TextView>(R.id.txtTablePlaceholder)
        val edtQuestion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtAiQuestion)
        val btnAsk = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAskAi)
        val cardAnswer = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAiAnswer)
        val txtAnswer = dialogView.findViewById<TextView>(R.id.txtAiAnswer)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 3. Analiz sonucunu işle
        try {
            val sections = aiResult.split("KRİTİK YERLER:", "BÖLÜMLER:", "TAVSİYE:")
            if (sections.size >= 3) {
                txtCritical.text = sections[1].trim()
                txtPlaceholder.visibility = View.GONE

                val lines = sections[2].trim().split("\n")
                lines.forEach { line ->
                    if (line.contains("|")) {
                        val parts = line.split("|")
                        val row = TableRow(this).apply { setPadding(0, 10, 0, 10) }

                        // Başlık Sütunu
                        val tvTitle = TextView(this).apply {
                            text = parts[0].trim()
                            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                            setTextColor(Color.parseColor("#6200EE"))
                            textSize = 14f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }

                        // Durum Sütunu (tvStatus hatası burada düzeltildi)
                        val tvStatusText = TextView(this).apply {
                            text = "Tamamlandı"
                            layoutParams = TableRow.LayoutParams(180, TableRow.LayoutParams.WRAP_CONTENT)
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            setTextColor(Color.parseColor("#4CAF50"))
                            textSize = 12f
                        }

                        row.addView(tvTitle)
                        row.addView(tvStatusText) // Değişken adı senkronize edildi
                        tableTopics.addView(row)
                    }
                }
            }
        } catch (e: Exception) {
            txtCritical.text = aiResult
        }

        // 4. Gemini 2.5 ile Soru-Cevap
        btnAsk.setOnClickListener {
            val question = edtQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                triggerHapticFeedback(50)
                btnAsk.isEnabled = false
                btnAsk.text = "Sorgulanıyor..."
                cardAnswer.visibility = View.VISIBLE
                txtAnswer.text = "🤖 Ders dökümü analiz ediliyor..."

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val model = GenerativeModel(
                            modelName = "gemini-2.5-flash",
                            apiKey = "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao",
                            systemInstruction = content { text(studentAiSystemInstruction) }
                        )

                        val chatResponse = model.generateContent("Ders Notu:\n${fullTranscript}\n\nSoru: $question")

                        withContext(Dispatchers.Main) {
                            txtAnswer.text = chatResponse.text ?: "Cevap üretilemedi."
                            btnAsk.isEnabled = true
                            btnAsk.text = "Asistana Sor"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            txtAnswer.text = "⚠️ Üzgünüm, şu an yanıt veremiyorum."
                            btnAsk.isEnabled = true
                            btnAsk.text = "Asistana Sor"
                        }
                    }
                }
            }
        }
        dialog.show()
    }
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "profile_photo.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath // Dosyanın yeni ve kalıcı adresi
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun joinSession(code: String) {
        binding.btnJoin.isEnabled = false
        binding.btnJoin.text = "BAĞLANILIYOR..."
        triggerHapticFeedback(30)

        db.child("sessions").child(code).child("active").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists() && snapshot.value == true) {
                // Önce kodu ata
                sessionCode = code

                // 1. Yerel ders sayısını artır
                val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
                val currentLessons = sharedPref.getInt("total_lessons", 0)
                sharedPref.edit().putInt("total_lessons", currentLessons + 1).apply()
                val isAnon = binding.switchAnon.isChecked

                // 2. Kullanıcı verisini hazırla
                val userRef = db.child("sessions").child(code).child("users").child(userId)


                val userData = mapOf(
                    "name" to adSoyad,
                    "no" to ogrenciNo,
                    "department" to (getSharedPreferences("UserProfile", MODE_PRIVATE).getString("bolum", "Bölüm Yok")),
                    "gender" to cinsiyet,
                    "isAnonymous" to isAnon, // Artık hata vermeyecek
                    "status" to "LIVE",
                    "type" to "online",
                    "lastTimestamp" to ServerValue.TIMESTAMP,
                    "understoodCount" to 0,
                    "repeatCount" to 0,
                    "notUnderstoodCount" to 0,
                    "photo" to photoUri
                )

                // HATA BURADAYDI: onDisconnect'i child üzerinde çağırıyoruz
                userRef.child("type").onDisconnect().setValue("offline")

                userRef.setValue(userData).addOnSuccessListener {
                    // UI Güncelleme
                    binding.btnJoin.visibility = View.GONE
                    binding.edtCode.isEnabled = false
                    binding.txtConnectionStatus.text = "DERS BAĞLANTISI AKTİF ✅"
                    binding.txtConnectionStatus.setTextColor(Color.parseColor("#34C759"))

                    // Dinleyicileri BAŞLAT
                    listenForTeacherSettings(code)
                    incrementUniqueStudentCount(userId)
                    setButtonsEnabled(true)
                    listenSessionConfig(code)
                    listenLiveSubtitles(code)
                    listenForLiveQuiz(code)
                    listenForProjects(code)
                    triggerHapticFeedback(100)
                    Toast.makeText(this, "Derse başarıyla katıldınız!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    binding.btnJoin.isEnabled = true
                    binding.btnJoin.text = "TEKRAR DENE"
                    Toast.makeText(this, "Veri gönderilemedi!", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.btnJoin.isEnabled = true
                binding.btnJoin.text = "DERSE KATIL"
                triggerHapticFeedback(300)
                Toast.makeText(this, "Geçersiz veya sonlanmış ders kodu!", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            binding.btnJoin.isEnabled = true
            binding.btnJoin.text = "HATA: Sunucuya ulaşılamadı"
        }
    }


    private fun incrementFeedbackCount(type: String) {
        val code = sessionCode ?: return

        // 1. ÖĞRENCİNİN KENDİ VERİSİNİ GÜNCELLE (Hocanın listesinde anlık görünmesi için)
        val userRef = db.child("sessions").child(code).child("users").child(userId)

        // Kimlik bilgilerini de her basışta tazele ki hoca listesinde hata olmasın
        val updates = mapOf(
            "type" to type,
            "name" to adSoyad,
            "no" to ogrenciNo,
            "department" to (getSharedPreferences("UserProfile", MODE_PRIVATE).getString("bolum", "Bölüm Yok")),
            "lastTimestamp" to ServerValue.TIMESTAMP
        )
        userRef.updateChildren(updates)

        // 2. SAYAÇLARI ARTIR (Transaction kısmı aynı kalabilir)
        val field = when (type) {
            "understood" -> "understoodCount"
            "repeat" -> "repeatCount"
            "not_understood" -> "notUnderstoodCount"
            else -> return
        }
        userRef.child(field).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val currentCount = data.getValue(Int::class.java) ?: 0
                data.value = currentCount + 1
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })

        // 3. Dersin genel istatistiklerini (Grafik için) artır
        val globalStatField = when (type) {
            "understood" -> "understood"
            "not_understood" -> "not_understood"
            "repeat" -> "repeat"
            else -> null
        }
        globalStatField?.let {
            db.child("sessions").child(code).child("stats").child(it).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.getValue(Int::class.java) ?: 0
                    data.value = current + 1
                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })
        }
    }
    private fun incrementUniqueStudentCount(studentId: String) {
        // Firebase'de 'UniqueStudents' düğümünde bu ID var mı bak
        val uniqueRef = FirebaseDatabase.getInstance().getReference("UniqueStudents").child(studentId)

        uniqueRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // ÖĞRENCİ İLK KEZ GELİYOR!
                uniqueRef.setValue(true) // Artık sistemde kayıtlı

                // Global sayacı 1 artır
                val globalRef = FirebaseDatabase.getInstance().getReference("GlobalStats/totalStudents")
                globalRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentVal = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = currentVal + 1
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
                })
            }
        }
    }
    private fun setupProfileNavigation() {
        // Profil resmine UZUN basınca galeri açılsın
        binding.imgAvatar.setOnLongClickListener {
            triggerHapticFeedback(50)
            // İŞTE BURADA ÇAĞIRIYORUZ (Gri renk burada gidecek):
            selectPhotoLauncher.launch("image/*")
            true
        }

        // Normal basınca profil sayfasına gitsin
        binding.imgAvatar.setOnClickListener {
            val intent = Intent(this, StudentProfileActivity::class.java)
            intent.putExtra("from_main", true)
            startActivity(intent)
        }
    }


    private fun setButtonsEnabled(enabled: Boolean) {
        // Artik 'btnUnderstood' yerine 'binding.btnUnderstood' kullanıyoruz
        binding.btnUnderstood.isEnabled = enabled
        binding.btnRepeat.isEnabled = enabled
        binding.btnNotUnderstood.isEnabled = enabled


        // Görsel geri bildirim (iOS tarzı yarı saydamlık)
        val targetAlpha = if (enabled) 1.0f else 0.4f
        binding.btnUnderstood.alpha = targetAlpha
        binding.btnRepeat.alpha = targetAlpha
        binding.btnNotUnderstood.alpha = targetAlpha
        binding.btnEmergency.isEnabled = true
        binding.btnAiNote.isEnabled = true
        binding.btnStartRecord.isEnabled = true
        binding.btnOpenSubtitles.isEnabled = true
        binding.btnEmergency.visibility = View.VISIBLE
    }

    private fun showDetailedFeedbackDialog(type: String) {
        // 1. Tasarımı (XML) yükle
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detailed_feedback, null)
        // showDetailedFeedbackDialog içindeki kilit kontrolünü şu hale getir:
        val status = binding.txtConnectionStatus.text.toString()
        if (status.contains("KİLİTLENDİ")) {
            triggerHapticFeedback(50) // EKLE: Kullanıcı butona basınca kilitli olduğunu hissetsin
            Toast.makeText(this, "Hoca etkileşimi kapattı.", Toast.LENGTH_SHORT).show()
            return
        }
        // 2. XML içindeki bileşenleri bul
        val edtMessage = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtStudentMessage)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerMinutes)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDetailTitle)
        val btnSend = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSendDetailed)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        // 3. Başlığı buton tipine göre değiştir
        txtTitle.text = when(type) {
            "understood" -> "Nereyi Anladınız?"
            "repeat" -> "Nereyi Tekrar Edelim?"
            else -> "Nereyi Anlamadınız?"
        }

        // 4. Dakika Spinner'ını doldur (1-10 Dakika)
        val minutesList = (1..10).map { "$it Dakika Önce" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, minutesList)
        spinner.adapter = adapter

        // 5. Dialog'u oluştur
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 6. GÖNDER BUTONU (5 Saniye Spam Korumalı)
        btnSend.setOnClickListener {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastFeedbackTime < 5000) {
                val kalanSaniye = (5000 - (currentTime - lastFeedbackTime)) / 1000
                Toast.makeText(this, "Lütfen bekleyin: $kalanSaniye sn.", Toast.LENGTH_SHORT).show()
                triggerHapticFeedback(30)
                return@setOnClickListener
            }

            val message = edtMessage.text.toString().trim()
            val selectedTime = spinner.selectedItem.toString()

            if (message.isNotEmpty()) {
                lastFeedbackTime = currentTime

                // --- YENİ EKLENEN KISIM ---
                incrementFeedbackTotal() // Cihazdaki Toplam Geri Bildirim sayısını 1 artırır
                incrementFeedbackCount(type) // Firebase'deki Hoca panelindeki sayacı artırır
                // --------------------------

                sendDetailedFeedbackToTeacher(type, selectedTime, message)
                dialog.dismiss()
            } else {
                triggerHapticFeedback(30)
                Toast.makeText(this, "Lütfen bir not yazın", Toast.LENGTH_SHORT).show()
            }
        }

        // 7. VAZGEÇ BUTONU
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
    private fun listenForProjects(code: String) {
        db.child("sessions").child(code).child("projects")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Listeyi temizle ve yeni gelenleri ekle (Griliği bitiren kısım)
                        projectsList.clear()

                        // Butonun görünürlüğünü XML'deki ID'ye göre aç
                        // Not: activity_student.xml içinde btnViewProjects butonun olmalı
                        binding.btnViewProjects.visibility = View.VISIBLE

                        binding.btnViewProjects.setOnClickListener {
                            showProjectListDialog(snapshot)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    private fun showProjectListDialog(snapshot: DataSnapshot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_quiz_detail, null) // Mevcut şık tasarımı kullanalım
        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDetailName)
        val txtContent = dialogView.findViewById<TextView>(R.id.txtDetailAnswers)
        val txtScore = dialogView.findViewById<TextView>(R.id.txtDetailScore)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDetail)

        // En güncel projeyi alalım
        val lastProject = snapshot.children.last()
        val title = lastProject.child("title").value.toString()
        val desc = lastProject.child("description").value.toString()
        val deadline = lastProject.child("deadline").value as Long

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(deadline))

        txtTitle.text = title
        txtScore.text = "Son Teslim: $dateStr"
        txtScore.setTextColor(Color.RED)
        txtContent.text = desc

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun sendDetailedFeedbackToTeacher(type: String, timeInfo: String, message: String) {
        val code = sessionCode ?: return
        val isAnon = binding.switchAnon.isChecked
        val studentPhotoForFirebase = if (isAnon) null else photoUri

        val feedbackData = mapOf(
            "type" to type,
            "name" to if (isAnon) "🔒 Gizli Katılımcı" else adSoyad,
            "studentNo" to if (isAnon) "---" else ogrenciNo,
            "message" to message,
            "timeInfo" to timeInfo,
            "isAnonymous" to isAnon, // Bu alanı mutlaka gönderiyoruz
            "photo" to studentPhotoForFirebase,
            "timestamp" to ServerValue.TIMESTAMP
        )

        db.child("sessions").child(code).child("feedback_list").push().setValue(feedbackData)
            .addOnSuccessListener {
                triggerHapticFeedback(100)
                Toast.makeText(this, "Mesajınız iletildi!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForTeacherSettings(code: String) {
        // BURASI ÇOK ÖNEMLİ: TeacherActivity setValue(true) yaptığı yerle aynı olmalı
        db.child("sessions").child(code).child("isSubtitlesActive")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    val isActive = snapshot.getValue(Boolean::class.java) ?: false

                    // Butonun ID'sinin doğru olduğundan emin ol (btnOpenSubtitles)
                    binding.btnOpenSubtitles.visibility = if (isActive) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    private fun listenSessionConfig(code: String) {
        // Kod boşsa veya Activity ölmüşse başlama
        if (code.isEmpty() || isFinishing || isDestroyed) return

        // Eğer eski bir dinleyici varsa önce onu temizle (Üst üste binmeyi önler)
        sessionConfigListener?.let {
            db.child("sessions").child(code).removeEventListener(it)
        }

        // Yeni dinleyici oluştur
        sessionConfigListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Yaşam döngüsü kontrolü
                if (isFinishing || isDestroyed) return

                // Veri var mı?
                if (!snapshot.exists()) {
                    Log.w("Firebase_Config", "Oturum verisi bulunamadı: $code")
                    return
                }

                try {
                    // Güvenli veri okuma: "config/buttonsEnabled" yoluna doğrudan git
                    val configSnap = snapshot.child("config")
                    val isEnabled = configSnap.child("buttonsEnabled").getValue(Boolean::class.java) ?: true

                    // UI İşlemleri (Binding null-safety için apply kullanıyoruz)
                    binding.apply {
                        // Butonları aktif/pasif yap
                        setButtonsEnabled(isEnabled)

                        // Durum metnini ve rengini güncelle
                        if (!isEnabled) {
                            txtConnectionStatus.text = "ETKİLEŞİM HOCA TARAFINDAN KİLİTLENDİ 🔒"
                            txtConnectionStatus.setTextColor(android.graphics.Color.RED)
                        } else {
                            txtConnectionStatus.text = "DERS BAĞLANTISI AKTİF ✅"
                            // Color.parseColor güvenlidir ancak context üzerinden çekmek daha profesyoneldir
                            txtConnectionStatus.setTextColor(android.graphics.Color.parseColor("#34C759"))
                        }
                    }

                    // Opsiyonel: Hoca altyazı ayarını da buradan dinleyebilirsiniz
                    val isSubtitlesActive = snapshot.child("isSubtitlesActive").getValue(Boolean::class.java) ?: false
                    binding.btnOpenSubtitles.visibility = if (isSubtitlesActive) android.view.View.VISIBLE else android.view.View.GONE

                } catch (e: Exception) {
                    Log.e("Firebase_Config", "Arayüz güncellenirken hata oluştu: ${e.localizedMessage}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase_Config", "Veritabanı okuma yetkisi yok veya bağlantı kesildi: ${error.message}")
            }
        }

        // Dinleyiciyi başlat
        sessionConfigListener?.let {
            db.child("sessions").child(code).addValueEventListener(it)
        }
    }
    private fun listenForLiveQuiz(code: String) {
        db.child("sessions").child(code).child("live_quiz")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    val isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: false
                    val content = snapshot.child("quizContent").getValue(String::class.java) ?: ""

                    // Eğer hoca testi aktif ettiyse yarışmayı başlat
                    if (isActive && content.isNotEmpty()) {
                        // startQuizGame artık gri olmayacak ve yarışma başlayacak
                        startQuizGame(content)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    private fun showSubtitlesDialog() {
        if (sessionCode == null) return
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_live_subtitles, null)
        val txtSubtitlesArea = dialogView.findViewById<TextView>(R.id.txtLiveSubtitles)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseSubtitles)
        val scrollView = txtSubtitlesArea.parent as? androidx.core.widget.NestedScrollView

        // 1. Önce o ana kadar biriken TÜM geçmişi bas (Kullanıcı boş ekran görmesin)
        txtSubtitlesArea.text = fullTranscript.toString().ifEmpty { "Eğitmen konuşması bekleniyor..." }

        // 2. KRİTİK: Bu penceredeki TextView'u sınıf seviyesindeki değişkene bağla
        // Böylece listenLiveSubtitles fonksiyonu yeni kelime geldikçe burayı güncelleyebilir.
        dialogSubtitleTextView = txtSubtitlesArea

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }

        // 3. GÜVENLİK: Dialog kapandığında referansı temizle (Hafıza sızıntısını ve çökmeyi önler)
        dialog.setOnDismissListener {
            dialogSubtitleTextView = null
        }

        try {
            dialog.show()
            // Açıldığında en aşağı kaydır
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        } catch (e: Exception) {
            Log.e("SUBTITLE_ERROR", "Dialog gösterilirken hata: ${e.message}")
        }
    }
    override fun onDestroy() {
        subtitleTimer?.removeCallbacksAndMessages(null) // Zamanlayıcıyı durdur
        mediaPlayer?.release()
        // Dinleyiciyi durdur (Eğer sessionCode varsa)
        sessionConfigListener?.let { listener ->
            sessionCode?.let { code -> db.child("sessions").child(code).removeEventListener(listener) }
        }
        super.onDestroy()
    }
}
