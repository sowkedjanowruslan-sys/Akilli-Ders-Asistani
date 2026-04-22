package com.example.akillidersasistani

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ItemLessonReportBinding
import java.text.SimpleDateFormat
import java.util.*

class ReportAdapter(private val reportList: List<LessonReport>) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemLessonReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLessonReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = reportList[position]

        holder.binding.apply {
            txtReportLessonName.text = item.lessonName

            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr"))
            txtReportDate.text = sdf.format(Date(item.date))

            txtReportPercent.text = "%${item.understoodPercent}"
            txtReportStudents.text = "${item.totalStudents} Öğrenci"

            reportProgress.progress = item.understoodPercent

            // Renk Ayarı
            val color = if (item.understoodPercent >= 70) Color.parseColor("#34C759")
            else if (item.understoodPercent >= 40) Color.parseColor("#FF9500")
            else Color.parseColor("#FF3B30")

            txtReportPercent.setTextColor(color)
            reportProgress.setIndicatorColor(color)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, LessonReportActivity::class.java).apply {
                putExtra("LESSON_CODE", item.lessonName)
                putExtra("report_id", item.reportId)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = reportList.size
}