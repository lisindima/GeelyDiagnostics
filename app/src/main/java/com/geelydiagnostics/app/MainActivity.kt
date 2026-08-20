package com.geelydiagnostics.app

import android.content.Intent
import android.os.Bundle
import android.os.Environment
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

    private val createReportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val output = requireNotNull(contentResolver.openOutputStream(uri))
            output.writer(Charsets.UTF_8).use { writer -> writer.write(createReport()) }
        }.onSuccess {
            viewModel.onLog("Diagnostic report exported")
            Toast.makeText(this, "Отчёт сохранён", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            viewModel.onLog("Report export failed: ${error.message}", error)
            Toast.makeText(this, "Не удалось сохранить отчёт", Toast.LENGTH_LONG).show()
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
                onFavoriteToggle = viewModel::toggleFavorite,
                onClearLog = viewModel::clearLog,
            )
        }
    }

    private fun exportReport() {
        val now = System.currentTimeMillis()
        val suffix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val fileName = "GeelyDiagnostics-$suffix.json"
        val pickerIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        val pickerAvailable = pickerIntent.resolveActivity(packageManager) != null
        if (pickerAvailable) {
            runCatching { createReportDocument.launch(fileName) }
                .onFailure { error -> saveReportLocally(fileName, error) }
        } else {
            saveReportLocally(fileName)
        }
    }

    private fun createReport(): String = DiagnosticsReportExporter.create(
        state = viewModel.uiState,
        generatedAtMillis = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME,
    )

    private fun saveReportLocally(fileName: String, pickerError: Throwable? = null) {
        runCatching {
            val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            check(directory.exists() || directory.mkdirs()) { "Cannot create ${directory.absolutePath}" }
            File(directory, fileName).apply { writeText(createReport(), Charsets.UTF_8) }
        }.onSuccess { file ->
            pickerError?.let { viewModel.onLog("System file picker failed: ${it.message}", it) }
            viewModel.onLog("Diagnostic report saved locally: ${file.absolutePath}")
            Toast.makeText(
                this,
                "Отчёт сохранён локально:\n${file.absolutePath}",
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure { error ->
            viewModel.onLog("Local report export failed: ${error.message}", error)
            Toast.makeText(this, "Не удалось сохранить отчёт", Toast.LENGTH_LONG).show()
        }
    }
}
