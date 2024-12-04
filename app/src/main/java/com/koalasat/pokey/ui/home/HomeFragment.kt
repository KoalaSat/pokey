package com.koalasat.pokey.ui.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.ViewModelProvider
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.UserEntity
import com.koalasat.pokey.databinding.FragmentHomeBinding
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.ExternalSigner
import com.koalasat.pokey.utils.images.CircleTransform
import com.koalasat.pokey.utils.isDarkThemeEnabled
import com.squareup.picasso.Picasso
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.uriToRoute
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        homeViewModel.accountList.observeForever { value ->
            binding.serviceStart.isEnabled = value?.isNotEmpty() == true
            value.forEach { user ->
                val imageWithTextLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = GridLayout.LayoutParams().apply {
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        width = 0
                        setMargins(4, 4, 4, 4)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                }

                val imageView = ImageView(requireContext()).apply {
                    id = user.hexPub.hashCode()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90f, context.resources.displayMetrics).toInt(),
                    )
                    contentDescription = context.getString(R.string.logo)
                    setImageResource(R.mipmap.ic_launcher)
                }

                val textView = TextView(requireContext()).apply {
                    text = if (user.name?.isNotEmpty() == true) {
                        user.name?.substring(0, 10) + "..."
                    } else {
                        Hex.decode(user.hexPub).toNpub().substring(0, 10) + "..."
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    gravity = Gravity.CENTER
                }

                loadAvatar(user.avatar.toString(), imageView)

                imageWithTextLayout.addView(imageView)
                imageWithTextLayout.addView(textView)

                binding.accountList.addView(imageWithTextLayout, 0)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addAccount.setOnClickListener {
            showAddAccountDialog()
        }

        binding.serviceStart.setOnClickListener {
            viewModel.updateServiceStart(!viewModel.serviceStart.value!!)
        }

        Pokey.isEnabled.observe(viewLifecycleOwner) {
            if (it) {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorButtonNormal, typedValue, true)
                binding.serviceStart.text = getString(R.string.stop)
                updateAddAccountButton(R.color.grey)
            } else {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                binding.serviceStart.text = getString(R.string.start)
                val color = if (isDarkThemeEnabled(requireContext())) R.color.purple_200 else R.color.purple_500
                updateAddAccountButton(color)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAddAccountDialog() {
        if (Pokey.isEnabled.value == true) return

        val inflater = LayoutInflater.from(requireContext())
        val dialogView: View = inflater.inflate(R.layout.fragment_add_account, null)

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()

        val npubInput: EditText = dialogView.findViewById(R.id.npub_input)
        val buttonAmber: Button = dialogView.findViewById(R.id.amber)
        val buttonSubmitAccount: Button = dialogView.findViewById(R.id.submitAccount)

        buttonAmber.setOnClickListener {
            ExternalSigner.savePubKey {
                dialog.hide()
                createUser(it, 1)
            }
        }

        buttonSubmitAccount.setOnClickListener {
            val hexPub = parseNpubInput(npubInput.text.toString())
            if (hexPub?.isNotEmpty() == true) {
                npubInput.error = null
                dialog.hide()
                createUser(hexPub, 0)
            } else {
                npubInput.error = getString(R.string.invalid_npub)
            }
        }

        dialog.show()
    }

    private fun parseNpubInput(value: String): String? {
        if (value.isEmpty()) return null

        val parseReturn = uriToRoute(value)

        when (val parsed = parseReturn?.entity) {
            is Nip19Bech32.NPub -> {
                return parsed.hex
            }
        }

        return null
    }

    private fun loadAvatar(avatar: String?, imageView: ImageView) {
        if (avatar?.isNotEmpty() != true) return

        Picasso.get()
            .load(avatar)
            .resize(512, 512)
            .centerCrop()
            .transform(CircleTransform())
            .into(imageView)
    }

    private fun createUser(hexPukKey: String?, signer: Int) {
        if (hexPukKey?.isNotEmpty() != true) return

        CoroutineScope(Dispatchers.IO).launch {
            val dao = context?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            dao?.insertUser(
                UserEntity(
                    id = 0,
                    hexPub = hexPukKey.toString(),
                    name = "",
                    avatar = "",
                    createdAt = 0,
                    signer = signer,
                ),
            )

            if (EncryptedStorage.inboxPubKey.value?.isNotEmpty() != true) EncryptedStorage.updateInboxPubKey(hexPukKey.toString())

            viewModel.loadAccouts()
        }
    }

    private fun updateAddAccountButton(color: Int) {
        binding.addAccount.compoundDrawables[1]?.setColorFilter(
            ContextCompat.getColor(requireContext(), color),
            PorterDuff.Mode.SRC_IN,
        )
    }
}
