package com.example.akillidersasistani.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ItemLessonCardBinding
import com.example.akillidersasistani.models.LessonModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * NEUTRAL STUDIO - PLATINUM ADAPTER v6.8
 * "Canlı Zaman Motoru" ve "Aktif Odak" sistemi entegre edildi.
 */
class LessonAdapter(
    private val onDeleteClick: (LessonModel) -> Unit,
    private val onEditClick: (LessonModel) -> Unit
) : ListAdapter<LessonModel, LessonAdapter.LessonViewHolder>(LessonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val binding = ItemLessonCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LessonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class LessonViewHolder(val binding: ItemLessonCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lesson: LessonModel, position: Int) {
            binding.apply {
                // 1. TEMEL VERİLERİ BAĞLA
                txtLessonName.text = lesson.dersAdi
                txtStartTime.text = lesson.baslangicSaati
                txtEndTime.text = lesson.bitisSaati
                txtLessonDay.text = lesson.gun.uppercase(java.util.Locale("tr"))

                // Akıllı Detay Metni (Boşluk kontrolü ile)
                val detail = buildString {
                    if (lesson.sinif.isNotEmpty()) append(lesson.sinif)
                    if (lesson.sinif.isNotEmpty() && lesson.egitmen.isNotEmpty()) append(" • ")
                    if (lesson.egitmen.isNotEmpty()) append(lesson.egitmen)
                }
                txtDetail.text = detail

                // 2. APPLE STİLİ AYIRICI ÇİZGİ YÖNETİMİ
                // Listenin son elemanı ise divider'ı gizle
                itemDivider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE

                // 3. CANLI ZAMAN MOTORU (updateLiveProgress fonksiyonuna devredildi)
                updateLiveProgress(lesson)

                // 4. ETKİLEŞİM OLAYLARI
                btnDeleteLesson.setOnClickListener { onDeleteClick(lesson) }
                btnEditLesson.setOnClickListener { onEditClick(lesson) }

                root.setOnLongClickListener {
                    onDeleteClick(lesson)
                    true
                }
            }
        }

        private fun updateLiveProgress(lesson: LessonModel) {
            val sdf = SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val calendar = Calendar.getInstance()

            // 1. GÜN KONTROLÜ: Sadece dersin günü bugünse çalıştır
            val todayName = SimpleDateFormat("EEEE", java.util.Locale("tr")).format(calendar.time)
            if (!lesson.gun.equals(todayName, ignoreCase = true)) {
                binding.vTimelineNode.alpha = 0.2f
                binding.txtRemainingTime.visibility = View.GONE
                binding.tagLive.visibility = View.GONE
                return
            }

            val currentTimeStr = sdf.format(calendar.time)

            try {
                val start = sdf.parse(lesson.baslangicSaati)
                val end = sdf.parse(lesson.bitisSaati)
                val now = sdf.parse(currentTimeStr)

                if (now != null && start != null && end != null) {
                    val params = binding.vTimelineNode.layoutParams as ConstraintLayout.LayoutParams

                    when {
                        // DURUM: DERS ŞU AN CANLI (20:00 - 20:09 arası)
                        (now.after(start) || now == start) && now.before(end) -> {

                            // MATEMATİKSEL HESAPLAMA (Yüzdeyi buluyoruz)
                            val totalDuration = end.time - start.time
                            val timePassed = now.time - start.time

                            // 0.0 ile 1.0 arasında bir değer üretir
                            val progress = timePassed.toFloat() / totalDuration.toFloat()

                            // YUVARLAĞI HAREKET ETTİR (Hassas Konumlandırma)
                            params.verticalBias = progress.coerceIn(0f, 1f)
                            binding.vTimelineNode.layoutParams = params
                            binding.vTimelineNode.alpha = 1.0f

                            // KALAN SÜRE YAZISI
                            val remainingMillis = end.time - now.time
                            val remainingMinutes = remainingMillis / (1000 * 60)
                            binding.txtRemainingTime.apply {
                                text = "$remainingMinutes dk kaldı"
                                visibility = View.VISIBLE
                            }
                            binding.tagLive.visibility = View.VISIBLE
                        }

                        // DURUM: DERS BİTMİŞ (20:09'dan sonrası)
                        now.after(end) || now == end -> {
                            params.verticalBias = 1.0f // En aşağıda sabitler
                            binding.vTimelineNode.layoutParams = params
                            binding.vTimelineNode.alpha = 0.2f // Sönükleşir
                            binding.txtRemainingTime.visibility = View.GONE
                            binding.tagLive.visibility = View.GONE
                        }

                        // DURUM: DERS HENÜZ BAŞLAMAMIŞ
                        else -> {
                            params.verticalBias = 0.0f // En üstte bekler
                            binding.vTimelineNode.layoutParams = params
                            binding.vTimelineNode.alpha = 1.0f
                            binding.txtRemainingTime.visibility = View.GONE
                            binding.tagLive.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    class LessonDiffCallback : DiffUtil.ItemCallback<LessonModel>() {
        override fun areItemsTheSame(oldItem: LessonModel, newItem: LessonModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LessonModel, newItem: LessonModel) = oldItem == newItem
    }

}