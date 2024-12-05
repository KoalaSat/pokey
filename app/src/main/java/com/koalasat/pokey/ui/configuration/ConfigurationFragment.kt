package com.koalasat.pokey.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.databinding.FragmentNotificationsBinding
import com.koalasat.pokey.models.EncryptedStorage
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewModel =
            ViewModelProvider(this)[ConfigurationViewModel::class.java]

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewModel.broadcast.observe(viewLifecycleOwner) { value ->
            binding.broadcast.isChecked = value
        }
        binding.broadcast.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateBroadcast(isChecked)
        }
        viewModel.broadcast.value.apply { EncryptedStorage.broadcast.value }

        viewModel.newReplies.observe(viewLifecycleOwner) { value ->
            binding.newReplies.isChecked = value
        }
        binding.newReplies.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReplies(isChecked)
        }

        viewModel.newZaps.observe(viewLifecycleOwner) { value ->
            binding.newZaps.isChecked = value
        }
        binding.newZaps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyZaps(isChecked)
        }

        viewModel.newReactions.observe(viewLifecycleOwner) { value ->
            binding.newReactions.isChecked = value
        }
        binding.newReactions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReactions(isChecked)
        }

        viewModel.newPrivate.observe(viewLifecycleOwner) { value ->
            binding.newPrivate.isChecked = value
        }
        binding.newPrivate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyPrivate(isChecked)
        }

        viewModel.newQuotes.observe(viewLifecycleOwner) { value ->
            binding.newQuotes.isChecked = value
        }
        binding.newQuotes.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyQuotes(isChecked)
        }

        viewModel.newReposts.observe(viewLifecycleOwner) { value ->
            binding.newReposts.isChecked = value
        }
        binding.newReposts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReposts(isChecked)
        }

        viewModel.newMentions.observe(viewLifecycleOwner) { value ->
            binding.newMentions.isChecked = value
        }
        binding.newMentions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyMentions(isChecked)
        }

        binding.accountTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.updateUserHexPubKey(tab.tag.toString())
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // This is called when a tab is unselected
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // This is called when a tab is reselected
            }
        })

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
            val users = dao.getUsers()
            withContext(Dispatchers.Main) {
                var firstInstance = false
                users.forEach {
                    if (!firstInstance) {
                        firstInstance = true
                        viewModel.updateUserHexPubKey(it.hexPub)
                    }
                    val name = if (it.name?.isNotEmpty() == true) {
                        it.name
                    } else {
                        Hex.decode(it.hexPub).toNpub().substring(0, 10) + "..."
                    }

                    binding.accountTabLayout.addTab(binding.accountTabLayout.newTab().setText(name).setTag(it.hexPub))
                }
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
