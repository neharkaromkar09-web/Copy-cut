package com.example.ui.archive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Project
import com.example.data.model.VNImportReport
import com.example.ui.theme.BorderColor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.White

@Composable
fun ImportReportDialog(
    report: VNImportReport,
    onDismiss: () -> Unit,
    onOpenImportedProject: (Project) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (report.importedProject != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (report.importedProject != null) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (report.importedProject != null) "Import Diagnostic Report" else "Import Failed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = PrimaryText
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "File: ${report.sourceFileName} (${if (report.isArchive) "Archive Package" else "Direct Project"})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SecondaryText
                    )
                }

                // Metric Cards
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SecondaryBackground,
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DiagnosticRow("Recognized Clips:", "${report.recognizedClips}")
                            DiagnosticRow("Recognized Cuts & Splits:", "${report.recognizedCuts}")
                            DiagnosticRow("Recognized Keyframes:", "${report.recognizedKeyframes}")
                            DiagnosticRow("Audio Tracks:", "${report.recognizedAudioTracks}")
                        }
                    }
                }

                // Supported Effects
                if (report.supportedEffects.isNotEmpty()) {
                    item {
                        Text("Supported Editing Features:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        report.supportedEffects.forEach { effect ->
                            Text("• $effect", fontSize = 11.sp, color = PrimaryText, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                        }
                    }
                }

                // Unsupported Features
                if (report.unsupportedEffects.isNotEmpty()) {
                    item {
                        Text("Unsupported / Proprietary Features:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        report.unsupportedEffects.forEach { unsupported ->
                            Text("• $unsupported", fontSize = 11.sp, color = SecondaryText, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                        }
                    }
                }

                // Warnings
                if (report.warnings.isNotEmpty()) {
                    item {
                        Text("Import Notices & Missing Media:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
                        report.warnings.forEach { warning ->
                            Text("• $warning", fontSize = 11.sp, color = SecondaryText, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (report.importedProject != null) {
                Button(
                    onClick = {
                        onOpenImportedProject(report.importedProject)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                    modifier = Modifier.testTag("open_imported_project_button")
                ) {
                    Text("Open in Editor")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (report.importedProject != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = SecondaryText)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
    }
}
