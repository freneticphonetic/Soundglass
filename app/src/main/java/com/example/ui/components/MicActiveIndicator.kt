package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CaptureState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.GreenActive
import com.example.ui.theme.RedClipping

@Composable
fun MicActiveIndicator(
    captureState: CaptureState,
    isMicrophoneMuted: Boolean,
    sourceLabel: String?,
    onClickDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (badgeBg, dotColor, label, icon) = when {
        isMicrophoneMuted -> {
            Quad(
                Color(0xFF3B1E22),
                RedClipping,
                "MIC HARDWARE MUTED",
                Icons.Default.MicOff
            )
        }
        captureState == CaptureState.RECORDING -> {
            Quad(
                Color(0xFF132F20),
                GreenActive,
                sourceLabel?.substringBefore(" ") ?: "MIC ACTIVE",
                Icons.Default.Mic
            )
        }
        captureState == CaptureState.PAUSED -> {
            Quad(
                Color(0xFF362B16),
                AmberWarning,
                "CAPTURE PAUSED",
                Icons.Default.Pause
            )
        }
        else -> {
            Quad(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "MIC STANDBY",
                Icons.Default.Mic
            )
        }
    }

    Box(
        modifier = modifier
            .testTag("mic_active_indicator")
            .clip(RoundedCornerShape(20.dp))
            .background(badgeBg)
            .clickable { onClickDiagnostics() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (captureState == CaptureState.RECORDING) pulseAlpha else 1.0f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = dotColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = dotColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
