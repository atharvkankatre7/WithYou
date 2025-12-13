package com.withyou.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.withyou.app.ui.theme.*

/**
 * Settings screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    var isDarkMode by remember { mutableStateOf(true) }
    var autoPlay by remember { mutableStateOf(true) }
    var syncSensitivity by remember { mutableStateOf("Normal") }
    var showNotifications by remember { mutableStateOf(true) }
    var showSyncSensitivityDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance section
            SettingsSection(title = "Appearance") {
                SettingsToggleItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    isChecked = isDarkMode,
                    onToggle = { isDarkMode = it }
                )
            }
            
            // Playback section
            SettingsSection(title = "Playback") {
                SettingsToggleItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = "Auto-play",
                    subtitle = "Automatically start playback when joining",
                    isChecked = autoPlay,
                    onToggle = { autoPlay = it }
                )
                
                SettingsClickItem(
                    icon = Icons.Outlined.Speed,
                    title = "Sync Sensitivity",
                    subtitle = syncSensitivity,
                    onClick = { showSyncSensitivityDialog = true }
                )
            }
            
            // Notifications section
            SettingsSection(title = "Notifications") {
                SettingsToggleItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Push Notifications",
                    subtitle = "Get notified when partner joins",
                    isChecked = showNotifications,
                    onToggle = { showNotifications = it }
                )
            }
            
            // About section
            SettingsSection(title = "About") {
                SettingsClickItem(
                    icon = Icons.Outlined.Info,
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
                
                SettingsClickItem(
                    icon = Icons.Outlined.Policy,
                    title = "Privacy Policy",
                    subtitle = null,
                    onClick = { }
                )
                
                SettingsClickItem(
                    icon = Icons.Outlined.Description,
                    title = "Terms of Service",
                    subtitle = null,
                    onClick = { }
                )
                
                SettingsClickItem(
                    icon = Icons.Outlined.Feedback,
                    title = "Send Feedback",
                    subtitle = null,
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sign out button
            Button(
                onClick = { /* Sign out */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed.copy(alpha = 0.2f),
                    contentColor = ErrorRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App branding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WithYou",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkSecondary
                )
                Text(
                    text = "Watch Together, Even Apart",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkSecondary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Made with ❤️",
                    style = MaterialTheme.typography.labelSmall,
                    color = RosePrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Sync sensitivity dialog
    if (showSyncSensitivityDialog) {
        AlertDialog(
            onDismissRequest = { showSyncSensitivityDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Sync Sensitivity", color = Color.White) },
            text = {
                Column {
                    listOf("Tight", "Normal", "Relaxed").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncSensitivity = option
                                    showSyncSensitivityDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = syncSensitivity == option,
                                onClick = {
                                    syncSensitivity = option
                                    showSyncSensitivityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RosePrimary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (option) {
                                        "Tight" -> "Very strict sync, may cause more seeks"
                                        "Normal" -> "Balanced sync for most connections"
                                        "Relaxed" -> "Looser sync, good for slow connections"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnDarkSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncSensitivityDialog = false }) {
                    Text("Cancel", color = OnDarkSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = RosePrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnDarkSecondary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkSecondary
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RosePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceVariantDark
            )
        )
    }
}

@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnDarkSecondary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkSecondary
                )
            }
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OnDarkSecondary
        )
    }
}

