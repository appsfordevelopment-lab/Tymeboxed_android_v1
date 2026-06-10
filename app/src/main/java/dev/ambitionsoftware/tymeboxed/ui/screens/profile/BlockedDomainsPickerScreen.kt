package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.domain.popularBlockedDomains

/**
 * Full-screen blocked-websites picker — popular hosts with checkboxes and Done.
 * Shares [ProfileEditViewModel] with [ProfileEditScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedDomainsPickerScreen(
    viewModel: ProfileEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val rowCardBg = if (isDark) Color(0xFF2C2C2E) else Color.White
    val doneButtonBg = if (isDark) Color(0xFFF2F2F7) else cs.onBackground
    val doneButtonText = if (isDark) Color(0xFF1C1C1E) else cs.background

    val filteredPopular = remember(searchQuery) {
        if (searchQuery.isBlank()) popularBlockedDomains
        else popularBlockedDomains.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    val customDomains = remember(state.domains) {
        state.domains.filter { it !in popularBlockedDomains }
    }
    val filteredCustom = remember(customDomains, searchQuery) {
        if (searchQuery.isBlank()) customDomains
        else customDomains.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.blocked_domains_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = doneButtonBg,
                        contentColor = doneButtonText,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.blocked_domains_done),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.isAllowModeDomains) {
                Text(
                    text = stringResource(R.string.blocked_domains_picker_allow_mode_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.blocked_domains_search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant,
                    unfocusedContainerColor = cs.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = cs.onSurface,
                    unfocusedTextColor = cs.onSurface,
                    cursorColor = cs.primary,
                ),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.blocked_domains_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.blocked_domains_popular_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (filteredPopular.isEmpty() && filteredCustom.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.blocked_domains_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(filteredPopular, key = { it }) { domain ->
                        PopularDomainRow(
                            domain = domain,
                            isSelected = domain in state.domains,
                            cardBackground = rowCardBg,
                            onToggle = { viewModel.onToggleDomain(domain) },
                        )
                    }
                    if (filteredCustom.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.blocked_domains_added_section),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                        }
                        items(filteredCustom, key = { "custom-$it" }) { domain ->
                            PopularDomainRow(
                                domain = domain,
                                isSelected = true,
                                cardBackground = rowCardBg,
                                onToggle = { viewModel.onRemoveDomain(domain) },
                                showRemove = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularDomainRow(
    domain: String,
    isSelected: Boolean,
    cardBackground: Color,
    onToggle: () -> Unit,
    showRemove: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (showRemove) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_remove_domain),
                    tint = cs.onSurfaceVariant,
                )
            }
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = cs.primary,
                ),
            )
        }
    }
}
