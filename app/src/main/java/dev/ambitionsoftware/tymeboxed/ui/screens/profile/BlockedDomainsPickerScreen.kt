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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.domain.popularBlockedDomains

/**
 * Full-screen blocked-websites picker — custom domain entry, blocked list,
 * and popular hosts with checkboxes. Shares [ProfileEditViewModel] with
 * [ProfileEditScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedDomainsPickerScreen(
    viewModel: ProfileEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var domainInput by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val rowCardBg = if (isDark) Color(0xFF2C2C2E) else Color.White
    val listContainerBg = if (isDark) Color(0xFF2C2C2E) else cs.surfaceVariant
    val doneButtonBg = if (isDark) Color(0xFFF2F2F7) else cs.onBackground
    val doneButtonText = if (isDark) Color(0xFF1C1C1E) else cs.background
    val maxDomains = ProfileEditViewModel.MAX_DOMAINS_PER_PROFILE
    val atDomainLimit = state.domains.size >= maxDomains

    val sortedBlockedDomains = remember(state.domains) {
        state.domains.sortedBy { it.lowercase() }
    }
    val filteredPopular = remember(searchQuery) {
        if (searchQuery.isBlank()) popularBlockedDomains
        else popularBlockedDomains.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    fun submitDomain() {
        if (domainInput.isBlank() || atDomainLimit) return
        if (viewModel.onAddDomain(domainInput)) {
            domainInput = ""
            focusManager.clearFocus()
        }
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
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.blocked_domains_done),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface,
                    actionIconContentColor = cs.onSurface,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isAllowModeDomains) {
                item {
                    Text(
                        text = stringResource(R.string.blocked_domains_picker_allow_mode_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            item {
                AddDomainSection(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it
                        viewModel.clearDomainError()
                    },
                    onAdd = ::submitDomain,
                    canAdd = domainInput.isNotBlank() && !atDomainLimit,
                    errorMessage = state.errorMessage,
                )
            }

            item {
                BlockedDomainsHeader(
                    count = state.domains.size,
                    max = maxDomains,
                )
            }

            if (sortedBlockedDomains.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(listContainerBg)
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.blocked_domains_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(sortedBlockedDomains, key = { "blocked-$it" }) { domain ->
                    BlockedDomainRow(
                        domain = domain,
                        cardBackground = rowCardBg,
                        onRemove = { viewModel.onRemoveDomain(domain) },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.blocked_domains_limit_footer, maxDomains),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            item {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.blocked_domains_popular_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.blocked_domains_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item {
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
            }

            if (filteredPopular.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.blocked_domains_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(filteredPopular, key = { "popular-$it" }) { domain ->
                    PopularDomainRow(
                        domain = domain,
                        isSelected = domain in state.domains,
                        cardBackground = rowCardBg,
                        enabled = domain in state.domains || !atDomainLimit,
                        onToggle = { viewModel.onToggleDomain(domain) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun AddDomainSection(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
    errorMessage: String?,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.blocked_domains_add_section),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceVariant)
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.blocked_domains_add_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                onClick = onAdd,
                enabled = canAdd,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (canAdd) cs.primary else cs.primary.copy(alpha = 0.4f),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.blocked_domains_add_cd),
                    tint = cs.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = cs.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            text = stringResource(R.string.blocked_domains_add_footer),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun BlockedDomainsHeader(
    count: Int,
    max: Int,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.blocked_domains_list_section),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.blocked_domains_count_fmt, count, max),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockedDomainRow(
    domain: String,
    cardBackground: Color,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_domain),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PopularDomainRow(
    domain: String,
    isSelected: Boolean,
    cardBackground: Color,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .clickable(enabled = enabled || isSelected, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled || isSelected) cs.onSurface else cs.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = isSelected,
            onCheckedChange = { if (enabled || isSelected) onToggle() },
            enabled = enabled || isSelected,
            colors = CheckboxDefaults.colors(
                checkedColor = cs.primary,
            ),
        )
    }
}
