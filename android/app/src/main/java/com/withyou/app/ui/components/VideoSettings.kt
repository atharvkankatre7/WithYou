package com.withyou.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.withyou.app.ui.theme.*

/**
 * Aspect ratio options
 */
enum class AspectRatioOption(val displayName: String, val ratio: Float?) {
    FIT("Fit", null),              // Fit to screen maintaining aspect ratio
    FILL("Fill", null),            // Fill screen (may crop)
    FIT_SCREEN("Fit Screen", null), // Stretch to screen
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_21_9("21:9", 21f / 9f),  // Ultrawide
    RATIO_1_1("1:1", 1f)           // Square
}

/**
 * Subtitle track info
 */
data class SubtitleTrack(
    val id: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean = false
)

/**
 * Audio track info
 */
data class AudioTrack(
    val id: Int,
    val language: String?,
    val label: String?,
    val channels: Int?,
    val isSelected: Boolean = false
)

/**
 * Video settings bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsSheet(
    playbackSpeed: Float,
    currentAspectRatio: AspectRatioOption,
    subtitleTracks: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    currentSubtitleId: Int?,
    currentAudioId: Int?,
    isLocked: Boolean,
    onSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatioOption) -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    onAudioSelect: (Int) -> Unit,
    onLoadExternalSubtitle: (Uri) -> Unit,
    onLockControls: () -> Unit,
    onClose: () -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Speed", "Aspect", "Subtitles", "Audio")
    
    // Subtitle file picker
    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onLoadExternalSubtitle(it) }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Video Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
        
        // Tabs
        TabRow(
            selectedTabIndex = currentTab,
            containerColor = Color.Transparent,
            contentColor = RosePrimary,
            indicator = { tabPositions ->
                if (currentTab < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[currentTab])
                            .height(3.dp)
                            .background(RosePrimary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            },
            divider = { HorizontalDivider(color = SurfaceVariantDark) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentTab == index,
                    onClick = { currentTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (currentTab == index) RosePrimary else OnDarkSecondary
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lock screen control button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isLocked) RosePrimary.copy(alpha = 0.2f) else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable { onLockControls() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) RosePrimary else OnDarkSecondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLocked) "Unlock Screen" else "Lock Screen",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isLocked) "Tap to unlock screen controls" else "Tap to lock screen controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary
                    )
                }
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = RosePrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tab content
        when (currentTab) {
            0 -> SpeedSettings(playbackSpeed, onSpeedChange)
            1 -> AspectRatioSettings(currentAspectRatio, onAspectRatioChange)
            2 -> SubtitleSettings(
                subtitleTracks = subtitleTracks,
                currentSubtitleId = currentSubtitleId,
                onSubtitleSelect = onSubtitleSelect,
                onLoadExternal = { subtitlePicker.launch("*/*") }
            )
            3 -> AudioSettings(
                audioTracks = audioTracks,
                currentAudioId = currentAudioId,
                onAudioSelect = onAudioSelect
            )
        }
    }
}

/**
 * Speed settings tab
 */
@Composable
private fun SpeedSettings(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Speed options
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            speeds.take(4).forEach { speed ->
                SpeedChip(
                    speed = speed,
                    isSelected = currentSpeed == speed,
                    onClick = { onSpeedChange(speed) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            speeds.drop(4).forEach { speed ->
                SpeedChip(
                    speed = speed,
                    isSelected = currentSpeed == speed,
                    onClick = { onSpeedChange(speed) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Current speed display
        Text(
            text = "Current: ${currentSpeed}x",
            style = MaterialTheme.typography.bodyLarge,
            color = RosePrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text("${speed}x") },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RosePrimary,
            selectedLabelColor = Color.White,
            containerColor = SurfaceVariantDark,
            labelColor = Color.White
        )
    )
}

/**
 * Aspect ratio settings tab
 */
@Composable
private fun AspectRatioSettings(
    currentRatio: AspectRatioOption,
    onRatioChange: (AspectRatioOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Screen Aspect Ratio",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AspectRatioOption.entries.forEach { option ->
            AspectRatioItem(
                option = option,
                isSelected = currentRatio == option,
                onClick = { onRatioChange(option) }
            )
        }
    }
}

@Composable
private fun AspectRatioItem(
    option: AspectRatioOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) RosePrimary.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = RosePrimary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = option.displayName,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (option != AspectRatioOption.FIT && option != AspectRatioOption.FILL) {
                    Text(
                        text = "Fixed ratio",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary
                    )
                }
            }
        }
    }
}

/**
 * Subtitle settings tab
 */
