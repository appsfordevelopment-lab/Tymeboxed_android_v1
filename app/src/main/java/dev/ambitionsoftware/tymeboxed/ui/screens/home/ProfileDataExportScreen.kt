package dev.ambitionsoftware.tymeboxed.ui.screens.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.data.export.SessionExportSortDirection
import dev.ambitionsoftware.tymeboxed.data.export.SessionExportTimeZone
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import kotlinx.coroutines.launch

@Composable
fun ProfileDataExportScreen(
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onDismiss)
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedProfileIds by remember { mutableStateOf(emptySet<String>()) }
    var sortDirection by remember { mutableStateOf(SessionExportSortDirection.ASCENDING) }
    var timeZone by remember { mutableStateOf(SessionExportTimeZone.UTC) }
    var isGenerating by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = cs.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.profiles_export_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.profiles_export_profiles_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                )
                SettingsCard {
                    if (profiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profiles_export_no_profiles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    } else {
                        profiles.forEach { profile ->
                            val selected = profile.id in selectedProfileIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProfileIds = if (selected) {
                                            selectedProfileIds - profile.id
                                        } else {
                                            selectedProfileIds + profile.id
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (selected) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Outlined.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = if (selected) cs.primary else cs.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = profile.name.ifBlank {
                                        stringResource(R.string.profile_region_default)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurface,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.profiles_export_sort_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.profiles_export_sort_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                SettingsCard {
                    ExportRadioRow(
                        selected = sortDirection == SessionExportSortDirection.ASCENDING,
                        title = stringResource(R.string.profiles_export_sort_asc),
                        onSelect = { sortDirection = SessionExportSortDirection.ASCENDING },
                    )
                    ExportRadioRow(
                        selected = sortDirection == SessionExportSortDirection.DESCENDING,
                        title = stringResource(R.string.profiles_export_sort_desc),
                        onSelect = { sortDirection = SessionExportSortDirection.DESCENDING },
                    )
                }

                Text(
                    text = stringResource(R.string.profiles_export_timezone_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.profiles_export_timezone_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                SettingsCard {
                    ExportRadioRow(
                        selected = timeZone == SessionExportTimeZone.UTC,
                        title = stringResource(R.string.profiles_export_timezone_utc),
                        onSelect = { timeZone = SessionExportTimeZone.UTC },
                    )
                    ExportRadioRow(
                        selected = timeZone == SessionExportTimeZone.LOCAL,
                        title = stringResource(R.string.profiles_export_timezone_local),
                        onSelect = { timeZone = SessionExportTimeZone.LOCAL },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    isGenerating = true
                    scope.launch {
                        val csv = vm.exportSessionsCsv(
                            profileIds = selectedProfileIds,
                            sortDirection = sortDirection,
                            timeZone = timeZone,
                        )
                        isGenerating = false
                        val timestamp = System.currentTimeMillis() / 1000
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "tymeboxed_sessions_$timestamp.csv")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.profiles_export_share_title),
                            ),
                        )
                    }
                },
                enabled = !isGenerating && selectedProfileIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                ),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = cs.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.profiles_export_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportRadioRow(
    selected: Boolean,
    title: String,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = cs.primary,
                unselectedColor = cs.onSurfaceVariant,
            ),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
        )
    }
}
