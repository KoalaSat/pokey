package com.koalasat.pokey.ui.relays

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.RelayEntity
import com.koalasat.pokey.databinding.FragmentRelaysBinding
import com.koalasat.pokey.models.NostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RelaysFragment : Fragment() {
    private val publicRelaysKind = 10002
    private val privateRelaysKind = 10050

    private var _binding: FragmentRelaysBinding? = null
    private val binding get() = _binding!!
    private lateinit var publicRelaysView: RecyclerView
    private lateinit var privateRelaysView: RecyclerView
    private lateinit var publicRelayAdapter: RelayListAdapter
    private lateinit var privateRelayAdapter: RelayListAdapter
    private lateinit var publicList: List<RelayEntity>
    private lateinit var privateList: List<RelayEntity>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val relaysViewModel =
            ViewModelProvider(this).get(RelaysViewModel::class.java)

        _binding = FragmentRelaysBinding.inflate(inflater, container, false)

        val root: View = binding.root

        val textView: TextView = binding.titleRelays
        Pokey.isEnabled.observe(viewLifecycleOwner) {
            textView.visibility = if (it) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        binding.addPublicRelayUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                relaysViewModel.updateNewPublicRelay(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        binding.addPublicRelay.setOnClickListener {
            if (relaysViewModel.validationResultPublicRelay.value == true) {
                showRelayDialog(publicRelaysKind) {
                    binding.addPublicRelayUrl.error = null
                    insertRelay(relaysViewModel.newPublicRelay.value!!, 10002)
                    binding.addPublicRelayUrl.setText("")
                    publicRelaysView.adapter?.notifyDataSetChanged()
                }
            } else {
                binding.addPublicRelayUrl.error = getString(R.string.invalid_uri)
            }
        }
        binding.publishPublicRelay.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                context?.let { it1 -> NostrClient.publishPublicRelays(it1) }
            }
        }
        binding.reloadPublicRelays.setOnClickListener {
            Pokey.updateLoadingPublicRelays(true)
            reconnectRelays(publicRelaysKind)
        }
        Pokey.loadingPublicRelays.observe(viewLifecycleOwner) {
            binding.publicRelaysLoading.visibility = if (it) {
                View.VISIBLE
            } else {
                View.GONE
            }
            loadRelays()
            publicRelaysView.adapter?.notifyDataSetChanged()
        }

        binding.addPrivateRelayUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                relaysViewModel.updateNewPrivateRelay(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        binding.addPrivateRelay.setOnClickListener {
            if (relaysViewModel.validationResultPrivateRelay.value == true) {
                showRelayDialog(privateRelaysKind) {
                    binding.addPrivateRelayUrl.error = null
                    relaysViewModel.newPrivateRelay.value?.let { it1 -> Log.d("Pokey", it1) }
                    insertRelay(relaysViewModel.newPrivateRelay.value!!, 10050)
                    binding.addPrivateRelayUrl.setText("")
                    privateRelaysView.adapter?.notifyDataSetChanged()
                }
            } else {
                binding.addPrivateRelayUrl.error = getString(R.string.invalid_uri)
            }
        }
        binding.publishPrivateRelay.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                context?.let { it1 -> NostrClient.publishPrivateRelays(it1) }
            }
        }
        binding.reloadPrivateRelay.setOnClickListener {
            Pokey.updateLoadingPrivateRelays(true)
            reconnectRelays(privateRelaysKind)
        }
        Pokey.loadingPrivateRelays.observe(viewLifecycleOwner) {
            if (it) {
                binding.privateRelaysLoading.visibility = View.VISIBLE
            } else {
                binding.privateRelaysLoading.visibility = View.GONE
            }
            loadRelays()
            privateRelaysView.adapter?.notifyDataSetChanged()
        }

        publicRelaysView = root.findViewById(R.id.public_relays)
        publicRelaysView.layoutManager = LinearLayoutManager(context)
        privateRelaysView = root.findViewById(R.id.private_relays)
        privateRelaysView.layoutManager = LinearLayoutManager(context)

        loadRelays()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun insertRelay(url: String, kind: Int) {
        lifecycleScope.launch {
            CoroutineScope(Dispatchers.IO).launch {
                context?.let { NostrClient.addRelay(it, url, kind) }
                loadRelays()
            }
        }
    }

    private fun reconnectRelays(kind: Int) {
        lifecycleScope.launch {
            CoroutineScope(Dispatchers.IO).launch {
                context?.let { NostrClient.reconnectInbox(it, kind) }
            }
        }
    }

    private fun loadRelays() {
        lifecycleScope.launch {
            val hexKey = Pokey.getInstance().getHexKey()
            val dao = context?.let { AppDatabase.getDatabase(it, hexKey).applicationDao() }
            if (dao != null) {
                publicList = withContext(Dispatchers.IO) { dao.getRelaysByKind(publicRelaysKind) }
                publicRelayAdapter = RelayListAdapter(publicList.filter { it.read == 1 }.toMutableList())
                publicRelaysView.adapter = publicRelayAdapter

                privateList = withContext(Dispatchers.IO) { dao.getRelaysByKind(privateRelaysKind) }
                privateRelayAdapter = RelayListAdapter(privateList.filter { it.read == 1 }.toMutableList())
                privateRelaysView.adapter = privateRelayAdapter
            }
        }
    }

    private fun showRelayDialog(kind: Int, confirmation: () -> Unit) {
        lifecycleScope.launch {
            val hexKey = Pokey.getInstance().getHexKey()
            val dao = context?.let { AppDatabase.getDatabase(it, hexKey).applicationDao() }
            if (dao != null) {
                val relayList = withContext(Dispatchers.IO) { dao.getRelaysByKind(kind) }
                if (relayList.size > 2) {
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle(R.string.recommend_relay_title)
                    builder.setMessage(R.string.recommend_relay_size)

                    builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, which: Int ->
                        confirmation()
                    }

                    builder.setNegativeButton(R.string.no) { dialog: DialogInterface, which: Int ->
                        dialog.dismiss()
                    }

                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                } else {
                    confirmation()
                }
            }
        }
    }
}
