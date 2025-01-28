package com.example.aes

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val jsonFile = File(filesDir, "products.json")
        if (!jsonFile.exists()) {
            // Copy the JSON file to internal storage if it doesn't exist
            copyJsonToInternalStorage()
        }
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val menuLayout: GridLayout = findViewById(R.id.menuLayout)
        read_json(menuLayout)

        val menuButton = findViewById<Button>(R.id.menuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, MainActivity3::class.java)
            startActivity(intent)
        }
        val fundsButton = findViewById<Button>(R.id.fundsButton)
        fundsButton.setOnClickListener {
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
        }
    }
    private fun menuButton(menuLayout: GridLayout, name: String, price: Int, sold: Int) {
        // Creating the button
        val buttonDynamic = Button(this)

        val params = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f)
        ).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setMargins(16, 16, 16, 16)
        }

        buttonDynamic.layoutParams = params
        val total = price*sold
        buttonDynamic.text = "name: $name\nprice: $price€\nunits sold: $sold\nearning: $total€"
        buttonDynamic.textSize = 16f

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(Color.parseColor("#cdcdcd"))
        }

        val rippleColor = ColorStateList.valueOf(Color.parseColor("#dddddd"))
        buttonDynamic.background = RippleDrawable(rippleColor, drawable, null)

        menuLayout.addView(buttonDynamic)
        Log.d("ButtonDebug", "Button added: $name with price $price")
    }
    fun read_json(menuLayout: GridLayout) {
        val file = File(filesDir, "products.json")
        try {
            if (file.exists()) {
                val json = file.readText() // Read the content of the file
                val jsonObject = JSONObject(json)

                jsonObject.keys().forEach { key ->
                    val productArray = jsonObject.getJSONArray(key)
                    val price = productArray.getInt(0) // Get the price (0th index)
                    val sold = productArray.getInt(1)

                    Log.d("ReadJson", "Product: $key, Price: $price")

                    // Remove existing button with the same name (if it exists)
                    val existingButton = findButtonByName(menuLayout, key, price)
                    existingButton?.let { menuLayout.removeView(it) }

                    // Add a button for this product
                    menuButton(menuLayout, key, price,sold)
                }
            } else {
                Log.e("JSON", "products.json not found!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ReadJson", "Error reading JSON: ${e.message}")
        }
    }
    fun findButtonByName(menuLayout: GridLayout, name: String, price: Int): Button? {
        for (i in 0 until menuLayout.childCount) {
            val child = menuLayout.getChildAt(i)
            if (child is Button && child.text == "$name\n$price€") {
                return child
            }
        }
        return null
    }
    fun copyJsonToInternalStorage() {
        try {
            // Get the input stream from assets folder
            val inputStream: InputStream = assets.open("products.json")

            // Get the output file in internal storage
            val outFile = File(filesDir, "products.json")
            val outputStream: OutputStream = FileOutputStream(outFile)

            // Copy the content of the file
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            Log.d("CopyJson", "Successfully copied JSON file to internal storage.")
        } catch (e: Exception) {
            Log.e("CopyJson", "Error copying JSON to internal storage: ${e.message}")
        }
    }

}
