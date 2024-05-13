package com.example.mydmucabapp_driver.fragment

import android.R.attr.password
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mydmucabapp_driver.controller.LoginActivity
import com.example.mydmucabapp_driver.databinding.FragmentUserDetailsBinding
import com.example.mydmucabapp_driver.databinding.LayoutUpdateEmailBinding
import com.example.mydmucabapp_driver.databinding.LayoutUpdatePassBinding
import com.example.mydmucabapp_driver.helpers.Validator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging


class UserDetailsFragment : Fragment() {

    private lateinit var binding: FragmentUserDetailsBinding
    private var UID = ""
    private var EMAIL = ""
    private var NAME = ""
    private var PHONE = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentUserDetailsBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UID = FirebaseAuth.getInstance().currentUser?.uid.toString()

        val ref = FirebaseFirestore.getInstance()

        ref.collection("users").document(UID).get().addOnSuccessListener {
            if (it != null){
                EMAIL = it.get("email").toString()
                PHONE = it.get("phone").toString()
                NAME = it.get("name").toString()
                val vReg = it.get("vehicleRegistration").toString()
                val vColor = it.get("vehicleColor").toString()
                val vModel = it.get("vehicleModel").toString()
                binding.edtName.setText(NAME)
                binding.edtVehicleReg.setText(vReg)
                binding.edtVehicleColor.setText(vColor)
                binding.edtVehicleModel.setText(vModel)
                binding.txtEmail.setText(EMAIL)
                binding.txtPhone.setText(PHONE)

            }
        }

