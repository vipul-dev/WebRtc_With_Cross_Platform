package com.dev.recordifiedwebrtc

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.permissionx.guolindev.PermissionX
import java.util.Random

class MainActivity : AppCompatActivity() {

    val EXTRA_MESSAGE = "com.dev.recordifiedwebrtc.ROOM_ID"
    private var roomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnJoin = findViewById<Button>(R.id.btnJoin)
        val btnRandom = findViewById<Button>(R.id.btnRandom)
        val roomIDText = findViewById<EditText>(R.id.roomIDText)

        roomId = generateRandomString(100000, 999999)
        roomIDText.setText(roomId)

        btnJoin.setOnClickListener { v: View? ->
            PermissionX.init(this)
                .permissions(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
                ).request { allgranted, _, _ ->
                    if (allgranted) {
                        startActivity(Intent(this@MainActivity, CallActivity::class.java).apply {
                            putExtra(EXTRA_MESSAGE, roomId)
                        })
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "you should accept all permissions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }


        }

        btnRandom.setOnClickListener { v: View? ->
            roomId = generateRandomString(100000, 999999)
            roomIDText.setText(roomId)
        }

        roomIDText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
                roomId = s.toString()
            }
        })
    }

    private fun generateRandomString(min: Int, max: Int): String {
        val random = Random().nextInt(max - min + 1)
        return random.toString()
    }
}