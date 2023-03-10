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
import android.provider.MediaStore
import android.util.Log
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
    private val REQUEST_ENABLE_BT: Int = 0
    private var isPairedDevicesShown = mutableStateOf(false)

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

    private val requestBTPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showToast("Permission granted")
        } else {
            showToast("Permission denied")
        }
    }
    private fun requestBlueToothPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED -> {
                showToast("BlueTooth Permission previously granted")
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) -> showToast("Show BlueTooth permissions dialog")

            else -> requestBTPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun initBlueTooth() {
        println("Initialize BlueTooth")
        bluetoothManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getSystemService(BluetoothManager::class.java)
            } else {
                TODO("VERSION.SDK_INT < M")
            }
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            // Device doesn't support BlueTooth
            println("BlueTooth not support")
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            // Request user to turn on BlueTooth
            println("BlueTooth enabling")
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
        queryPairedDevice()
    }

    private var setPairedDevices: Set<BluetoothDevice>? = null
    private var listPairedDevicesName = ArrayList<String>()
    private fun queryPairedDevice() {
        if (bluetoothAdapter == null) return
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
        setPairedDevices = bluetoothAdapter?.bondedDevices
        println("Paired devices: ${setPairedDevices?.size}")
        setPairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            listPairedDevicesName.add(deviceName)
            println("$deviceName $deviceHardwareAddress")
        }
//        val mmServerSocket: BluetoothServerSocket? =
//            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
//                "ARTL",
//                UUID.fromString("b850c51d-fb45-4bd7-9161-01923ce65526")
//            )
    }

    private val MY_UUID: UUID? = UUID.fromString("b850c51d-fb45-4bd7-9161-01923ce65526")
    private fun pairDevice(deviceName: String) {
        if (setPairedDevices?.isEmpty() == true) return
        var device: BluetoothDevice? = null
        setPairedDevices!!.forEach{ d ->
            if (d.name == deviceName) device = d
        }
        if (device == null) return

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
            return
        }
        showToast(device!!.name)

        val socket: BluetoothSocket

        try {
            socket = device!!.createRfcommSocketToServiceRecord(MY_UUID)
        } catch (e: IOException) {
            Log.e(TAG, "Socket creation failed", e)
            return
        }

        try {
            socket.connect()
            showToast("Socket Connected!")
        } catch (e: IOException) {
            try {
                socket.close()
            } catch (e2: IOException) {
                Log.e(TAG, "Unable to close socket", e2)
            }

            Log.e(TAG, "Socket connection failed", e)
            return
        }
        //val connect = ConnectThread(device, MY_UUID_INSECURE!!, this)

        // connect.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // println("===Run Here===")

        // Create a translator:
        translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE).build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            .addOnSuccessListener { translatorReady = true }
            .addOnFailureListener { exception -> showToast(exception.toString()) }

        requestBlueToothPermission()
        initBlueTooth()
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
                    },
//                    pairDevice = {println("p")},
                    startServer = {println("p")},
                    sendMessage = {println("p")},
                    pairDevice = ::pairDevice,
                    isPairedDevicesShown = isPairedDevicesShown,
                    listPairedDevices = listPairedDevicesName
//                    startServer = ::startServer,
//                    sendMessage = ::sendMessage
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
}
