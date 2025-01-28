package com.example.aes

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity2 : AppCompatActivity() {
    var total = 0.0
    private var detectedTag: Tag? = null
    private val itemButtons: MutableMap<String, Pair<Button, Int>> = mutableMapOf()
    private var nfcAdapter: NfcAdapter? = null
    private var isNfcOperationActive = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Handle any NFC intent that may have started this activity
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            println("NFC tag detected on activity start.")
        }

        val menuLayout: GridLayout = findViewById(R.id.menuLayout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        menuButton(menuLayout, "1", "1")
        menuButton(menuLayout, "5", "5")
        menuButton(menuLayout, "10", "10")
        menuButton(menuLayout, "20", "20")
        menuButton(menuLayout, "50", "50")
        menuButton(menuLayout, "100", "100")

        val button = findViewById<Button>(R.id.fundsInterfaceButton)
        button.setOnClickListener {
            val intent = Intent(this, MainActivity3::class.java)
            startActivity(intent)
        }
        val writeButton = findViewById<Button>(R.id.paymentButton)
        writeButton.setOnClickListener {
            val data = findViewById<TextView>(R.id.totalText)
            waitForNfcAndWrite(this, data.text.toString())
        }
        val readButton = findViewById<Button>(R.id.readNfcButton)
        readButton.setOnClickListener {
            waitForNfcAndRead(this)
        }
    }
    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }
    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }
    private fun enableNfcForegroundDispatch() {
        try {
            val intent = Intent(this, this.javaClass)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }
    private fun cleanupNfcOperation() {
        detectedTag = null // Reset the detected tag
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            println("NFC tag detected on new intent.")
        }
    }
    private fun menuButton(menuLayout: GridLayout, name: String, price: String) {
        // Creating the button
        val buttonDynamic = Button(this)

        // Setting layout_width and layout_height using GridLayout.LayoutParams
        val params = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f)
        ).apply {
            width = 0 // Allows flexibility for equal distribution
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setMargins(16, 16, 16, 16)
        }

        buttonDynamic.layoutParams = params

        buttonDynamic.text = "Add $price€"
        buttonDynamic.textSize = 20f

        // Setting rounded corners and ripple effect
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f // Set the corner radius
            setColor(Color.parseColor("#cdcdcd")) // Background color
        }

        val rippleColor = ColorStateList.valueOf(Color.parseColor("#dddddd")) // Ripple color
        buttonDynamic.background = RippleDrawable(rippleColor, drawable, null)

        // Setting onClick animation
        buttonDynamic.setOnClickListener {
            val priceValue = price.toDoubleOrNull() ?: 0.0
            total += priceValue
            val display_test: TextView = findViewById(R.id.totalText)
            display_test.text = total.toInt().toString()
            val orderLayout: LinearLayout = findViewById(R.id.orderSummaryLayout)
            orderButton(orderLayout, name, price)

            // Create the scale animation
            val scaleAnimation = ScaleAnimation(
                1f, 1.05f,  // Start and end scale for X axis
                1f, 1.05f,  // Start and end scale for Y axis
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,  // Pivot X
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f   // Pivot Y
            ).apply {
                duration = 150  // Duration of the expand phase
                fillAfter = true  // Keeps the button at the expanded size
            }

            // Create the contract animation
            val contractAnimation = ScaleAnimation(
                1.05f, 1f,  // Start and end scale for X axis
                1.05f, 1f,  // Start and end scale for Y axis
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,  // Pivot X
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f   // Pivot Y
            ).apply {
                duration = 150  // Duration of the contract phase
                fillAfter = true  // Keeps the button at the contracted size
                startOffset = 150  // Delay to start this after expand animation
            }

            // Apply both animations sequentially
            buttonDynamic.startAnimation(scaleAnimation)
            buttonDynamic.postDelayed({ buttonDynamic.startAnimation(contractAnimation) }, 150)
        }

        // Adding the button to the GridLayout
        menuLayout.addView(buttonDynamic)
    }
    private fun orderButton(menuLayout: LinearLayout, name: String, price: String) {
        // Check if the button for this item already exists
        var newCount = 1
        val priceValue = price.toInt() ?: 0
        if (itemButtons.containsKey(name)) {
            // Update the existing button's count and text
            val (existingButton, count) = itemButtons[name]!!
            newCount = count + 1
            val final = priceValue * newCount
            existingButton.text = "   x$newCount  $name€ - $final€"
            itemButtons[name] = existingButton to newCount
        } else {
            // Create a new button if it doesn't exist
            val buttonDynamic = Button(this)

            // Set layout parameters
            val params =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(4, 4, 4, 4)
                }
            buttonDynamic.layoutParams = params
            val final = priceValue * newCount
            buttonDynamic.text = "   x$newCount  $name€ - $final€"
            buttonDynamic.textSize = 20f
            buttonDynamic.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            buttonDynamic.gravity = Gravity.START or Gravity.CENTER_VERTICAL


            // Style the button
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor("#cdcdcd"))
            }
            val rippleColor = ColorStateList.valueOf(Color.parseColor("#dddddd"))
            buttonDynamic.background = RippleDrawable(rippleColor, drawable, null)

            // Add onClickListener to decrement or remove the button
            buttonDynamic.setOnClickListener {
                val (_, count) = itemButtons[name]!!
                val newCount = count - 1
                if (newCount > 0) {
                    val final = priceValue * newCount
                    buttonDynamic.text = "   x$newCount  $name - $final€"
                    itemButtons[name] = buttonDynamic to newCount
                } else {
                    menuLayout.removeView(buttonDynamic)
                    itemButtons.remove(name)
                }

                val priceValue = price.toDoubleOrNull() ?: 0.0
                total -= priceValue
                val displayTest: TextView = findViewById(R.id.totalText)
                displayTest.text = total.toInt().toString()
            }

            // Add the button to the layout and map
            menuLayout.addView(buttonDynamic)
            itemButtons[name] = buttonDynamic to 1
        }
    }
    fun waitForNfcAndWrite(context: Context, data: String) {
        isNfcOperationActive = true
        enableNfcForegroundDispatch()

        // Show a message to the user
        Toast.makeText(context, "Please place your NFC card within 5 seconds.", Toast.LENGTH_SHORT).show()

        val timeout = 5000L // 5 seconds
        val startTime = System.currentTimeMillis()

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (detectedTag != null) {

                    // Read data from NFC tag
                    readFromNfcTag(context, detectedTag)
                    // Safely access UI elements on the main thread
                    val nfcBalanceText = findViewById<TextView>(R.id.nfcBalanceText)

                    // Extract numeric values and calculate balance
                    try {
                        val dataBalance = data.filter { it.isDigit() }
                            .takeIf { it.isNotEmpty() }
                            ?.toInt() ?: 0

                        val nfcBalance = nfcBalanceText.text.toString()
                            .filter { it.isDigit() }
                            .takeIf { it.isNotEmpty() }
                            ?.toInt() ?: 0

                        // Debugging: Add Toast to check calculated balance
                        val balance = (dataBalance + nfcBalance).toString()

                        // Check for null detectedTag
                        if (detectedTag == null) {
                            Handler(Looper.getMainLooper()).post {
                                PaymentStatus(
                                    iconResId = R.drawable.ic_cross,
                                    title = "Failed!",
                                    message = "NFC detected as NULL",
                                    isSuccess =
                                    false // This makes the title text
                                    // green
                                )
                            }
                            return
                        }

                        // Attempt to write to NFC tag
                        val success = try {
                            writeToNfcTag(detectedTag!!, balance)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Error writing to NFC tag", Toast.LENGTH_SHORT).show()
                            }
                            false
                        }

                        // Show success or failure message
                        Handler(Looper.getMainLooper()).post {
                            if (success) {
                                PaymentStatus(
                                    iconResId = R.drawable.ic_tick,
                                    title = "Success!",
                                    message = "Data Written Successfully",
                                    isSuccess =
                                    true // This makes the title text
                                    // green
                                )
                            } else {
                                PaymentStatus(
                                    iconResId = R.drawable.ic_cross,
                                    title = "Failed!",
                                    message = "Failed to Write Data",
                                    isSuccess =
                                    false // This makes the title text
                                    // green
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }


                    // Stop NFC operation
                    cleanupNfcOperation()
                    return
                }

                // Check timeout for NFC tag detection
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < timeout) {
                    handler.postDelayed(this, 200) // Check every 200ms
                } else {
                    // Timeout: No NFC card detected
                    Handler(Looper.getMainLooper()).post {
                        PaymentStatus(
                            iconResId = R.drawable.ic_cross,
                            title = "Failed!",
                            message = "No NFC Card Detected",
                            isSuccess =
                            false // This makes the title text
                            // green
                        )
                    }
                    cleanupNfcOperation() // Stop NFC detection
                }
            }
        })
    }
    fun writeToNfcTag(tag: Tag, data: String): Boolean {
        try {
            // Convert the data to NDEF format
            val ndefMessage = NdefMessage(
                arrayOf(
                    NdefRecord.createTextRecord("en", data)
                )
            )

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable) {
                    ndef.writeNdefMessage(ndefMessage)
                    ndef.close()
                    return true // Successfully written
                } else {
                    ndef.close()
                    throw IllegalStateException("NFC tag is not writable.")
                }
            } else {
                val ndefFormatable = NdefFormatable.get(tag)
                if (ndefFormatable != null) {
                    ndefFormatable.connect()
                    ndefFormatable.format(ndefMessage)
                    ndefFormatable.close()
                    return true // Successfully formatted and written
                } else {
                    throw IllegalStateException("NFC tag does not support NDEF.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false // Writing failed
        }
    }
    fun waitForNfcAndRead(context: Context) {
        isNfcOperationActive = true
        enableNfcForegroundDispatch()
        Toast.makeText(context, "Please tap your NFC tag within 5 seconds.", Toast.LENGTH_SHORT).show()

        val timeout = 5000L // 5 seconds
        val startTime = System.currentTimeMillis()

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (detectedTag != null) {
                    readFromNfcTag(context, detectedTag)
                    cleanupNfcOperation() // Stop NFC detection
                    return
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < timeout) {
                    handler.postDelayed(this, 200) // Check every 200ms
                } else {
                    PaymentStatus(
                        iconResId = R.drawable.ic_cross,
                        title = "Failed!",
                        message = "No NFC Card Detected",
                        isSuccess =
                        false // This makes the title text
                        // green
                    )
                    cleanupNfcOperation() // Stop NFC detection
                }
            }
        })
    }
    fun readFromNfcTag(context: Context, tag: Tag?) {
        if (tag == null) {
            Toast.makeText(context, "No NFC tag detected.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Check if the tag supports NDEF
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage // Read the NDEF message from the tag
                ndef.close()

                if (ndefMessage != null) {
                    // Extract records from the NDEF message
                    val records = ndefMessage.records
                    val data = records.joinToString("\n") { record ->
                        String(record.payload)  // Convert the payload bytes to a string
                    }

                    // Display the data as a Toast
                    val nfcBalanceText = findViewById<TextView>(R.id.nfcBalanceText)
                    val finalData = data.filter{it.isDigit()}
                    nfcBalanceText.text = "NFC balance: €$finalData"
                } else {
                    Toast.makeText(context, "No NDEF message found on the tag.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "This tag does not support NDEF.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to read tag: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun PaymentStatus(iconResId: Int, title: String, message: String, isSuccess: Boolean) {
        // Inflate the custom layout
        val dialogView = layoutInflater.inflate(R.layout.payment_status_dialog, null)

        // Set the icon
        val statusIcon = dialogView.findViewById<ImageView>(R.id.statusIcon)
        statusIcon.setImageResource(iconResId)

        // Set the title
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        titleText.text = title

        // Change the color of the title text based on success or failure
        titleText.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSuccess) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            )
        )

        // Set the message
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        messageText.text = message

        // Update button background based on status
        val okButton = dialogView.findViewById<Button>(R.id.okButton)
        okButton.backgroundTintList =
            ContextCompat.getColorStateList(
                this,
                if (isSuccess) android.R.color.holo_green_light
                else android.R.color.holo_red_light
            )

        // Create and show the AlertDialog
        val paymentStatusDialog =
            AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        okButton.setOnClickListener { paymentStatusDialog.dismiss() }

        // Show dialog first
        paymentStatusDialog.show()

        // Get the display metrics
        val displayMetrics = resources.displayMetrics
        val dialogWidth =
            (displayMetrics.widthPixels * 0.35).toInt() // Reduced to 35% of screen width

        // Set fixed dimensions for the dialog
        paymentStatusDialog.window?.apply {
            setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setWindowAnimations(android.R.style.Animation_Dialog)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}