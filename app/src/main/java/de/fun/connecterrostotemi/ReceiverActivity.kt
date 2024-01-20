package de.`fun`.connecterrostotemi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import de.`fun`.connecterrostotemi.databinding.ActivityRevieverBinding
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import kotlin.concurrent.thread


class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRevieverBinding

    private val mapOfQuestionsAndAnswers = mutableMapOf<String, String>()

    private val permissions = arrayOf(
        Manifest.permission.INTERNET,
    )

    var x = 0.0f
    var y = 0.0f

    fun unpackVec2(data: ByteArray): Pair<Float, Float> {
        val float1 = ByteArray(4)
        val float2 = ByteArray(4)

        System.arraycopy(data, 0, float1, 0, 4)

        System.arraycopy(data, 4, float2, 0, 4)

        val value1 = ByteBuffer.wrap(float1).float
        val value2 = ByteBuffer.wrap(float2).float

        return Pair(value1, value2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRevieverBinding.inflate(layoutInflater)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(binding.root)

        val robot = Robot.getInstance()

        binding.btnAnswers.setOnClickListener {
            var text = ""
            Log.i("Saying Text", "Map: $mapOfQuestionsAndAnswers")
            for (question in mapOfQuestionsAndAnswers.keys) {
                text += "$question, ${mapOfQuestionsAndAnswers[question]}\n"
            }
            Handler(Looper.getMainLooper()).post {
                robot.speak(
                    TtsRequest.create(text)
                )
            }

        }

        if (!hasPermissions(this, *permissions)) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }

        thread {
            try {
                val soc = DatagramSocket(12345)
                Log.i("Receiving Controls", "Socket created")
                soc.use { socket ->
                    while (true) {
                        val buffer = ByteArray(8)

                        val packet = DatagramPacket(buffer, buffer.size)

                        socket.receive(packet)
                        val vec2 = unpackVec2(buffer)
                        x = vec2.first
                        y = vec2.second
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        thread {
            try {
                // Create a UDP socket
                val soc = DatagramSocket(12346)
                Log.i("Reviecing Q & A", "Socket created")
                soc.use { socket ->
                    while (true) {
                        try {
                            // recieve a string
                            val buffer = ByteArray(8196)
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val receivedString = String(packet.data, 0, packet.length)
                            val jsonObject = JSONObject(receivedString)
                            val question = jsonObject.getString("question")
                            val answer = jsonObject.getString("answer")
                            mapOfQuestionsAndAnswers[question] = answer
                            binding.question.text = question
                            binding.answer.text = answer
                        } catch (e: Exception) {
                            Log.e("Can't Parse Q & A", "Error: $e")
                        }

                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        thread {
            try {
                while (true) {
                    Thread.sleep(50)

                    Handler(Looper.getMainLooper()).post {
                        robot.skidJoy(
                            x,
                            y,
                            false
                        )
                        // Updating trajectory view
                        val layoutParams =
                            binding.stick.layoutParams as ConstraintLayout.LayoutParams
                        layoutParams.horizontalBias = (y + 1) / 2
                        layoutParams.verticalBias = (x + 1) / 2
                        binding.stick.layoutParams = layoutParams
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
        for (permission in permissions) {
            context?.let {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

}