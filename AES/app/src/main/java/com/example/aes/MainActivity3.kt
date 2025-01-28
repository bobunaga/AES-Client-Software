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
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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
import org.json.JSONArray
import java.io.File
import org.json.JSONObject

class MainActivity3 : AppCompatActivity() {
    private val orderList = mutableListOf<String>()
    var total = 0
    var isAddButtonAdded = false // Track if "+" button has been added
    var isAddButtonRemoved = false // Track if "+" button has been removed
    private val itemButtons: MutableMap<String, Pair<Button, Int>> = mutableMapOf()
    private var nfcAdapter: NfcAdapter? = null
    private var isNfcOperationActive = false
    private var detectedTag: Tag? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main3)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            println("NFC tag detected on activity start.")
        }

        val menuLayout: GridLayout = findViewById(R.id.menuLayout)
        read_json(menuLayout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val button = findViewById<Button>(R.id.fundsInterfaceButton)
        button.setOnClickListener {
            showPasswordDialog {
                val intent = Intent(this, MainActivity2::class.java)
                startActivity(intent)
            }
        }
        val editButton = findViewById<Button>(R.id.editButton)
        editButton.setOnClickListener { showPasswordDialog { addActionButtonsToGrid(menuLayout) } }
        val writeButton = findViewById<Button>(R.id.paymentButton)
        writeButton.setOnClickListener {
            val data = findViewById<TextView>(R.id.totalText)
            waitForNfcAndWrite(this, data.text.toString())
        }
        val readButton = findViewById<Button>(R.id.readNfcButton)
        readButton.setOnClickListener { waitForNfcAndRead(this) }
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
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
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
    private fun showPasswordDialog(onSuccess: () -> Unit) {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Enter Password")
        builder.setMessage("Please enter the password to proceed:")

        // Create an EditText for password input
        val input =
            EditText(this).apply {
                hint = "Password" // Hint for the EditText
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_PASSWORD // Password input type
            }

        // Add the EditText to the dialog
        builder.setView(input)

        // Set positive button (OK) action
        builder.setPositiveButton("OK") { dialog, _ ->
            val enteredPassword = input.text.toString()
            val correctPassword = "1" // Replace with the actual password

            if (enteredPassword == correctPassword) {
                dialog.dismiss()
                onSuccess() // Execute the function if the password is correct
            } else {
                // Show error dialog if password is wrong
                dialog.dismiss()
                showErrorDialog("Incorrect Password")
            }
        }

        // Set negative button (Cancel) action
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss() // Dismiss the dialog
        }

        // Show the dialog
        builder.create().show()
    }
    private fun showErrorDialog(message: String) {
        // Create an AlertDialog for showing errors
        val errorDialog =
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss() // Dismiss the error dialog
                }
                .create()

        // Show the error dialog
        errorDialog.show()
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
    private fun menuButton(menuLayout: GridLayout, name: String, price: Int) {
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
        buttonDynamic.text = "$name\n$price€"
        buttonDynamic.textSize = 16f

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(Color.parseColor("#cdcdcd"))
        }

        val rippleColor = ColorStateList.valueOf(Color.parseColor("#dddddd"))
        buttonDynamic.background = RippleDrawable(rippleColor, drawable, null)

        buttonDynamic.setOnClickListener {
            total += price
            val displayTest: TextView = findViewById(R.id.totalText)
            displayTest.text = "€$total"
            val orderLayout: LinearLayout = findViewById(R.id.orderSummaryLayout)
            orderButton(orderLayout, name, price)
        }

        menuLayout.addView(buttonDynamic)
        Log.d("ButtonDebug", "Button added: $name with price $price")
    }
    private fun orderButton(menuLayout: LinearLayout, name: String, price: Int) {
        // Check if the button for this item already exists
        var newCount = 1
        val priceValue = price
        if (itemButtons.containsKey(name)) {
            // Update the existing button's count and text
            val (existingButton, count) = itemButtons[name]!!
            newCount = count + 1
            val final = priceValue * newCount
            existingButton.text = "   x$newCount  $name - $final€"
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
            buttonDynamic.text = "   x$newCount  $name - $final€"
            buttonDynamic.textSize = 20f
            buttonDynamic.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            buttonDynamic.gravity = Gravity.START or Gravity.CENTER_VERTICAL

            // Style the button
            val drawable =
                GradientDrawable().apply {
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

                total -= priceValue
                val displayTest: TextView = findViewById(R.id.totalText)
                var displayTotal = total.toString()
                displayTest.text = "€$displayTotal"
            }

            // Add the button to the layout and map
            menuLayout.addView(buttonDynamic)
            itemButtons[name] = buttonDynamic to 1
        }
    }
    fun addActionButtonsToGrid(menuLayout: GridLayout) {
        val orderLayout: LinearLayout = findViewById(R.id.orderSummaryLayout)
        orderLayout.removeAllViews()
        itemButtons.clear()
        total = 0
        val displayTest: TextView = findViewById(R.id.totalText)
        var displayTotal = total.toString()
        displayTest.text = "€$displayTotal"

        // Collect the original buttons
        val originalButtons = mutableListOf<View>()

        for (i in 0 until menuLayout.childCount) {
            val child = menuLayout.getChildAt(i)

            // Reset states
            child.isPressed = false
            child.isFocusable = false
            child.clearAnimation()
            child.refreshDrawableState()
            child.invalidate()
            originalButtons.add(child)
        }

        // Clear the GridLayout (but retain the original buttons)
        menuLayout.removeAllViews()

        // Add each original button with new buttons below it
        for (originalView in originalButtons) {
            // Assuming `originalView` is a Button, we can get its text for the name and price
            val button = originalView as? Button
            val currentText = button?.text.toString()
            val parts = currentText.split("\n") // Assuming the format is "name\nprice"

            // Make sure the text is in the expected format (name\nprice)
            val currentName = parts.getOrElse(0) { "Unknown" }
            var currentPrice = parts.getOrElse(1) { "0.0" }

            // Remove the Euro symbol from the price string, if present
            currentPrice = currentPrice.replace("€", "").trim() // Remove the Euro symbol

            // Create a vertical LinearLayout to hold the original button and the new buttons
            val verticalLayout =
                LinearLayout(menuLayout.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams =
                        GridLayout.LayoutParams(
                            GridLayout.spec(
                                GridLayout.UNDEFINED,
                                GridLayout.FILL,
                                1f
                            ),
                            GridLayout.spec(
                                GridLayout.UNDEFINED,
                                GridLayout.FILL,
                                1f
                            )
                        )
                            .apply {
                                width = 0 // Match parent width in GridLayout
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setMargins(16, 16, 16, 16)
                            }
                }

            // Ensure the original button fills the width of its cell
            val buttonParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            originalView.layoutParams = buttonParams

            // Create a horizontal LinearLayout to hold the action buttons
            val horizontalLayout =
                LinearLayout(menuLayout.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                            .apply {
                                topMargin =
                                    8 // Space between the parent button and action
                                // buttons
                            }
                    gravity = Gravity.CENTER // Center the buttons horizontally
                }

            // Create the "X" button
            val actionButton =
                Button(menuLayout.context).apply {
                    text = "X"
                    setTextColor(Color.RED) // Icon color
                    textSize = 20f // Larger icon size
                    gravity = Gravity.CENTER

                    layoutParams =
                        LinearLayout.LayoutParams(
                            60, // Width
                            60 // Height
                        )
                            .apply {
                                marginEnd = 16 // Space between X and pencil button
                            }

                    setPadding(0, 0, 0, 0) // Ensure content isn't clipped

                    val shape =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.LTGRAY) // Neutral background
                        }

                    val rippleDrawable =
                        RippleDrawable(ColorStateList.valueOf(Color.LTGRAY), shape, null)
                    background = rippleDrawable

                    setOnClickListener {
                        try {
                            // Get the parent button (the button we want to remove)
                            val parentButton = verticalLayout.getChildAt(0) as Button

                            // Get the name and price of the item from the parent button text
                            val buttonText = parentButton.text.toString()
                            val parts = buttonText.split("\n")
                            val itemName = parts.getOrElse(0) { "Unknown" }
                            val itemPrice = parts.getOrElse(1) { "0.0" }

                            // Log the item info for debugging
                            Log.d(
                                "RemoveButton",
                                "Attempting to remove item: Name = $itemName, Price = $itemPrice"
                            )

                            // Step 1: Remove the button from the layout
                            menuLayout.removeView(verticalLayout)

                            // Step 2: Remove the item from the JSON file
                            val isDeleted = removeFromJsonFile(itemName)
                            if (isDeleted) {
                                // Update total
                                val priceValue = itemPrice.toInt() ?: 0
                                total -= priceValue
                                val displayTest: TextView = findViewById(R.id.totalText)
                                var displayTotal = total.toString()
                                displayTest.text = "€$displayTotal"

                                // Show success toast
                                Toast.makeText(
                                    context,
                                    "$itemName removed successfully!",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            } else {
                                // If deletion failed in JSON, inform user
                                Log.e("RemoveButton", "Failed to delete $itemName from JSON.")
                                Toast.makeText(
                                    context,
                                    "Failed to update JSON file. Try again.",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        } catch (e: Exception) {
                            // Catch any error that occurs during the removal process
                            Log.e("RemoveButton", "Error while removing item: ${e.message}")
                            Toast.makeText(context, "Removed successfully!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }

            // Create the pencil button for editing
            val pencilButton =
                Button(menuLayout.context).apply {
                    text = "\u270E"
                    setTextColor(Color.BLUE) // Icon color
                    textSize = 20f // Larger icon size
                    gravity = Gravity.CENTER

                    layoutParams =
                        LinearLayout.LayoutParams(
                            60, // Width
                            60 // Height
                        )

                    setPadding(0, 0, 0, 0) // Ensure content isn't clipped

                    val shape =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.LTGRAY) // Neutral background
                        }

                    val rippleDrawable =
                        RippleDrawable(ColorStateList.valueOf(Color.LTGRAY), shape, null)
                    background = rippleDrawable

                    setOnClickListener {
                        // Show the edit dialog and pass the necessary info
                        if (button != null) {
                            showEditDialog(
                                menuLayout.context,
                                button,
                                currentName,
                                currentPrice
                            ) // Pass price without Euro symbol
                        }
                    }
                }

            // Add the buttons to the horizontal layout
            horizontalLayout.addView(actionButton)
            horizontalLayout.addView(pencilButton)

            // Add the original button and the horizontal layout to the vertical layout
            verticalLayout.addView(originalView)
            verticalLayout.addView(horizontalLayout)

            // Add the vertical layout back to the GridLayout
            menuLayout.addView(verticalLayout)
        }

        // Add the "+" button
        val addButton =
            Button(menuLayout.context).apply {
                text = "+"
                setTextColor(Color.BLACK)
                textSize = 30f
                gravity = Gravity.CENTER

                layoutParams =
                    GridLayout.LayoutParams(
                        GridLayout.spec(
                            GridLayout.UNDEFINED,
                            GridLayout.FILL,
                            1f
                        ),
                        GridLayout.spec(
                            GridLayout.UNDEFINED,
                            GridLayout.FILL,
                            1f
                        )
                    )
                        .apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            setMargins(16, 16, 16, 16)
                        }

                val drawable =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 10f
                        setColor(Color.TRANSPARENT)
                        setStroke(2, Color.BLACK)
                    }

                val rippleDrawable =
                    RippleDrawable(ColorStateList.valueOf(Color.LTGRAY), drawable, null)
                background = rippleDrawable

                setOnClickListener { showAddProductDialog(menuLayout) }
            }

        val editButton = findViewById<Button>(R.id.editButton)
        editButton.setOnClickListener {
            val orderSummaryLayout = findViewById<LinearLayout>(R.id.orderSummaryLayout)
            removeActionButtonsFromGrid(menuLayout, orderSummaryLayout)
        }

        menuLayout.addView(addButton)
    }
    fun showAddProductDialog(menuLayout: GridLayout) {
        // Create the dialog
        val dialog = AlertDialog.Builder(this).create()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null)

        // Get the input fields
        val nameEditText: EditText = dialogView.findViewById(R.id.editName)
        val priceEditText: EditText = dialogView.findViewById(R.id.editPrice)

        // Set InputFilter on the priceEditText to allow only numeric input
        priceEditText.inputType = InputType.TYPE_CLASS_NUMBER // Restricts input to numbers only

        dialog.setView(dialogView)

        // "Add" button action
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add") { _, _ ->
            val name = nameEditText.text.toString().trim()
            val price = priceEditText.text.toString().trim()

            // Validate inputs
            if (name.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Please enter a valid name and price!", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            // Parse the price as an integer
            val priceInt = price.toIntOrNull()
            if (priceInt == null) {
                Toast.makeText(this, "Price must be a valid integer!", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            // Update the JSON file
            addProductToJsonFile(name, priceInt.toString())

            // Remove the "+" button temporarily
            val addButton = menuLayout.getChildAt(menuLayout.childCount - 1)
            menuLayout.removeView(addButton)

            // Add the new button
            menuButton(menuLayout, name, priceInt)

            // Re-add the "+" button to the end
            menuLayout.addView(addButton)

            Toast.makeText(this, "$name added successfully!", Toast.LENGTH_SHORT).show()
        }

        // "Cancel" button action
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { _, _ -> dialog.dismiss() }

        dialog.show()
    }
    fun removeActionButtonsFromGrid(menuLayout: GridLayout, orderSummaryLayout: LinearLayout) {
        // 1️⃣ Clear the entire GridLayout (removes all child views)
        menuLayout.removeAllViews()

        // 2️⃣ Clear the Order Summary buttons
        orderSummaryLayout.removeAllViews()

        // 3️⃣ Reset the total to 0 when clearing the layout
        total = 0
        val displayTest: TextView = findViewById(R.id.totalText)
        var displayTotal = total.toString()
        displayTest.text = "€$displayTotal"

        // 4️⃣ Clear the item buttons map to reset the state
        itemButtons.clear()

        // 5️⃣ Clear the order list (if you're using one)
        orderList.clear()

        // 6️⃣ Instead of re-reading the JSON, manually add the buttons you need
        // For example, you can call read_json to re-add all buttons from the JSON file
        read_json(menuLayout)

        // 7️⃣ Loop through the children of the GridLayout and remove the "+" button if it exists
        for (i in 0 until menuLayout.childCount) {
            val child = menuLayout.getChildAt(i)
            if (child is Button && child.text == "+") {
                // Remove the "+" button
                menuLayout.removeViewAt(i)
                isAddButtonRemoved = true // Mark the "+" button as removed
                break // Only remove the first "+" button
            }
        }

        // 8️⃣ Add the "+" button only if it was removed previously and is not added already
        if (isAddButtonRemoved && !isAddButtonAdded) {
            val addButton =
                Button(menuLayout.context).apply {
                    text = "+"
                    setTextColor(Color.BLACK)
                    textSize = 30f
                    gravity = Gravity.CENTER
                    layoutParams =
                        GridLayout.LayoutParams(
                            GridLayout.spec(
                                GridLayout.UNDEFINED,
                                GridLayout.FILL,
                                1f
                            ),
                            GridLayout.spec(
                                GridLayout.UNDEFINED,
                                GridLayout.FILL,
                                1f
                            )
                        )
                            .apply {
                                width = 0
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setMargins(16, 16, 16, 16)
                            }

                    val drawable =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 10f
                            setColor(Color.TRANSPARENT)
                            setStroke(2, Color.BLACK)
                        }

                    val rippleDrawable =
                        RippleDrawable(ColorStateList.valueOf(Color.LTGRAY), drawable, null)

                    background = rippleDrawable

                    setOnClickListener { println("Add button clicked!") }
                }

            menuLayout.addView(addButton)
            isAddButtonAdded = true
        }

        // 9️⃣ Set the edit button functionality
        val editButton = findViewById<Button>(R.id.editButton)
        editButton.setOnClickListener { showPasswordDialog { addActionButtonsToGrid(menuLayout) } }
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
    fun showEditDialog(context: Context, button: Button, currentName: String, currentPrice: String) {
        val dialog = AlertDialog.Builder(context).create()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit, null)

        // Get the name and price input fields from the dialog layout
        val nameEditText: EditText = dialogView.findViewById(R.id.editName)
        val priceEditText: EditText = dialogView.findViewById(R.id.editPrice)

        // Set the current name and price as default values in the input fields
        nameEditText.setText(currentName)
        priceEditText.setText(currentPrice)

        dialog.setView(dialogView)

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { _, _ ->
            val newName = nameEditText.text.toString().trim()
            val newPrice = priceEditText.text.toString().trim()

            // Ensure the new name and price are valid
            if (newName.isNotEmpty() && newPrice.isNotEmpty() && newPrice.toDoubleOrNull() != null
            ) {
                try {
                    // Update the product in the JSON file (this could be handled elsewhere)
                    updateJsonFile(currentName, newName, newPrice)

                    // Update the text of the existing button only (no need to remove or recreate)
                    button.text = "$newName\n$newPrice€"

                    // Find the item in the order summary and update it too
                    itemButtons[newName] = button to 1 // Update the map with the new name

                    // Ensure the layout of action buttons below the parent button is correct
                    val parent = button.parent
                    if (parent is LinearLayout) {
                        // Find the horizontal layout containing action buttons (X and pencil)
                        val horizontalLayout = parent.getChildAt(1) as? LinearLayout

                        // Ensure the action buttons (X and pencil) are still intact, we don't need
                        // to recreate them
                        horizontalLayout?.let {
                            // Here we assume the existing X and pencil buttons are still present
                            // and functional
                            // No need to modify these buttons unless required
                        }
                    }

                    Toast.makeText(context, "Button updated successfully!", Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Failed to update button: ${e.message}",
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            } else {
                Toast.makeText(context, "Please enter valid name and price!", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { _, _ -> dialog.dismiss() }

        dialog.show()
    }
    fun waitForNfcAndWrite(context: Context, data: String) {
        isNfcOperationActive = true
        enableNfcForegroundDispatch()

        // Show a message to the user
        Toast.makeText(context, "Please place your NFC card within 5 seconds.", Toast.LENGTH_SHORT)
            .show()

        val timeout = 5000L // 5 seconds
        val startTime = System.currentTimeMillis()

        val handler = Handler(Looper.getMainLooper())
        handler.post(
            object : Runnable {
                override fun run() {
                    if (detectedTag != null) {

                        // Read data from NFC tag
                        readFromNfcTag(context, detectedTag)
                        // Safely access UI elements on the main thread
                        val nfcBalanceText = findViewById<TextView>(R.id.nfcBalanceText)

                        // Extract numeric values and calculate balance
                        try {
                            val dataBalance =
                                data
                                    .filter { it.isDigit() }
                                    .takeIf { it.isNotEmpty() }
                                    ?.toInt()
                                    ?: 0
                            val nfcBalance =
                                nfcBalanceText
                                    .text
                                    .toString()
                                    .filter { it.isDigit() }
                                    .takeIf { it.isNotEmpty() }
                                    ?.toInt()
                                    ?: 0
                            val balance = (nfcBalance - dataBalance).toString()
                            // Check for null detectedTag
                            if (detectedTag == null) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        "Error: DetectedTag is null",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                                return
                            }

                            // Attempt to write to NFC tag
                            val success =
                                try {
                                    if (nfcBalance > dataBalance) {
                                        writeToNfcTag(detectedTag!!, balance)
                                        PaymentStatus(
                                            iconResId = R.drawable.ic_tick,
                                            title = "Success!",
                                            message = "Payment Successful",
                                            isSuccess =
                                            true // This makes the title text
                                            // green
                                        )
                                        updateAllOrderCountsInJson()

                                    } else {
                                        writeToNfcTag(detectedTag!!, nfcBalance.toString())
                                        PaymentStatus(
                                            iconResId = R.drawable.ic_cross,
                                            title = "Failed!",
                                            message = "Insufficient Balance",
                                            isSuccess =
                                            false // This makes the title text
                                            // green
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            "Error writing to NFC tag",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                    false
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
                                message = "NFC tag not detected",
                                isSuccess = false // This makes the title text green
                            )
                        }
                        cleanupNfcOperation() // Stop NFC detection
                    }
                }
            }
        )
    }
    fun writeToNfcTag(tag: Tag, data: String): Boolean {
        try {
            // Convert the data to NDEF format
            val ndefMessage = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", data)))

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
        Toast.makeText(context, "Please tap your NFC tag within 5 seconds.", Toast.LENGTH_SHORT)
            .show()

        val timeout = 5000L // 5 seconds
        val startTime = System.currentTimeMillis()

        val handler = Handler(Looper.getMainLooper())
        handler.post(
            object : Runnable {
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
            }
        )
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
                    val data =
                        records.joinToString("\n") { record ->
                            String(record.payload) // Convert the payload bytes to a string
                        }

                    // Display the data as a Toast
                    val nfcBalanceText = findViewById<TextView>(R.id.nfcBalanceText)
                    val finalData = data.filter{it.isDigit()}
                    nfcBalanceText.text = "NFC balance: €$finalData"
                } else {
                    Toast.makeText(context, "No NDEF message found on the tag.", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(context, "This tag does not support NDEF.", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to read tag: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    fun addProductToJsonFile(name: String, price: String) {
        val file = File(filesDir, "products.json")

        try {
            // Read the existing JSON content or initialize an empty JSON object if the file doesn't exist
            val jsonString = if (file.exists()) file.readText() else "{}"
            val jsonObject = JSONObject(jsonString)

            if (jsonObject.has(name)) {
                // If the product exists, update the price (0th index) and keep other values unchanged
                val existingArray = jsonObject.getJSONArray(name)
                existingArray.put(0, price) // Update the 0th index with the new price
            } else {
                // If the product doesn't exist, create a new array [price, 0, 0]
                val newArray = JSONArray()
                newArray.put(price) // 0th index: price
                newArray.put(0)     // 1st index: 0
                jsonObject.put(name, newArray)
            }

            // Write the updated JSON object back to the file
            file.writeText(jsonObject.toString())
            Log.d("AddProduct", "Successfully added/updated $name in JSON file with price: $price.")
        } catch (e: Exception) {
            Log.e("AddProduct", "Error adding/updating product in JSON file: ${e.message}")
        }
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

                    Log.d("ReadJson", "Product: $key, Price: $price")

                    // Remove existing button with the same name (if it exists)
                    val existingButton = findButtonByName(menuLayout, key, price)
                    existingButton?.let { menuLayout.removeView(it) }

                    // Add a button for this product
                    menuButton(menuLayout, key, price)
                }
            } else {
                Log.e("JSON", "products.json not found!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ReadJson", "Error reading JSON: ${e.message}")
        }
    }
    fun updateJsonFile(oldName: String, newName: String, newPrice: String) {
        val file = File(filesDir, "products.json") // Internal storage path

        try {
            // Read the existing JSON data
            val jsonString = if (file.exists()) file.readText() else "{}"
            val jsonObject = JSONObject(jsonString)

            // Preserve the order by creating a new JSONObject with the same order of keys
            val jsonOrder = mutableListOf<String>()
            val newJsonObject = JSONObject()

            // Collect the keys (names) in the order they appear in the original JSON object
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                jsonOrder.add(keys.next())
            }

            // Iterate over the order and update the product
            for (key in jsonOrder) {
                if (key == oldName) {
                    // Update the product name and modify the price
                    val existingArray = jsonObject.getJSONArray(key)
                    val updatedArray = JSONArray().apply {
                        put(newPrice.toInt()) // Update the price (0th index)
                        put(existingArray.getInt(1)) // Retain units sold (1st index)
                    }
                    newJsonObject.put(newName, updatedArray) // Update with the new name and array
                } else {
                    // Retain the existing products
                    newJsonObject.put(key, jsonObject.getJSONArray(key))
                }
            }

            // Write the updated JSON data back to the file
            file.writeText(newJsonObject.toString())
            Log.d("UpdateJson", "Successfully updated $oldName to $newName in JSON file.")
        } catch (e: Exception) {
            Log.e("UpdateJson", "Error updating JSON file: ${e.message}")
        }
    }
    fun removeFromJsonFile(itemName: String): Boolean {
        try {
            val file = File(filesDir, "products.json") // Path to the JSON file

            if (!file.exists()) {
                Log.e("RemoveFromJson", "JSON file not found.")
                return false
            }

            val jsonString = file.readText() // Read the JSON file content
            val jsonObject = JSONObject(jsonString)

            // Check if the product exists
            if (jsonObject.has(itemName)) {
                jsonObject.remove(itemName) // Remove the product

                // Write the updated JSON back to the file
                file.writeText(jsonObject.toString())
                Log.d("RemoveFromJson", "Successfully removed $itemName from JSON file.")
                return true
            } else {
                Log.e("RemoveFromJson", "Item $itemName not found in JSON.")
                return false
            }
        } catch (e: Exception) {
            // Catch any errors during the process
            Log.e("RemoveFromJson", "Error removing item from JSON: ${e.message}")
            return false
        }
    }
    fun updateJsonWithOrderCount(itemName: String, count: Int) {
        val file = File(filesDir, "products.json") // Path to your JSON file

        try {
            // Check if the JSON file exists
            if (!file.exists()) {
                Log.e("UpdateJson", "JSON file not found!")
                return
            }

            // Read the existing JSON content
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)

            // Check if the item exists in the JSON
            if (jsonObject.has(itemName)) {
                val itemArray = jsonObject.getJSONArray(itemName)

                // Update the second index by adding the count
                val currentUnitsSold = itemArray.getInt(1) // Get the existing count
                itemArray.put(1, currentUnitsSold + count) // Update the second index (units sold)

                // Save the updated array back into the JSON object
                jsonObject.put(itemName, itemArray)

                // Write the updated JSON back to the file
                file.writeText(jsonObject.toString())
                Log.d("UpdateJson", "Updated $itemName's units sold to ${currentUnitsSold + count}.")
            } else {
                Log.e("UpdateJson", "Item $itemName not found in JSON!")
            }
        } catch (e: Exception) {
            Log.e("UpdateJson", "Error updating JSON: ${e.message}")
        }
    }
    fun updateAllOrderCountsInJson() {
        itemButtons.forEach { (name, pair) ->
            val (_, count) = pair // Extract the count
            updateJsonWithOrderCount(name, count) // Update the JSON for this item
        }
    }

}