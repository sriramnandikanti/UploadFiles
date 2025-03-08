package com.example.myapplication

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Context
import android.provider.OpenableColumns
import java.io.InputStream
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RequestStoragePermission()
            FilePickerScreen()
        }
    }
}

@Composable
fun RequestStoragePermission() {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e("Permission", "Storage permission denied")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun FilePickerScreen() {
    var fileName by remember { mutableStateOf("No file selected") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            fileName = getFileName(context, it)
            fileUri = it
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = { filePickerLauncher.launch("*/*") }) {
            Text(text = "Upload Files")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = fileName)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { fileUri?.let { downloadFile(context, it, fileName) } }, enabled = fileUri != null) {
            Icon(painter = painterResource(id = R.drawable.download), contentDescription = "Download")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Download")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { fileUri?.let { openPreviewScreen(context, it) } }, enabled = fileUri != null) {
            Icon(painter = painterResource(id = R.drawable.preview), contentDescription = "Preview")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Preview")
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "Unknown"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

@RequiresApi(Build.VERSION_CODES.Q)
fun downloadFile(context: Context, uri: Uri, fileName: String) {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, contentResolver.getType(uri))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newUri = contentResolver.insert(collection, values)

        newUri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                inputStream?.copyTo(outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(newUri, values, null, null)
            }

            Log.d("Download", "File downloaded to: $newUri")
        }
    } catch (e: Exception) {
        Log.e("Download", "Error downloading file", e)
    }
}

fun openPreviewScreen(context: Context, uri: Uri) {
    val intent = Intent(context, PreviewActivity::class.java).apply {
        putExtra("fileUri", uri.toString())
    }
    context.startActivity(intent)
}

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileUri: Uri? = intent.getStringExtra("fileUri")?.let { Uri.parse(it) }
        setContent {
            fileUri?.let { PreviewScreen(it) }
        }
    }
}

@Composable
fun PreviewScreen(fileUri: Uri) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(fileUri) ?: ""

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when {
            mimeType.startsWith("image/") -> {
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(fileUri))
                bitmap?.asImageBitmap()?.let {
                    Image(bitmap = it, contentDescription = "Preview Image")
                }
            }
            mimeType.startsWith("video/") -> {
                AndroidView(factory = { VideoView(it).apply {
                    setVideoURI(fileUri)
                    start()
                } })
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Open with")
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Log.e("Preview", "No application found to open this file type.", e)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Preview(showBackground = true)
@Composable
fun FilePickerPreview() {
    FilePickerScreen()
}
