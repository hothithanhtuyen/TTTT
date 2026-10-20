package com.example.doan.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.example.doan.ui.cinema.CinemaApp
import com.example.doan.ui.cinema.CinemaTheme
import com.example.doan.BuildConfig
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.FIREBASE_AUTH_EMULATOR_HOST.isNotBlank()) {
            FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_AUTH_EMULATOR_HOST, 9099)
        }
        setContent {
            CinemaTheme {
                CinemaApp()
            }
        }
    }
}
