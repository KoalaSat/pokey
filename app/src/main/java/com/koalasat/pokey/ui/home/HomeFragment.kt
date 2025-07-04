package com.koalasat.pokey.ui.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.size
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
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.utils.images.CircleTransform
import com.koalasat.pokey.utils.isDarkThemeEnabled
import com.squareup.picasso.Picasso
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    @SuppressLint("SetTextI18n", "ResourceAsColor")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        homeViewModel.accountList.observeForever { value ->
            if (_binding != null) {
                if (binding.accountList.size > 1) {
                    binding.accountList.removeViews(0, binding.accountList.size - 1)
                }
                value.reversed().forEach { user ->
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
                            user.name
                        } else {
                            Hex.decode(user.hexPub).toNpub().substring(0, 10) + "..."
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        gravity = Gravity.CENTER
                    }

                    imageView.setOnClickListener { view ->
                        if (Pokey.isEnabled.value != true) {
                            showUserPopupMenu(view, user)
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.stopPokey), Toast.LENGTH_SHORT).show()
                        }
                    }

                    loadAvatar(user.avatar.toString(), imageView)

                    imageWithTextLayout.addView(imageView)
                    imageWithTextLayout.addView(textView)

                    binding.accountList.addView(imageWithTextLayout, 0)
                }
            }
        }

        homeViewModel.subscription.observeForever { value ->
            if (_binding != null) {
                if (value.isNotEmpty()) {
                    val text = if (value.length > 10) {
                        value?.substring(0, 10) + "..."
                    } else {
                        value
                    }
                    binding.addSubscription.text = text
                } else {
                    binding.addSubscription.visibility = View.VISIBLE
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addAccount.setOnClickListener {
            showAddAccountDialog()
        }

        binding.addSubscription.setOnClickListener {
            showAddASubscriptionDialog()
        }

        binding.serviceStart.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
                val users = dao.getUsers()
                val subscription = EncryptedStorage.inboxSubscription.value
                if ((users.isNotEmpty() || subscription?.isNotEmpty() == true) || viewModel.serviceStart.value == true) {
                    viewModel.updateServiceStart(!viewModel.serviceStart.value!!)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.addUsersToStart), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Pokey.isEnabled.observe(viewLifecycleOwner) {
            if (it) {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorButtonNormal, typedValue, true)
                binding.serviceStart.text = getString(R.string.stop)
                updateAddButtons(R.color.grey)
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = AppDatabase.getDatabase(requireContext(), "common").applicationDao()
                    for (user in dao.getUsers()) {
                        loadUser(user)
                    }
                }
            } else {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                binding.serviceStart.text = getString(R.string.start)
                val color = if (isDarkThemeEnabled(requireContext())) R.color.purple_200 else R.color.purple_500
                updateAddButtons(color)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAddAccountDialog() {
        if (Pokey.isEnabled.value == true) {
            Toast.makeText(requireContext(), getString(R.string.stopPokey), Toast.LENGTH_SHORT).show()
        } else {
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
                    createUser(it.toString(), 1)
                }
            }

            buttonSubmitAccount.setOnClickListener {
                val hexPub = NostrClient.parseNpub(npubInput.text.toString())
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
    }

    private fun showAddASubscriptionDialog() {
        if (Pokey.isEnabled.value == true) {
            Toast.makeText(requireContext(), getString(R.string.stopPokey), Toast.LENGTH_SHORT).show()
        } else {
            val inflater = LayoutInflater.from(requireContext())
            val dialogView: View = inflater.inflate(R.layout.fragment_add_subscription, null)

            val builder = AlertDialog.Builder(requireContext())
            builder.setView(dialogView)
            val dialog = builder.create()

            val subscriptionInput: EditText = dialogView.findViewById(R.id.subscription_input)
            val buttonSubmitSubscription: Button = dialogView.findViewById(R.id.submitSubscription)
            val buttonDeleteSubscription: Button = dialogView.findViewById(R.id.deleteSubscription)
            val helpButton: ImageButton = dialogView.findViewById(R.id.help_button)

            helpButton.setOnClickListener {
                Toast.makeText(requireContext(), getString(R.string.subscription_hint), Toast.LENGTH_LONG).show()
            }

            buttonSubmitSubscription.setOnClickListener {
                val input = subscriptionInput.text.toString()
                val (type, result) = when {
                    input.startsWith("npub", ignoreCase = true) -> NostrClient.parseBech32(input)
                    input.startsWith("nevent", ignoreCase = true) -> NostrClient.parseBech32(input)
                    input.startsWith("#") -> Pair("hashtag", input)
                    else -> Pair(null, null)
                }

                if (type != null && result != null) {
                    subscriptionInput.error = null
                    dialog.hide()
                    createSubscription(input)
                } else {
                    subscriptionInput.error = getString(R.string.invalid_input)
                }
            }
            buttonDeleteSubscription.setOnClickListener {
                EncryptedStorage.updateInboxSubscription("")
                binding.addSubscription.text = getString(R.string.create_subscription)
                dialog.hide()
            }

            dialog.show()
        }
    }

    private fun loadAvatar(avatar: String?, imageView: ImageView) {
        if (avatar?.isNotEmpty() != true) return

        Picasso.get()
            .load(avatar)
            .resize(512, 512)
            .centerCrop()
            .transform(CircleTransform())
            .error(R.mipmap.ic_launcher)
            .into(imageView)
    }

    private fun createUser(hexPukKey: String?, signer: Int) {
        if (hexPukKey?.isNotEmpty() != true) return

        CoroutineScope(Dispatchers.IO).launch {
            val dao = context?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            val user = UserEntity(
                id = 0,
                hexPub = hexPukKey.toString(),
                name = "",
                avatar = "",
                createdAt = 0,
                signer = signer,
            )
            dao?.insertUser(user)

            if (EncryptedStorage.inboxPubKey.value?.isNotEmpty() != true) EncryptedStorage.updateInboxPubKey(hexPukKey.toString())
            if (EncryptedStorage.mutePubKey.value?.isNotEmpty() != true) EncryptedStorage.updateMutePubKey(hexPukKey.toString())

            viewModel.loadAccounts()

            loadUser(user)
        }
    }

    private fun createSubscription(value: String) {
        if (value.isNotEmpty() != true) return

        CoroutineScope(Dispatchers.IO).launch {
            EncryptedStorage.updateInboxSubscription(value)
        }
    }

    private fun updateAddButtons(color: Int) {
        binding.addAccount.compoundDrawables[1]?.setColorFilter(
            ContextCompat.getColor(requireContext(), color),
            PorterDuff.Mode.SRC_IN,
        )
        binding.addSubscription.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
    }

    private fun showUserPopupMenu(view: View, user: UserEntity) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.user_popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_delete -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = context?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
                        dao?.deleteUser(user)
                        var users = dao?.getUsers()
                        if (users?.size!! > 0 && EncryptedStorage.inboxPubKey.value == user.hexPub) {
                            var nextUser = users.first()
                            EncryptedStorage.updateInboxPubKey(nextUser.hexPub)
                            EncryptedStorage.updateMutePubKey(nextUser.hexPub)
                        }
                        viewModel.loadAccounts()
                    }
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun loadUser(user: UserEntity) {
        context?.let { context ->
            NostrClient.getNip05Content(user.hexPub, context, onResponse = { profile ->
                try {
                    user.name = profile?.getString("name") ?: user.name
                    user.avatar = profile?.getString("picture") ?: user.avatar
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDatabase.getDatabase(context, "common").applicationDao()
                        dao.updateUser(user)
                        viewModel.loadAccounts()
                    }
                } catch (e: JSONException) { }
            })
        }
    }
}
