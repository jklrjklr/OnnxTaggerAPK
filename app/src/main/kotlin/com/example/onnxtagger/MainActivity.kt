package com.example.onnxtagger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.onnxtagger.ui.screen.MainScreen
import com.example.onnxtagger.ui.theme.OnnxTaggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnnxTaggerTheme {
                MainScreen()
            }
        }
    }
}
