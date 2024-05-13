package com.example.mydmucabapp_driver.helpers

class Validator {
    companion object {

        fun isValidFullName(fullName: String): Boolean {
            val regex = "^[a-zA-Z\\s'-]{1,15}\$"
            return fullName.matches(Regex(regex))
        }

        fun isValidPhoneNumber(phoneNumber: String): Boolean {
            val regex = """^(?:\+44|0)7\d{9}$""".toRegex()
            return phoneNumber.matches(regex)

        }

        fun isValidEmail(email: String): Boolean {
            val pattern = "^[A-Za-z0-9+_.-]+@my365.dmu.ac.uk$"
            return email.matches(Regex(pattern))
        }

        fun isStrongPassword(password: String): Boolean {
            val passwordPattern = "^(?=.*[A-Za-z])(?=.*\\d).{7,}$"
            return password.matches(Regex(passwordPattern))
        }


    }
}