package com.koalasat.pokey.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.koalasat.pokey.databinding.FragmentNotificationsBinding
import com.koalasat.pokey.models.EncryptedStorage

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewModel.newReplies.value.apply { EncryptedStorage.notifyReplies.value }
        binding.newReplies.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReplies(isChecked)
        }
        viewModel.newReplies.observe(viewLifecycleOwner) { value ->
            binding.newReplies.isChecked = value
        }

        viewModel.newZaps.value.apply { EncryptedStorage.notifyZaps.value }
        binding.newZaps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyZaps(isChecked)
        }
        viewModel.newZaps.observe(viewLifecycleOwner) { value ->
            binding.newZaps.isChecked = value
        }

        viewModel.newReactions.value.apply { EncryptedStorage.notifyReactions.value }
        binding.newReactions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReactions(isChecked)
        }
        viewModel.newReactions.observe(viewLifecycleOwner) { value ->
            binding.newReactions.isChecked = value
        }

        viewModel.newPrivate.value.apply { EncryptedStorage.notifyPrivate.value }
        binding.newPrivate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyPrivate(isChecked)
        }
        viewModel.newPrivate.observe(viewLifecycleOwner) { value ->
            binding.newPrivate.isChecked = value
        }

        viewModel.newQuotes.value.apply { EncryptedStorage.notifyQuotes.value }
        binding.newQuotes.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyQuotes(isChecked)
        }
        viewModel.newQuotes.observe(viewLifecycleOwner) { value ->
            binding.newQuotes.isChecked = value
        }

        viewModel.newMentions.value.apply { EncryptedStorage.notifyMentions.value }
        binding.newMentions.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyMentions(isChecked)
        }
        viewModel.newMentions.observe(viewLifecycleOwner) { value ->
            binding.newMentions.isChecked = value
        }

        viewModel.newReposts.value.apply { EncryptedStorage.notifyResposts.value }
        binding.newReposts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyReposts(isChecked)
        }
        viewModel.newReposts.observe(viewLifecycleOwner) { value ->
            binding.newReposts.isChecked = value
        }

        viewModel.newFollows.value.apply { EncryptedStorage.notifyFollows.value }
        binding.newFollows.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotifyFollows(isChecked)
        }
        viewModel.newFollows.observe(viewLifecycleOwner) { value ->
            binding.newFollows.isChecked = value.apply { EncryptedStorage.notifyFollows.value }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
