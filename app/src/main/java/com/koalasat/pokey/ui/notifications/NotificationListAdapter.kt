package com.koalasat.pokey.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.R
import com.koalasat.pokey.database.NotificationEntity

class NotificationListAdapter(
    private val notificationList: MutableList<NotificationEntity>,
) : RecyclerView.Adapter<NotificationListAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textViewItem)

        fun bind(notificationEntity: NotificationEntity) {
            textView.text = notificationEntity.eventId
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_notification_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notificationEntity = notificationList[position]
        holder.textView.text = notificationEntity.eventId
        holder.bind(notificationList[position])
    }

    override fun getItemCount() = notificationList.size
}
