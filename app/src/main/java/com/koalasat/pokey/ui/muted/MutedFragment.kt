package com.koalasat.pokey.ui.muted

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.databinding.FragmentMutedBinding
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.NostrClient
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MutedFragment : Fragment() {

    private var _binding: FragmentMutedBinding? = null
    private val binding get() = _binding!!
    private lateinit var publicMutedView: RecyclerView
    private lateinit var privateMutedView: RecyclerView
    private lateinit var publicMutedAdapter: MutedListAdapter
    private lateinit var privateMutedAdapter: MutedListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val mutedViewModel =
            ViewModelProvider(this).get(MutedViewModel::class.java)

        _binding = FragmentMutedBinding.inflate(inflater, container, false)

        val root: View = binding.root

        Pokey.isEnabled.observe(viewLifecycleOwner) {
            if (it) {
                binding.activeAccount.visibility = View.VISIBLE
                binding.titleMuted.visibility = View.GONE
            } else {
                binding.titleMuted.visibility = View.VISIBLE
                binding.titleMuted.visibility = View.VISIBLE
            }
        }

        binding.activeAccount.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                Pokey.updateLoadingMuteList(false)
                val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
                val users = dao.getUsers()

                val popupMenu = PopupMenu(requireContext(), binding.activeAccount)

                users.forEach { user ->
                    popupMenu.menu.add(user.name)
                }

                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    val username = item.title.toString()
                    val user = users.find { it.name == username }

                    if (user != null) {
                        EncryptedStorage.updateMutePubKey(user.hexPub)
                    }
                    true
                }

                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    popupMenu.show()
                }
            }
        }

        binding.publishMuteList.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
                val usePubKey = EncryptedStorage.mutePubKey.value
                if (usePubKey != null) {
                    val user = dao.getUser(usePubKey)
                    if (user != null) NostrClient.publishMuteList(requireContext(), user)
                }
            }
        }

        binding.reloadMuteList.setOnClickListener {
            val selectedPubKey = EncryptedStorage.mutePubKey.value
            if (selectedPubKey != null) {
                Pokey.updateLoadingMuteList(true)
                NostrClient.fetchMuteList(requireContext(), selectedPubKey)
            }
        }

        EncryptedStorage.mutePubKey.observeForever {
            CoroutineScope(Dispatchers.IO).launch {
                if (isAdded) {
                    val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
                    val user = withContext(Dispatchers.IO) {
                        dao.getUser(it)
                    }
                    if (user != null) {
                        val handler = Handler(Looper.getMainLooper())
                        handler.post {
                            binding.publishMuteList.visibility = if (user.signer != 1) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                            binding.activeAccount.text = if (user.name?.isNotEmpty() == true) {
                                user.name
                            } else {
                                Hex.decode(user.hexPub).toNpub().substring(0, 10) + "..."
                            }
                        }
                        loadMuteList()
                    }
                }
            }
        }

        Pokey.loadingMuteList.observe(viewLifecycleOwner) {
            if (it) {
                binding.muteListLoading.visibility = View.VISIBLE
            } else {
                binding.muteListLoading.visibility = View.GONE
            }
            publicMutedView.adapter?.notifyDataSetChanged()
            privateMutedView.adapter?.notifyDataSetChanged()
        }

        publicMutedView = root.findViewById(R.id.public_mute_list)
        publicMutedView.layoutManager = LinearLayoutManager(context)
        privateMutedView = root.findViewById(R.id.private_mute_list)
        privateMutedView.layoutManager = LinearLayoutManager(context)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadMuteList() {
        lifecycleScope.launch {
            val kind = 10000
            val hexKey = EncryptedStorage.mutePubKey.value.toString()
            val dao = context?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            if (dao != null) {
                val muteList = withContext(Dispatchers.IO) {
                    val lastCreatedAt = dao.getMostRecentMuteListDate(hexKey) ?: 0
                    dao.getMuteList(kind, hexKey, lastCreatedAt).filter { arrayOf("p", "e").contains(it.tagType) }
                }
                publicMutedAdapter = MutedListAdapter(requireContext(), muteList.filter { it.private != 1 }.toMutableList())
                publicMutedView.adapter = publicMutedAdapter

                privateMutedAdapter = MutedListAdapter(requireContext(), muteList.filter { it.private == 1 }.toMutableList())
                privateMutedView.adapter = privateMutedAdapter
            }
            privateMutedView.adapter?.notifyDataSetChanged()
            publicMutedView.adapter?.notifyDataSetChanged()
        }
    }
}
