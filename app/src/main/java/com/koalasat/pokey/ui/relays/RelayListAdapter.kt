package com.koalasat.pokey.ui.relays

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.R
import com.koalasat.pokey.database.RelayEntity
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.utils.isDarkThemeEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RelayListAdapter(
    private val relayList: MutableList<RelayEntity>,
) : RecyclerView.Adapter<RelayListAdapter.ViewHolder>() {
    class ViewHolder(view: View, private val adapter: RelayListAdapter) : RecyclerView.ViewHolder(view) {
        private val deleteIcon: ImageView = itemView.findViewById(R.id.remove_relay)

        fun bind(relayEntity: RelayEntity, position: Int) {
            deleteIcon.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    NostrClient.deleteRelay(relayEntity.hexPub, itemView.context, relayEntity.url, relayEntity.kind)
                    withContext(Dispatchers.Main) {
                        adapter.removeItem(position)
                    }
                }
            }
        }

        val textView: TextView = view.findViewById(R.id.textViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_relay_item, parent, false)
        return ViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val relayEntity = relayList[position]
        holder.textView.text = relayEntity.url
        val relay = NostrClient.getRelay(relayEntity.url)

        val color = if (relay == null) {
            if (isDarkThemeEnabled(holder.textView.context)) R.color.white else R.color.black
        } else if (relay.isConnected()) {
            R.color.green
        } else {
            R.color.red
        }
        holder.textView.setTextColor(ContextCompat.getColorStateList(holder.textView.context, color))
        holder.bind(relayList[position], position)
    }

    override fun getItemCount() = relayList.size

    fun removeItem(position: Int) {
        relayList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, relayList.size)
    }
}
