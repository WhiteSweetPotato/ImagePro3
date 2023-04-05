package com.example.imagepro3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.imagepro3.CameraActivity
import com.example.imagepro3.R
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    private var camera_button: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        camera_button = findViewById<Button>(R.id.camera_button)
        camera_button!!.setOnClickListener(View.OnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    CameraActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        })
    }

    companion object {
        init {
            if (OpenCVLoader.initDebug()) {
                Log.d("MainActivity: ", "Opencv is loaded")
            } else {
                Log.d("MainActivity: ", "Opencv failed to load")
            }
        }
    }
}