        binding.btnUpdate.setOnClickListener {
            val name = binding.edtName.text.toString().trim()
            val vReg = binding.edtVehicleReg.text.toString().trim()
            val vModel = binding.edtVehicleModel.text.toString().trim()
            val vColor = binding.edtVehicleColor.text.toString().trim()

            // Validation
            if (!Validator.isValidFullName(name)) {
                Toast.makeText(requireContext(), "Invalid Full Name (up to 15 characters)", Toast.LENGTH_SHORT).show()
            } else if (vReg.isEmpty() || vModel.isEmpty() || vColor.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all vehicle details", Toast.LENGTH_SHORT).show()
            } else {
                // Update user details
                binding.progressBar.visibility = View.VISIBLE
                binding.btnUpdate.visibility = View.GONE
                val updates = mapOf(
                    "name" to name,
                    "vehicleRegistration" to vReg,
                    "vehicleModel" to vModel,
                    "vehicleColor" to vColor
                )
                val ref = FirebaseFirestore.getInstance()
                ref.collection("users").document(UID).update(updates).addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdate.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Updated Successfully!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdate.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnChangeEmail.setOnClickListener {
            showEmailUpdateSheet(requireContext())
        }
        binding.btnChangePass.setOnClickListener {
            showPasswordUpdateSheet(requireContext())
        }
    }

    private fun showEmailUpdateSheet(context: Context){
        val layoutBinding = LayoutUpdateEmailBinding.inflate(LayoutInflater.from(context))
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(layoutBinding.root)
        sheet.setCancelable(false)

        var email = ""

        layoutBinding.btnCancel.setOnClickListener {
            sheet.dismiss()
        }
        layoutBinding.btnUpdate.setOnClickListener {
            email = layoutBinding.edtNewEmail.text.toString().trim()
            if (!Validator.isValidEmail(email)) {
                Toast.makeText(requireContext(), "Invalid Email Address (e.g., name@my365.dmu.ac.uk)", Toast.LENGTH_SHORT).show()
            }else{
                layoutBinding.txtTitle.text = "Confirm Password"
                layoutBinding.edtNewEmail.visibility = View.GONE
                layoutBinding.edtPass.visibility = View.VISIBLE
                layoutBinding.btnVerify.visibility = View.VISIBLE
                layoutBinding.btnUpdate.visibility = View.GONE
            }
        }
        layoutBinding.btnVerify.setOnClickListener {
            val pass = layoutBinding.edtPass.text.toString().trim()
            if (!pass.isNullOrEmpty()){
                layoutBinding.progressBar.visibility = View.VISIBLE
                layoutBinding.buttonsLayout.visibility = View.GONE

                val user = FirebaseAuth.getInstance().currentUser
                val credential = EmailAuthProvider.getCredential(EMAIL, pass) // Current Login Credentials
                user!!.reauthenticate(credential).addOnSuccessListener {
                    val cUser = FirebaseAuth.getInstance().currentUser
                    cUser!!.updateEmail(email).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            FirebaseFirestore.getInstance().collection("users").document(UID).update("email",email)
                            layoutBinding.progressBar.visibility = View.GONE
                            layoutBinding.buttonsLayout.visibility = View.VISIBLE
                            Toast.makeText(
                                context,
                                "Email Updated Successfully, Login Again!",
                                Toast.LENGTH_LONG
                            ).show()
                            sheet.dismiss()
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(UID)
                            FirebaseAuth.getInstance().signOut()
                            Intent(context,LoginActivity::class.java).also {
                                startActivity(it)
                                requireActivity().finishAffinity()
                            }
                        }
                    }.addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Error: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        layoutBinding.progressBar.visibility = View.GONE
                        layoutBinding.buttonsLayout.visibility = View.VISIBLE
                    }
                }.addOnFailureListener {
                    Toast.makeText(
                        context,
                        "Error: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    layoutBinding.progressBar.visibility = View.GONE
                    layoutBinding.buttonsLayout.visibility = View.VISIBLE
                }
            }else{
                Toast.makeText(context, "Please Enter Password!", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun showPasswordUpdateSheet(context: Context){
        val layoutBinding = LayoutUpdatePassBinding.inflate(LayoutInflater.from(context))
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(layoutBinding.root)
        sheet.setCancelable(false)

        layoutBinding.btnCancel.setOnClickListener {
            sheet.dismiss()
        }
        layoutBinding.btnUpdate.setOnClickListener {
            val currentPass = layoutBinding.edtCurrentPass.text.toString().trim()
            val newPass = layoutBinding.edtNewPass.text.toString().trim()
            val confirmPass = layoutBinding.edtConfirmPassword.text.toString().trim()
            if (currentPass.isEmpty()){
                Toast.makeText(context, "Please Enter Current Password!", Toast.LENGTH_SHORT).show()
            }else{
                if (!Validator.isStrongPassword(newPass)) {
                    Toast.makeText(requireContext(), "Invalid New Password (at least 7 characters with letters and numbers)", Toast.LENGTH_LONG).show()
                }else if (newPass != confirmPass){
                    Toast.makeText(context, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                }else{
                    layoutBinding.progressBar.visibility = View.VISIBLE
                    layoutBinding.buttonsLayout.visibility = View.GONE
                    val user = FirebaseAuth.getInstance().currentUser
                    val credential = EmailAuthProvider.getCredential(EMAIL, currentPass) // Current Login Credentials
                    user!!.reauthenticate(credential).addOnSuccessListener {
                        FirebaseAuth.getInstance().currentUser?.updatePassword(newPass)!!.addOnSuccessListener {
                            layoutBinding.progressBar.visibility = View.GONE
                            layoutBinding.buttonsLayout.visibility = View.VISIBLE
                            Toast.makeText(
                                context,
                                "Password Updated Successfully, Login Again!",
                                Toast.LENGTH_LONG
                            ).show()
                            sheet.dismiss()
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(UID)
                            FirebaseAuth.getInstance().signOut()
                            Intent(context,LoginActivity::class.java).also {
                                startActivity(it)
                                requireActivity().finishAffinity()
                            }
                        }.addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Error: ${it.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            layoutBinding.progressBar.visibility = View.GONE
                            layoutBinding.buttonsLayout.visibility = View.VISIBLE
                        }
                    }.addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Current Password is not correct!",
                            Toast.LENGTH_LONG
                        ).show()
                        layoutBinding.progressBar.visibility = View.GONE
                        layoutBinding.buttonsLayout.visibility = View.VISIBLE
                    }
                }
            }
        }

        sheet.show()
    }

}