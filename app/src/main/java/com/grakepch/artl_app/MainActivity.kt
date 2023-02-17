package com.grakepch.artl_app

import android.Manifest
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.grakepch.artl_app.camera.CameraView
import com.grakepch.artl_app.ui.theme.ARTL_APPTheme
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {

    private var distToCenter: Int = 0
    private var strNearCenter: String = ""
    private var imgWidth: Int = 0
    private var imgHeight: Int = 0

    private var selectedImage = mutableStateOf<Bitmap?>(null)
    private val inputText = mutableStateOf("[Ready]")
    private val outputText = mutableStateOf("[Ready]")

    private var translatorReady: Boolean = false
    private lateinit var translator: Translator
    private val availableLanguages: List<String> = TranslateLanguage.getAllLanguages()
    private val selectedLangCode = mutableStateOf("zh")

    private var isLangSelectorShown = mutableStateOf(false)

    // Camera
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var PhoneCameraMode = mutableStateOf(false)

    // BlueTooth
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val REQUEST_ENABLE_BT: Int = 0;

    // Request Camera Permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showToast("Permission granted")
            PhoneCameraMode.value = true
        } else {
            showToast("Permission denied")
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                showToast("Permission previously granted")
                PhoneCameraMode.value = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> showToast("Show camera permissions dialog")

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestBlueTooth() {
        println("Start Bluetooth")
        bluetoothManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getSystemService(BluetoothManager::class.java)
            } else {
                TODO("VERSION.SDK_INT < M")
            }
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            println("Bluetooth not support")
        }
        if (bluetoothAdapter?.isEnabled == false) {
            println("Bluetooth enabled")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_ENABLE_BT
                )
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            println("CODE$REQUEST_ENABLE_BT")
        }
    }

    private fun queryPairedDevice() {
        var pairedDevices: Set<BluetoothDevice>? = null
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_ENABLE_BT
            )
        }
        pairedDevices = bluetoothAdapter?.bondedDevices
        println("Paired devices: ${pairedDevices?.size}")
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            println("$deviceName $deviceHardwareAddress")
        }
        val mmServerSocket: BluetoothServerSocket? =
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("ARTL", UUID.fromString("b850c51d-fb45-4bd7-9161-01923ce65526"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a translator:
        translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE).build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            .addOnSuccessListener { translatorReady = true }
            .addOnFailureListener { exception -> showToast(exception.toString()) }

        requestBlueTooth()
        queryPairedDevice()
        requestCameraPermission()
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ARTL_APPTheme {
                // A surface container using the 'background' color from the theme
                MainLayout(
                    selectedImage,
                    inputText,
                    outputText,
                    isLangSelectorShown,
                    ::runImgChoose,
                    menuLangSelect = { langCode: String ->
                        selectedLangCode.value = langCode
                        outputText.value = "[Waiting...]"
                        val options =
                            TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH)
                                .setTargetLanguage(TranslateLanguage.fromLanguageTag(langCode)!!)
                                .build()
                        translator = Translation.getClient(options)
                        val conditions = DownloadConditions.Builder().requireWifi().build()
                        translator.downloadModelIfNeeded(conditions).addOnSuccessListener {
                            translatorReady = true
                            runTranslator(inputText.value)
                            isLangSelectorShown.value = false
                        }.addOnFailureListener { exception -> showToast(exception.toString()) }

                    },
                    availableLanguages,
                    selectedLangCode,
                    PhoneCameraMode,
                    createCameraView = {
                        CameraView(
                            outputDirectory = outputDirectory,
                            executor = cameraExecutor,
                            onImageCaptured = ::handleImageCapture,
                            onError = { println("View error: $it") },
                            PhoneCameraMode
                        )
                    },
                    modeToggle = {
                        PhoneCameraMode.value = !PhoneCameraMode.value
                    }
                )
            }
        }
    }

    private val getImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val intent = result.data ?: return@registerForActivityResult
            val imageUri = intent.data
            selectedImage.value = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                ?: return@registerForActivityResult

            imgWidth = selectedImage.value!!.width
            imgHeight = selectedImage.value!!.height

            runTextRecognition()
        }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun runImgChoose() {
        val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        getImage.launch(gallery)
    }

    private fun runTextRecognition() {
        val imageImported: InputImage = InputImage.fromBitmap(selectedImage.value!!, 0)

        val recognizer: TextRecognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(imageImported).addOnSuccessListener { texts ->
            processTextRecognitionResult(texts)
        }.addOnFailureListener { e -> // Task failed with an exception
            e.printStackTrace()
        }
    }

    private fun processTextRecognitionResult(texts: Text) {
        val blocks = texts.textBlocks
        if (blocks.size == 0) {
//            showToast("No text found")
            return
        }
        println("No. of Blocks: " + blocks.size)
//        mGraphicOverlay.clear()
        distToCenter = imgWidth + imgHeight
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            println("No. of Lines: " + lines.size)
            for (j in lines.indices) {
                val elements = lines[j].elements
                val lineBox = Rect()
                var lineStr = ""
                for (k in elements.indices) {
                    val boundingBox = elements[k].boundingBox
                    lineBox.union(boundingBox!!)
                    lineStr += (if (k == 0) "" else " ") + elements[k].text
//                    val textGraphic: GraphicOverlay.Graphic = TextGraphic(
//                        mGraphicOverlay,
//                        elements[k]
//                    )
//                    mGraphicOverlay.add(textGraphic)
                }
                val dist = sqrt(
                    (lineBox.centerX() - imgWidth / 2.0).pow(2.0) + (lineBox.centerY() - imgHeight / 2.0).pow(
                        2.0
                    )
                ).toInt()
                if (dist < distToCenter) {
                    distToCenter = dist
                    strNearCenter = lineStr
                }
                println("Element Text: $lineStr")
                println("Element Rect: $lineBox")
            }
        }
        println("Dim: " + imgWidth + "x" + imgHeight)
        inputText.value = strNearCenter

        runTranslator(strNearCenter)
    }

    private fun runTranslator(input: String) {
        if (translatorReady) {
            translator.translate(input).addOnSuccessListener { translatedText ->
                outputText.value = translatedText
            }.addOnFailureListener { e ->
                showToast(e.toString())
                outputText.value = "[Err]"
            }
        }
    }

    // Camera Functions
    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    private fun handleImageCapture(uri: Uri) {
        println("Image captured: $uri")

        val unRotatedBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            ?: return

        selectedImage.value = rotateBitmap(unRotatedBitmap, 90f)

        imgWidth = selectedImage.value!!.width
        imgHeight = selectedImage.value!!.height

        runTextRecognition()
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // BlueTooth Threads
    private val MY_UUID_INSECURE: UUID? = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    var mConnectedThread: ConnectedThread? = null
    private val handler: Handler? = null

    var TAG = "MainActivity"
    var send_data: EditText? = null
    var view_data: TextView? = null
    var messages: StringBuilder? = null
    inner class ConnectThread(device: BluetoothDevice, uuid: UUID) : Thread() {
        private var mmSocket: BluetoothSocket? = null

        init {
            Log.d(TAG, "ConnectThread: started.")
            mmDevice = device
            deviceUUID = uuid
        }

        override fun run() {
            var tmp: BluetoothSocket? = null
            Log.i(TAG, "RUN mConnectThread ")

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(
                    TAG, "ConnectThread: Trying to create InsecureRfcommSocket using UUID: "
                            + MY_UUID_INSECURE
                )
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID_INSECURE)
            } catch (e: IOException) {
                Log.e(TAG, "ConnectThread: Could not create InsecureRfcommSocket " + e.message)
            }
            mmSocket = tmp

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket!!.connect()
            } catch (e: IOException) {
                // Close the socket
                try {
                    mmSocket!!.close()
                    Log.d(TAG, "run: Closed Socket.")
                } catch (e1: IOException) {
                    Log.e(
                        TAG,
                        "mConnectThread: run: Unable to close connection in socket " + e1.message
                    )
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: $MY_UUID_INSECURE")
            }

            //will talk about this in the 3rd video
            mmSocket?.let { connected(it) }
        }

        fun cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.")
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: close() of mmSocket in Connectthread failed. " + e.message)
            }
        }
    }

    private fun connected(mmSocket: BluetoothSocket) {
        Log.d(TAG, "connected: Starting.")

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(mmSocket)
        mConnectedThread!!.start()
    }

    inner class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            Log.d(TAG, "ConnectedThread: Starting.")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024) // buffer store for the stream
            var bytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                // Read from the InputStream
                try {
                    bytes = mmInStream!!.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)
                    Log.d(TAG, "InputStream: $incomingMessage")
                    runOnUiThread(Runnable { view_data?.setText(incomingMessage) })
                } catch (e: IOException) {
                    Log.e(TAG, "write: Error reading Input Stream. " + e.message)
                    break
                }
            }
        }

        fun write(bytes: ByteArray?) {
            val text = String(bytes!!, Charset.defaultCharset())
            Log.d(TAG, "write: Writing to outputstream: $text")
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "write: Error writing to output stream. " + e.message)
            }
        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (_: IOException) {
            }
        }
    }


    fun SendMessage(v: View?) {
        val bytes: ByteArray = send_data?.text.toString().toByteArray(Charset.defaultCharset())
        mConnectedThread?.write(bytes)
    }


//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        send_data = findViewById<View>(R.id.editText) as EditText
//        view_data = findViewById<View>(R.id.textView) as TextView
//        if (bluetoothAdapter != null && !bluetoothAdapter!!.isEnabled) {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
//        }
//    }

    fun Start_Server(view: View?) {
        val accept = AcceptThread()
        accept.start()
    }

    inner class AcceptThread : Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null

            // Create a new listening server socket
            try {
                tmp = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                    "appname",
                    MY_UUID_INSECURE
                )
                Log.d(TAG, "AcceptThread: Setting up Server using: $MY_UUID_INSECURE")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }
            mmServerSocket = tmp
        }

        override fun run() {
            Log.d(TAG, "run: AcceptThread Running.")
            var socket: BluetoothSocket? = null
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(TAG, "run: RFCOM server socket start.....")
                socket = mmServerSocket!!.accept()
                Log.d(TAG, "run: RFCOM server socket accepted connection.")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }

            //talk about this is in the 3rd
            socket?.let { connected(it) }
            Log.i(TAG, "END mAcceptThread ")
        }

        fun cancel() {
            Log.d(TAG, "cancel: Canceling AcceptThread.")
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: Close of AcceptThread ServerSocket failed. " + e.message)
            }
        }
    }
}