package com.example.akillidersasistani

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityAddLessonBinding
import com.example.akillidersasistani.models.LessonModel
import java.util.*
import android.content.Context
class AddLessonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddLessonBinding
    private val viewModel: ScheduleViewModel by viewModels()

    // Düzenleme modunda olup olmadığımızı anlamak için değişken
    private var editLesson: LessonModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- DÜZENLEME MODU KONTROLÜ (KRİTİK KISIM) ---
        // Intent ile gelen veriyi alıyoruz
        editLesson = intent.getSerializableExtra("EDIT_LESSON") as? LessonModel

        if (editLesson != null) {
            // Eğer veri gelmişse, formu dolduruyoruz
            fillFormForEdit(editLesson!!)
        }

        // SAAT SEÇİCİLERİ BAĞLA
        binding.etBaslangicSaat.setOnClickListener { openTimePicker(true) }
        binding.etBitisSaat.setOnClickListener { openTimePicker(false) }

        // ViewModel Sonucunu Dinle
        viewModel.lessonAddStatus.observe(this) { isSuccess ->
            if (isSuccess == true) {
                setupAlarms() // Bildirimleri kur/güncelle
                val message = if (editLesson == null) "Ders Kaydedildi! ✅" else "Ders Güncellendi! 🔄"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.resetStatus()
                finish()
            }
        }
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Veya finish()
        }
        binding.btnSave.setOnClickListener {
            saveOrUpdateLesson()
        }
        checkPermissions()
    }
    private fun checkPermissions() {
        // 1. Bildirim İzni (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 2. Tam Zamanlı Alarm İzni (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
    private fun fillFormForEdit(lesson: LessonModel) {
        binding.apply {
            etDersAdi.setText(lesson.dersAdi)
            etEgitmen.setText(lesson.egitmen)
            etSinif.setText(lesson.sinif)
            etBaslangicSaat.setText(lesson.baslangicSaati)
            etBitisSaat.setText(lesson.bitisSaati)
            btnSave.text = "DEĞİŞİKLİKLERİ KAYDET" // Buton metnini değiştir

            // Spinner'da kayıtlı günü otomatik bulup seçtir
            val gunler = resources.getStringArray(R.array.gunler_array)
            val index = gunler.indexOf(lesson.gun)
            if (index != -1) spinnerGun.setSelection(index)
        }
    }

    private fun saveOrUpdateLesson() {
        val ad = binding.etDersAdi.text.toString().trim()
        val bSaat = binding.etBaslangicSaat.text.toString()

        if (ad.isNotEmpty() && bSaat.contains(":")) {
            // Yeni ders oluştururken veya güncellerken ID'yi koruyoruz
            val lesson = LessonModel(
                id = editLesson?.id ?: "", // Eğer düzenleme ise eski ID, değilse boş (ViewModel'de push olacak)
                dersAdi = ad,
                egitmen = binding.etEgitmen.text.toString().trim(),
                sinif = binding.etSinif.text.toString().trim(),
                gun = binding.spinnerGun.selectedItem.toString(),
                baslangicSaati = bSaat,
                bitisSaati = binding.etBitisSaat.text.toString()
            )
            viewModel.addLesson(lesson)
        } else {
            Toast.makeText(this, "Lütfen Ders Adı ve Başlangıç Saatini girin!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTimePicker(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            if (isStart) binding.etBaslangicSaat.setText(formattedTime)
            else binding.etBitisSaat.setText(formattedTime)
        }, hour, minute, true)

        timePicker.show()
    }

    private fun setupAlarms() {
        // 1. Eğer bu bir DÜZENLEME ise, eski ID'ye ait alarmları önce temizle
        // (Böylece saat değiştiyse eski saatte bildirim çalmaz)
        editLesson?.let {
            AlarmUtils.cancelLessonAlarm(this, it.id)
        }

        // 2. Formdaki güncel verilerle yeni bir model oluştur
        // (id kısmında editLesson?.id kullanarak ID'nin korunmasını sağlıyoruz)
        val lesson = LessonModel(
            id = editLesson?.id ?: "",
            dersAdi = binding.etDersAdi.text.toString(),
            baslangicSaati = binding.etBaslangicSaat.text.toString(),
            gun = binding.spinnerGun.selectedItem.toString(),
            sinif = binding.etSinif.text.toString()
        )

        // 3. Yeni alarmları siteme kaydet
        // 1 saat önce duyur
        AlarmUtils.scheduleLessonAlarm(this, lesson, true)
        // Başladığında duyur
        AlarmUtils.scheduleLessonAlarm(this, lesson, false)
    }
}