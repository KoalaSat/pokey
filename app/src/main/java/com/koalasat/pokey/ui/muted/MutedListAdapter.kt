package com.koalasat.pokey.ui.muted

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.MuteEntity
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MutedListAdapter(
    private val context: Context,
    private val muteList: MutableList<MuteEntity>,
) : RecyclerView.Adapter<MutedListAdapter.ViewHolder>() {
    class ViewHolder(view: View, private val adapter: MutedListAdapter) : RecyclerView.ViewHolder(view) {
        private val deleteIcon: ImageView = itemView.findViewById(R.id.remove_mute)

        fun bind(muteEntity: MuteEntity, position: Int) {
            deleteIcon.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabase.getDatabase(adapter.context, "common").applicationDao()
                        dao.deleteMuteEntity(muteEntity.id)
                        dao.updateMuteThreadNotifications(muteEntity.entityId, 0)
                        dao.updateMuteUserNotifications(muteEntity.entityId, 0)

                        val handler = Handler(Looper.getMainLooper())
                        handler.post {
                            adapter.removeItem(position)
                        }
                    }
                }
            }
        }

        val textView: TextView = view.findViewById(R.id.textViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_muted_item, parent, false)
        return ViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val muteEntity = muteList[position]
        val decodedHex = Hex.decode(muteEntity.entityId)
        val nip32Bech32 = when (muteEntity.tagType) {
            "p" -> {
                decodedHex.toNpub()
            }
            "e" -> {
                decodedHex.toNote()
            }
            else -> ""
        }
        val kind = when (muteEntity.tagType) {
            "p" -> {
                context.getString(R.string.user)
            }
            "e" -> {
                context.getString(R.string.thread)
            }
            else -> ""
        }
        holder.textView.text = buildString {
            append(kind)
            append(": ")
            append(nip32Bech32.substring(0, 15) + "..." + nip32Bech32.substring(nip32Bech32.length - 15))
        }
        holder.itemView.setOnClickListener {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("nostr:$nip32Bech32")
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

        holder.bind(muteList[position], position)
    }

    override fun getItemCount() = muteList.size

    fun removeItem(position: Int) {
        muteList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, muteList.size)
    }
}
