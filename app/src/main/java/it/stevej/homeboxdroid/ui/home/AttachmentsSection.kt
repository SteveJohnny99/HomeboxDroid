package it.stevej.homeboxdroid.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.stevej.homeboxdroid.domain.Attachment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sezione allegati nel dettaglio: miniature immagini (con auth via Coil),
 * chip per i file non-immagine, eliminazione, e caricamento da fotocamera o
 * galleria.
 */
@Composable
fun AttachmentsSection(
    viewModel: HomeViewModel,
    entityId: String,
    attachments: List<Attachment>,
    enabled: Boolean,
) {
    val context = LocalContext.current

    // Galleria (photo picker, nessun permesso richiesto).
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) uploadFromUri(context, viewModel, entityId, uri)
    }

    // Fotocamera: scrive su un file temporaneo esposto via FileProvider.
    var pendingPhoto by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val p = pendingPhoto
        if (success && p != null) {
            val bytes = runCatching { p.second.readBytes() }.getOrNull()
            if (bytes != null) {
                viewModel.uploadAttachment(entityId, bytes, p.second.name, "image/jpeg")
            }
        }
        pendingPhoto = null
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Allegati",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                attachments.forEach { att ->
                    AttachmentThumb(
                        url = viewModel.attachmentUrl(entityId, att.id),
                        attachment = att,
                        enabled = enabled,
                        onDelete = { viewModel.deleteAttachment(entityId, att.id) },
                    )
                    Spacer(Modifier.width(10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val file = createImageFile(context)
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    pendingPhoto = uri to file
                    cameraLauncher.launch(uri)
                },
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Text("  Foto")
            }
            OutlinedButton(
                onClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text("  Galleria")
            }
        }
    }
}

@Composable
private fun AttachmentThumb(
    url: String,
    attachment: Attachment,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.size(96.dp),
        ) {
            if (attachment.isImage) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    contentDescription = attachment.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Column(
                    modifier = Modifier.size(96.dp).padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Text(
                        attachment.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
        if (enabled) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(26.dp),
                onClick = onDelete,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Elimina allegato",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

private fun createImageFile(context: android.content.Context): File {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(dir, "IMG_$stamp.jpg")
}

private fun uploadFromUri(
    context: android.content.Context,
    viewModel: HomeViewModel,
    entityId: String,
    uri: Uri,
) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        ?: return
    val ext = when {
        mime.contains("png") -> "png"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("pdf") -> "pdf"
        mime.contains("webp") -> "webp"
        else -> "bin"
    }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    viewModel.uploadAttachment(entityId, bytes, "IMG_$stamp.$ext", mime)
}
