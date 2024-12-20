package com.koalasat.pokey.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.database.UserEntity
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
    private lateinit var accountList: List<UserEntity>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewModel =
            ViewModelProvider(this)[NotificationsViewModel::class.java]

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        notificationsView = root.findViewById(R.id.notifications_list)
        notificationsView.layoutManager = LinearLayoutManager(context)

        Pokey.lastNotificationTime.observe(viewLifecycleOwner) {
            loadNotifications()
        }

        loadNotifications()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            val context = context ?: return@launch
            val dao = AppDatabase.getDatabase(context, "common").applicationDao()

            notificationList = withContext(Dispatchers.IO) { dao.getNotifications() }
            accountList = withContext(Dispatchers.IO) { dao.getUsers() }

            var viewEmpty: View = binding.root.findViewById(R.id.notifications_list_empty)
            if (notificationList.isNotEmpty()) {
                viewEmpty.visibility = View.GONE
            } else {
                viewEmpty.visibility = View.VISIBLE
            }

            notificationListAdapter = NotificationListAdapter(notificationList.toMutableList(), accountList.toMutableList())
            notificationsView.adapter = notificationListAdapter
            notificationsView.adapter?.notifyDataSetChanged()
        }
    }
}
