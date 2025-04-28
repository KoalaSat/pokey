package com.koalasat.pokey.ui.notifications

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.R
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.database.UserEntity
import com.koalasat.pokey.utils.images.CircleTransform
import com.squareup.picasso.Picasso

class NotificationListAdapter(
    private val notificationList: MutableList<NotificationEntity>,
    private val accountList: MutableList<UserEntity>,
) : RecyclerView.Adapter<NotificationListAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.titleViewItem)
        val textView: TextView = view.findViewById(R.id.textViewItem)
        val userAvatarView: ImageView = view.findViewById(R.id.userAvatar)
        val accountAvatarView: ImageView = view.findViewById(R.id.accountAvatar)

        fun bind(notificationEntity: NotificationEntity, userEntity: UserEntity?) {
            titleView.text = notificationEntity.title
            textView.text = notificationEntity.text

            Picasso.get()
                .load(notificationEntity.avatarUrl)
                .resize(124, 124)
                .centerCrop()
                .transform(CircleTransform())
                .error(R.mipmap.ic_launcher)
                .into(userAvatarView)

            Picasso.get()
                .load(userEntity?.avatar)
                .resize(124, 124)
                .centerCrop()
                .transform(CircleTransform())
                .error(R.mipmap.ic_launcher)
                .into(accountAvatarView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_notification_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notificationEntity = notificationList[position]
        val userEntity = accountList.find { it.hexPub == notificationEntity.accountKexPub }

        holder.itemView.setOnClickListener {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("nostr:${notificationEntity.nip32}")
            }
            try {
                if (deepLinkIntent.resolveActivity(holder.itemView.context.packageManager) != null) {
                    holder.itemView.context.startActivity(deepLinkIntent)
                } else {
                    Toast.makeText(holder.itemView.context, "No application can handle this request.", Toast.LENGTH_LONG).show()
                }
            } catch (e: ActivityNotFoundException) {
                // Handle the exception if the activity is not found
                Toast.makeText(holder.itemView.context, "No application can handle this request.", Toast.LENGTH_LONG).show()
            }
        }

        holder.bind(notificationList[position], userEntity)
    }

    override fun getItemCount() = notificationList.size
}
