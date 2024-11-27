package com.koalasat.pokey.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.databinding.FragmentHomeBinding
import com.koalasat.pokey.models.ExternalSigner
import com.koalasat.pokey.utils.images.CircleTransform
import com.squareup.picasso.Picasso

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.npubInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateNpubInput(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.serviceStart.setOnClickListener {
            if (viewModel.serviceStart.value == true) {
                viewModel.updateServiceStart(false)
            } else {
                if (viewModel.validationResult.value == true) {
                    viewModel.updateServiceStart(true)
                    binding.npubInput.error = null
                } else {
                    binding.npubInput.error = getString(R.string.invalid_npub)
                }
            }
        }

        viewModel.avatar.observe(viewLifecycleOwner) { value ->
            if (value.isNotEmpty()) {
                Picasso.get()
                    .load(value)
                    .resize(512, 512)
                    .centerCrop()
                    .transform(CircleTransform())
                    .into(binding.avatar)
            }
        }

        viewModel.npubInput.observe(viewLifecycleOwner) { value ->
            if (binding.npubInput.text.toString() != value) {
                binding.npubInput.setText(value)
            }
        }

        Pokey.isEnabled.observe(viewLifecycleOwner) {
            if (it) {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorButtonNormal, typedValue, true)
                binding.serviceStart.text = getString(R.string.stop)
                binding.amber.isEnabled = false
                binding.npubInput.isEnabled = false
            } else {
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                binding.serviceStart.text = getString(R.string.start)
                binding.amber.isEnabled = true
                binding.npubInput.isEnabled = true
            }
        }

        binding.amber.setOnClickListener {
            ExternalSigner.savePubKey()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
