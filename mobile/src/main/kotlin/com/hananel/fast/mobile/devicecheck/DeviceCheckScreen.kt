package com.hananel.fast.mobile.devicecheck

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hananel.fast.core.diagnostics.ReportRow
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.RowStatus
import com.hananel.fast.core.diagnostics.toLogText
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.theme.FastTheme

@Composable
fun DeviceCheckRoute(viewModel: DeviceCheckViewModel = viewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    DeviceCheckScreen(sections)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCheckScreen(sections: List<ReportSection>?) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.device_check_share_subject)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.device_check_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(R.string.device_check_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = sections != null,
                        onClick = { sections?.let { context.shareReport(shareSubject, it) } },
                    ) {
                        Text(stringResource(R.string.device_check_share))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        if (sections == null) {
            Loading(innerPadding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(sections, key = { it.title }) { section ->
                    // The report is English technical data. Laying it out RTL in a Hebrew UI
                    // reorders values such as "16 (API 36)", so the body stays LTR.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SectionCard(section)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: ReportSection) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = section.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                section.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReportRowItem(row)
                }
            }
        }
    }
}

@Composable
private fun ReportRowItem(row: ReportRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = row.status.color(),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowStatus.color(): Color = when (this) {
    RowStatus.GOOD -> FastColors.Mint
    RowStatus.WARN -> MaterialTheme.colorScheme.error
    RowStatus.INFO -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun Loading(innerPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.device_check_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Context.shareReport(subject: String, sections: List<ReportSection>) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, sections.toLogText())
    }
    startActivity(Intent.createChooser(send, subject))
}

@Preview
@Composable
private fun DeviceCheckScreenPreview() {
    FastTheme {
        DeviceCheckScreen(
            listOf(
                ReportSection(
                    "GNSS",
                    listOf(
                        ReportRow("On-board GNSS", "yes", RowStatus.GOOD),
                        ReportRow("Dual-frequency", "yes", RowStatus.GOOD),
                        ReportRow("Signals", "GPS L1+L5, Galileo L1+L5, BeiDou B1+L5, GLONASS G1"),
                    ),
                ),
                ReportSection("Paired devices", listOf(ReportRow("Watch", "not connected", RowStatus.WARN))),
            ),
        )
    }
}
