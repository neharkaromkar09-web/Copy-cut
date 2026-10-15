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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.DocumentUtils
import com.example.ui.theme.BorderColor
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.White

data class ImportProgressState(
    val fileName: String,
    val fileSizeBytes: Long = -1L,
    val detectedFormat: String = "Detecting format...",
    val progress: Float = 0f, // 0f to 1f
    val statusText: String = "Reading document stream..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportProgressDialog(
    state: ImportProgressState,
    onDismissRequest: () -> Unit = {}
) {
    BasicAlertDialog(
        onDismissRequest = { /* Modal during active import */ },
        modifier = Modifier.testTag("import_progress_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = White,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PurpleAccentLight),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = if (state.fileName.endsWith(".json", true)) {
                        Icons.Default.Description
                    } else if (state.fileName.endsWith(".7z", true)) {
                        Icons.Default.Archive
                    } else {
                        Icons.Default.FolderZip
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Importing",
                        tint = PurpleAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Title & File Details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Importing Project",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = state.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SecondaryBackground
                        ) {
                            Text(
                                text = state.detectedFormat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurpleAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (state.fileSizeBytes > 0) {
                            Text(
                                text = DocumentUtils.formatFileSize(state.fileSizeBytes),
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                    }
                }

                // Progress Indicator
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PurpleAccent,
                            trackColor = SecondaryBackground,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PurpleAccent,
                            trackColor = SecondaryBackground,
                            strokeCap = StrokeCap.Round
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.statusText,
                            fontSize = 12.sp,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (state.progress > 0f) {
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurpleAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
