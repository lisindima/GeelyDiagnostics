package com.geelydiagnostics.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
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
            val report = DiagnosticsReportExporter.create(
                state = viewModel.uiState,
                generatedAtMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
            )
            val output = requireNotNull(contentResolver.openOutputStream(uri))
            output.writer(Charsets.UTF_8).use { writer -> writer.write(report) }
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

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }

    private fun exportReport() {
        val now = System.currentTimeMillis()
        val suffix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        createReportDocument.launch("GeelyDiagnostics-$suffix.json")
    }
}
