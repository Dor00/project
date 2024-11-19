package com.example.cameraapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cameraapp.ui.theme.CameraappTheme
import java.io.ByteArrayOutputStream
import java.io.IOException


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraCaptureScreen()
                }
            }
        }
    }
}

@Composable
fun CameraCaptureScreen() {
    var hasCameraPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasCameraPermission = true
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    if (hasCameraPermission) {
        CameraContent()
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Camera permission is required to take pictures.")
            Button(onClick = {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun CameraContent() {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { capturedBitmap: Bitmap? ->
        if (capturedBitmap != null) {
            bitmap = capturedBitmap
            val uri = saveImageToExternalStorage(context, capturedBitmap)
            imageUri = uri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        )
        {

            Button(
                onClick = { launcher.launch(null) },

                ) {
                Text("Take Picture")
            }

            Button(
                onClick = {
                            bitmap = compressImageUsingQuadTree(bitmap!!)
                          },

                ) {
                Text("Compress Picture")
            }

        }

        Spacer(modifier = Modifier.height(16.dp))
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        imageUri?.let {
            Text(
                text = "Image saved to: $it "+"tamaño"+ getBitmapSizeInBytes(bitmap!!)+" bytes" +
                        "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

    private fun getBitmapSizeInBytes(bitmap: Bitmap): Int {
        val byteStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
        val byteArray = byteStream.toByteArray()
        return byteArray.size
    }


fun saveImageToExternalStorage(context: Context, bitmap: Bitmap): Uri? {



    val contentResolver = context.contentResolver
    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "captured_image_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val imageUri = contentResolver.insert(imageCollection, contentValues)

    imageUri?.let { uri ->
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)) {
                    throw IOException("Failed to save bitmap.")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
    return imageUri
}

// QuadTree implementation
data class QuadTreeNode(
    val x: Int, val y: Int, val size: Int,
    val color: Int? = null, val isLeaf: Boolean = false,
    val nw: QuadTreeNode? = null, val ne: QuadTreeNode? = null,
    val sw: QuadTreeNode? = null, val se: QuadTreeNode? = null
)

fun Bitmap.getAverageColor(x: Int, y: Int, size: Int): Int {
    var r = 0
    var g = 0
    var b = 0
    var count = 0

    for (i in x until x + size) {
        for (j in y until y + size) {
            val color = getPixel(i, j)
            r += Color.red(color)
            g += Color.green(color)
            b += Color.blue(color)
            count++
        }
    }

    return Color.rgb(r / count, g / count, b / count)
}

fun Bitmap.isHomogeneous(x: Int, y: Int, size: Int, tolerance: Int): Boolean {
    val averageColor = getAverageColor(x, y, size)
    for (i in x until x + size) {
        for (j in y until y + size) {
            val color = getPixel(i, j)
            if (Math.abs(Color.red(color) - Color.red(averageColor)) > tolerance ||
                Math.abs(Color.green(color) - Color.green(averageColor)) > tolerance ||
                Math.abs(Color.blue(color) - Color.blue(averageColor)) > tolerance) {
                return false
            }
        }
    }
    return true
}

fun buildQuadTree(bitmap: Bitmap, x: Int, y: Int, size: Int, tolerance: Int): QuadTreeNode {
    if (size <= 1 || bitmap.isHomogeneous(x, y, size, tolerance)) {
        return QuadTreeNode(x, y, size, bitmap.getAverageColor(x, y, size), true)
    }

    val halfSize = size / 2
    return QuadTreeNode(
        x, y, size, null, false,
        buildQuadTree(bitmap, x, y, halfSize, tolerance),
        buildQuadTree(bitmap, x + halfSize, y, halfSize, tolerance),
        buildQuadTree(bitmap, x, y + halfSize, halfSize, tolerance),
        buildQuadTree(bitmap, x + halfSize, y + halfSize, halfSize, tolerance)
    )
}

fun drawQuadTree(bitmap: Bitmap, node: QuadTreeNode) {
    val colorTransparente: Int = Color.argb(0, 0, 0, 0) // Transparente
    if (node.isLeaf && node.color != null) {
        for (i in node.x until node.x + node.size) {
            for (j in node.y until node.y + node.size) {
                bitmap.setPixel(i, j, node.color)
            }
        }
    }
    else {
        node.nw?.let { drawQuadTree(bitmap, it) }
        node.ne?.let { drawQuadTree(bitmap, it) }
        node.sw?.let { drawQuadTree(bitmap, it) }
        node.se?.let { drawQuadTree(bitmap, it) }
    }
}

fun compressImageUsingQuadTree(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height


    // Crear una nueva imagen bitmap para almacenar la imagen comprimida
    val compressedBitmap = Bitmap.createBitmap(width, height, bitmap.config!!)

    // Generar el quadtree y dibujar la imagen comprimida
    val quadtree = buildQuadTree(bitmap, 0, 0, width.coerceAtMost(height), 1)
    drawQuadTree(compressedBitmap, quadtree)
    return compressedBitmap
}



@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CameraappTheme {
        CameraCaptureScreen()
    }
}
