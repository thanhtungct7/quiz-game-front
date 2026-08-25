package com.kma.quiz_game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kma.quiz_game.ui.navigation.DuoNavHost
import com.kma.quiz_game.ui.theme.Quiz_gameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Quiz_gameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DuoNavHost()
                }
            }
        }
    }
}
