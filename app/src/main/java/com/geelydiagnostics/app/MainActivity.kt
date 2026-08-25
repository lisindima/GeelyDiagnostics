package com.geelydiagnostics.app

import com.geelydiagnostics.app.export.DiagnosticsReportExporter
import com.geelydiagnostics.app.ui.GeelyDiagnosticsApp
import com.geelydiagnostics.app.ui.viewmodel.DiagnosticsViewModel
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: DiagnosticsViewModel
    private var pendingExportFileName: String? = null

    private val requestCarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) viewModel.refresh()
    }

    private val createReportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val fileName = pendingExportFileName
        pendingExportFileName = null
        if (uri == null) return@registerForActivityResult
        runCatching {
            val output = requireNotNull(contentResolver.openOutputStream(uri))
            output.writer(Charsets.UTF_8).use { writer -> writer.write(createReport()) }
        }.onSuccess {
            viewModel.onLog("Diagnostic report exported")
            Toast.makeText(this, "Отчёт сохранён", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            viewModel.onLog("Report export failed: ${error.message}", error)
            saveReportLocally(fileName ?: newReportFileName(), error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DiagnosticsViewModel::class.java]
        setContent {
            GeelyDiagnosticsApp(
                state = viewModel.uiState,
                onRefresh = viewModel::refresh,
                onExport = ::exportReport,
                onVhalProfileSelected = viewModel::selectVhalProfile,
                onVhalBackendSelected = { backend ->
                    viewModel.selectVhalBackend(backend)
                    if (backend == VhalGatewayBackend.CAR_PROPERTY_MANAGER) {
                        requestMissingCarPermissions()
                    }
                },
                onFavoriteToggle = viewModel::toggleFavorite,
                onObserveParameter = viewModel::observeParameter,
                onClearLog = viewModel::clearLog,
            )
        }
        if (viewModel.uiState.selectedVhalBackend == VhalGatewayBackend.CAR_PROPERTY_MANAGER) {
            requestMissingCarPermissions()
        }
    }

    private fun requestMissingCarPermissions() {
        val missing = RUNTIME_CAR_PERMISSIONS.filter { permission ->
            runCatching { packageManager.getPermissionInfo(permission, 0) }.isSuccess &&
                checkSelfPermission(permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestCarPermissions.launch(missing.toTypedArray())
    }

    private fun exportReport() {
        val fileName = newReportFileName()
        val pickerIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        val pickerAvailable = pickerIntent.resolveActivity(packageManager) != null
        if (pickerAvailable) {
            pendingExportFileName = fileName
            runCatching { createReportDocument.launch(fileName) }
                .onFailure { error ->
                    pendingExportFileName = null
                    saveReportLocally(fileName, error)
                }
        } else {
            saveReportLocally(fileName)
        }
    }

    private fun newReportFileName(): String {
        val suffix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "GeelyDiagnostics-$suffix.json"
    }

    private fun createReport(): String = DiagnosticsReportExporter.create(
        state = viewModel.uiState,
        generatedAtMillis = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME,
    )

    private fun saveReportLocally(fileName: String, pickerError: Throwable? = null) {
        var downloadsError: Throwable? = null
        runCatching {
            val report = createReport()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { saveReportToDownloads(fileName, report) }
                    .getOrElse { error ->
                        downloadsError = error
                        saveReportToPrivateStorage(fileName, report)
                    }
            } else {
                saveReportToPrivateStorage(fileName, report)
            }
        }.onSuccess { saved ->
            pickerError?.let { viewModel.onLog("System file picker failed: ${it.message}", it) }
            downloadsError?.let { viewModel.onLog("Public Downloads export failed: ${it.message}", it) }
            viewModel.onLog("Diagnostic report saved locally: ${saved.location}")
            Toast.makeText(
                this,
                "Отчёт сохранён:\n${saved.location}",
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure { error ->
            viewModel.onLog("Local report export failed: ${error.message}", error)
            Toast.makeText(this, "Не удалось сохранить отчёт", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveReportToDownloads(fileName: String, report: String): SavedReport {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_REPORT_DIRECTORY",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "MediaStore did not create a Downloads entry" }
        try {
            val output = requireNotNull(contentResolver.openOutputStream(uri, "w"))
            output.writer(Charsets.UTF_8).use { writer -> writer.write(report) }
            val published = contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            check(published > 0) { "MediaStore did not publish the Downloads entry" }
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw error
        }
        return SavedReport("${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_REPORT_DIRECTORY/$fileName")
    }

    private fun saveReportToPrivateStorage(fileName: String, report: String): SavedReport {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        check(directory.exists() || directory.mkdirs()) { "Cannot create ${directory.absolutePath}" }
        val file = File(directory, fileName).apply { writeText(report, Charsets.UTF_8) }
        return SavedReport(file.absolutePath)
    }

    private data class SavedReport(val location: String)

    private companion object {
        const val PUBLIC_REPORT_DIRECTORY = "GeelyDiagnostics"
        val RUNTIME_CAR_PERMISSIONS = listOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_ENERGY",
        )
    }
}