@Composable
private fun SubtitleSettings(
    subtitleTracks: List<SubtitleTrack>,
    currentSubtitleId: Int?,
    onSubtitleSelect: (Int?) -> Unit,
    onLoadExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // Load external subtitle button
        OutlinedButton(
            onClick = onLoadExternal,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = RosePrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Load External Subtitle (.srt, .ass)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Disable subtitles option
        SubtitleItem(
            track = SubtitleTrack(-1, null, "Off"),
            isSelected = currentSubtitleId == null,
            onClick = { onSubtitleSelect(null) }
        )
        
        // Embedded subtitle tracks
        if (subtitleTracks.isEmpty()) {
            Text(
                text = "No embedded subtitles found",
                color = OnDarkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            subtitleTracks.forEach { track ->
                SubtitleItem(
                    track = track,
                    isSelected = currentSubtitleId == track.id,
                    onClick = { onSubtitleSelect(track.id) }
                )
            }
        }
    }
}

@Composable
private fun SubtitleItem(
    track: SubtitleTrack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) RosePrimary.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (track.id == -1) Icons.Default.SubtitlesOff else Icons.Default.Subtitles,
                contentDescription = null,
                tint = if (isSelected) RosePrimary else OnDarkSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.label ?: track.language ?: "Track ${track.id}",
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                track.language?.let { lang ->
                    if (track.label != null) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkSecondary
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RosePrimary
                )
            }
        }
    }
}

/**
 * Audio settings tab
 */
@Composable
private fun AudioSettings(
    audioTracks: List<AudioTrack>,
    currentAudioId: Int?,
    onAudioSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Audio Track",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (audioTracks.isEmpty()) {
            Text(
                text = "No audio tracks found",
                color = OnDarkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            audioTracks.forEach { track ->
                AudioItem(
                    track = track,
                    isSelected = currentAudioId == track.id,
                    onClick = { onAudioSelect(track.id) }
                )
            }
        }
    }
}

/**
 * Convert ISO 639 language code to readable language name
 */
private fun getLanguageName(code: String?): String {
    if (code.isNullOrBlank()) return "Unknown"
    
    // Handle common language codes
    val langMap = mapOf(
        "en" to "English",
        "eng" to "English",
        "hin" to "Hindi",
        "hi" to "Hindi",
        "es" to "Spanish",
        "spa" to "Spanish",
        "fr" to "French",
        "fra" to "French",
        "fre" to "French",
        "de" to "German",
        "deu" to "German",
        "ger" to "German",
        "it" to "Italian",
        "ita" to "Italian",
        "pt" to "Portuguese",
        "por" to "Portuguese",
        "ru" to "Russian",
        "rus" to "Russian",
        "ja" to "Japanese",
        "jpn" to "Japanese",
        "ko" to "Korean",
        "kor" to "Korean",
        "zh" to "Chinese",
        "zho" to "Chinese",
        "chi" to "Chinese",
        "ar" to "Arabic",
        "ara" to "Arabic",
        "nl" to "Dutch",
        "nld" to "Dutch",
        "dut" to "Dutch",
        "tr" to "Turkish",
        "tur" to "Turkish",
        "pl" to "Polish",
        "pol" to "Polish",
        "sv" to "Swedish",
        "swe" to "Swedish",
        "no" to "Norwegian",
        "nor" to "Norwegian",
        "da" to "Danish",
        "dan" to "Danish",
        "fi" to "Finnish",
        "fin" to "Finnish",
        "cs" to "Czech",
        "ces" to "Czech",
        "cze" to "Czech",
        "hu" to "Hungarian",
        "hun" to "Hungarian",
        "ro" to "Romanian",
        "ron" to "Romanian",
        "rum" to "Romanian",
        "th" to "Thai",
        "tha" to "Thai",
        "vi" to "Vietnamese",
        "vie" to "Vietnamese",
        "id" to "Indonesian",
        "ind" to "Indonesian",
        "ms" to "Malay",
        "msa" to "Malay",
        "may" to "Malay",
        "he" to "Hebrew",
        "heb" to "Hebrew",
        "el" to "Greek",
        "ell" to "Greek",
        "gre" to "Greek"
    )
    
    val normalizedCode = code.lowercase().trim()
    
    // Check exact match first
    langMap[normalizedCode]?.let { return it }
    
    // Check if it's already a readable name (starts with capital letter)
    if (code[0].isUpperCase()) {
        return code
    }
    
    // If not found, capitalize first letter and return
    return code.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun AudioItem(
    track: AudioTrack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VioletSecondary.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = if (isSelected) VioletSecondary else OnDarkSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Only show language name, nothing else
                val languageName = getLanguageName(track.language)
                Text(
                    text = languageName,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = VioletSecondary
                )
            }
        }
    }
}

