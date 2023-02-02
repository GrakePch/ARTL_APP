package com.grakepch.artl_app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grakepch.artl_app.ui.theme.ARTL_APPTheme
import java.util.*

@Composable
fun MainLayout(
    selectedImage: MutableState<Bitmap?>,
    inputText: MutableState<String>,
    outputText: MutableState<String>,
    isLangSelectorShown: MutableState<Boolean>,
    runImgChoose: () -> Unit,
    menuLangSelect: (String) -> Unit,
    listLang: List<String>,
    selectedLang: MutableState<String>,
    isCameraShown: MutableState<Boolean>,
    createCameraView: @Composable() () -> Unit,
    modeToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedImage.value != null) Image(
                    bitmap = selectedImage.value!!.asImageBitmap(),
                    contentDescription = "Image to be recognized"
                )
                if (isCameraShown.value) {
                    createCameraView()
                }
            }
            Column(
                modifier = Modifier.padding(all = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isCameraShown.value) {
                    Button(
                        onClick = runImgChoose
                    ) { Text("Import from file") }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Recognized Text", style = MaterialTheme.typography.labelSmall)
                Text(text = inputText.value, style = MaterialTheme.typography.headlineLarge)


                Text(text = "Translated Text", style = MaterialTheme.typography.labelSmall)
                Text(text = outputText.value, style = MaterialTheme.typography.headlineLarge)

                Row {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = {
                                isLangSelectorShown.value = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Target: ${Locale(selectedLang.value).displayName}") }
                        DropdownMenu(expanded = isLangSelectorShown.value,
                            onDismissRequest = { isLangSelectorShown.value = false }) {
                            listLang.forEach { item ->
                                DropdownMenuItem(text = { Text("${Locale(item).displayName} (${item.uppercase()})") },
                                    onClick = { menuLangSelect(item) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = modeToggle,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isCameraShown.value) "Camera Mode" else "File Mode") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ARTL_APPTheme {
        MainLayout(
            selectedImage = remember { mutableStateOf(null) },
            inputText = remember { mutableStateOf("Input") },
            outputText = remember { mutableStateOf("Output") },
            isLangSelectorShown = remember { mutableStateOf(false) },
            runImgChoose = {},
            menuLangSelect = {},
            listOf("Test1", "Test2", "Test3"),
            selectedLang = remember { mutableStateOf("zh") },
            isCameraShown = remember { mutableStateOf(false) },
            createCameraView = {},
            modeToggle = {}
        )
    }
}