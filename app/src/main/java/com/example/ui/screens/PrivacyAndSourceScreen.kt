package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.GreenActive
import com.example.ui.theme.PurplePrimaryDark

@Composable
fun PrivacyAndSourceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("privacy_source_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Privacy & Open Source",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy Guarantees Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = GreenActive)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% OFFLINE PRIVACY GUARANTEE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenActive
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                PrivacyBullet("No INTERNET Permission", "Soundglass does not declare or request the android.permission.INTERNET manifest permission. Network requests are physically impossible at the OS kernel level.")
                PrivacyBullet("No Audio Leaves Device", "Raw microphone PCM audio is processed entirely in volatile RAM. No audio files, recordings, or telemetry are ever uploaded or transmitted.")
                PrivacyBullet("No Raw Audio Storage", "Raw audio samples are discarded immediately after computing sound level metrics and FFT bins. Only decimated summary statistics are saved when you explicitly choose to save a session.")
                PrivacyBullet("Contextual Permission", "Microphone access is requested strictly when you tap Start Measurement, never on app launch.")
                PrivacyBullet("Zero Tracking & Telemetry", "No ad networks, analytics engines, Google Firebase tracking, crash reporters, or proprietary tracking libraries are bundled.")
                PrivacyBullet("Cloud Backup Disabled", "Both Android cloud auto-backup and device-to-device transfer are explicitly excluded in backup_rules.xml and data_extraction_rules.xml.")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Manifest Audit Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = CyanAccentDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDITING THE MERGED MANIFEST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccentDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can independently verify that zero unauthorized permissions exist by inspecting the merged manifest in the build output:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF100E17),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "aapt dump permissions app-release.apk\n# Or inspect:\napp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CyanAccentDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Expected output: Only 'android.permission.RECORD_AUDIO' is present. 'android.permission.INTERNET' is completely absent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GreenActive
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Open Source License Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = PurplePrimaryDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APPLICATION LICENSE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimaryDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Soundglass is free and open-source software licensed under the GNU General Public License v3.0 or later (GPL-3.0-or-later).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You are free to inspect, run, modify, and redistribute this software under the terms of the GPL-3.0 license.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dependency Inventory Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEPENDENCY INVENTORY & LICENSES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                DepItem("Kotlin Standard Library & Coroutines", "JetBrains s.r.o.", "Apache-2.0")
                DepItem("AndroidX Core & Lifecycle", "Google LLC / AOSP", "Apache-2.0")
                DepItem("Jetpack Compose & Material 3", "Google LLC / AOSP", "Apache-2.0")
                DepItem("AndroidX Room Persistence", "Google LLC / AOSP", "Apache-2.0")
                DepItem("AndroidX DataStore Preferences", "Google LLC / AOSP", "Apache-2.0")
                DepItem("AndroidX Navigation Compose", "Google LLC / AOSP", "Apache-2.0")
                DepItem("Moshi JSON Library", "Square, Inc.", "Apache-2.0")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PrivacyBullet(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(text = "• $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun DepItem(name: String, author: String, license: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = author, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = license,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = PurplePrimaryDark,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
