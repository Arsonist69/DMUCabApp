package com.example.mydmucabapp_driver.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.adopters.FaqsAdapter
import com.example.mydmucabapp_driver.databinding.FragmentFaqsBinding
import com.example.mydmucabapp_driver.model.DataClass.Faq

class FaqsFragment : Fragment() {

    private lateinit var binding: FragmentFaqsBinding
    private val FAQs = listOf<Faq>(
        Faq(question = "Question?", answer = "Answer"),
        Faq(question = "Question?", answer = "Answer"),
        Faq(question = "Question?", answer = "Answer"),
        Faq(question = "Question?", answer = "Answer")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFaqsBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.faqsRecycler.adapter = FaqsAdapter(FAQs,requireContext())
    }

}