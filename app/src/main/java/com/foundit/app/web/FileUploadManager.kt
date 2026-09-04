package com.foundit.app.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.foundit.app.R
import java.io.File

class FileUploadManager(
    private val activity: AppCompatActivity,
    private val launcher: ActivityResultLauncher<Intent>
) {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        val contentIntent = params.createIntent().apply {
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, activity.getString(R.string.file_upload))
            createCameraIntent()?.let {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
            }
        }

        return runCatching {
            launcher.launch(chooser)
            true
        }.getOrElse {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            false
        }
    }

    fun handleResult(resultCode: Int, data: Intent?) {
        val results = when {
            resultCode != Activity.RESULT_OK -> null
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            cameraPhotoUri != null -> arrayOf(cameraPhotoUri!!)
            else -> null
        }

        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
        cameraPhotoUri = null
    }

    fun cancelPending() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        cameraPhotoUri = null
    }

    private fun createCameraIntent(): Intent? {
        val imageFile = File.createTempFile("foundit_capture_", ".jpg", activity.cacheDir)
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            imageFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("FoundIt capture", uri)
        }

        return if (intent.resolveActivity(activity.packageManager) != null) {
            cameraPhotoUri = uri
            intent
        } else {
            null
        }
    }
}
