package com.koalasat.pokey.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var notificationsView: RecyclerView
    private lateinit var notificationListAdapter: NotificationListAdapter
    private lateinit var notificationList: List<NotificationEntity>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewModel =
            ViewModelProvider(this)[NotificationsViewModel::class.java]

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        loadNotifications()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            val dao = context?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            if (dao != null) {
                notificationList = withContext(Dispatchers.IO) { dao.getNotifications() }
                notificationListAdapter = NotificationListAdapter(notificationList.toMutableList())
                notificationsView.adapter = notificationListAdapter
            }
            notificationsView.adapter?.notifyDataSetChanged()
        }
    }
}
