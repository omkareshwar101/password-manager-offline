package com.offlinevault.app.ui.components

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
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.offlinevault.app.OfflineVaultApplication
import com.offlinevault.app.R
import com.offlinevault.app.util.BuiltInIcons
import com.offlinevault.app.util.PasswordStrength
import com.offlinevault.app.util.PasswordStrengthEstimator
import com.offlinevault.app.ui.theme.AmberWarning
import com.offlinevault.app.ui.theme.DeepIndigo
import com.offlinevault.app.ui.theme.OfflineVaultTheme
import com.offlinevault.app.ui.theme.StrongGreen
import com.offlinevault.app.ui.theme.TealAccent
import com.offlinevault.app.ui.util.passwordStrengthLabelRes
import com.offlinevault.app.ui.util.strengthLabelRes

@Composable
fun OfflineVaultLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.ic_splash_logo),
        contentDescription = stringResource(R.string.content_desc_app_logo),
        modifier = modifier.size(size),
    )
}

@Composable
fun OfflineBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.offline_badge),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun AccountAvatar(
    imageRef: String?,
    accountName: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val context = LocalContext.current
    val imageStore = remember(context) {
        (context.applicationContext as OfflineVaultApplication).container.imageStore
    }
    val file = remember(imageRef) { imageStore.resolveFile(imageRef) }
    val builtIn = remember(imageRef) { BuiltInIcons.find(imageRef) }
    val description = stringResource(R.string.content_desc_account_image, accountName)
    val palette = listOf(
        Color(0xFF3F51B5),
        Color(0xFF00897B),
        Color(0xFF8E24AA),
        Color(0xFFD81B60),
        Color(0xFF5E35B1),
        Color(0xFF00838F),
    )
    val fallbackColor = palette[(accountName.hashCode() and Int.MAX_VALUE) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        fallbackColor.copy(alpha = 0.85f),
                        fallbackColor.copy(alpha = 0.55f),
                    ),
                ),
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (builtIn != null) {
            Text(
                text = builtIn.emoji,
                fontSize = (size.value * 0.5f).sp,
                textAlign = TextAlign.Center,
            )
        } else if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = accountName.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier,
    fairLabelAsMedium: Boolean = false,
) {
    if (password.isEmpty()) return
    val strength = PasswordStrengthEstimator.estimate(password)
    val segments = PasswordStrengthEstimator.segments(password)
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.FAIR -> AmberWarning
        PasswordStrength.STRONG -> TealAccent
        PasswordStrength.VERY_STRONG -> StrongGreen
    }
    val label = if (fairLabelAsMedium) {
        stringResource(passwordStrengthLabelRes(password))
    } else {
        stringResource(strengthLabelRes(strength))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < segments) color else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(16.dp))
        } else {
            OfflineVaultLogo(size = 58.dp)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DividerLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Suppress("unused")
@Composable
private fun PreviewLight() {
    OfflineVaultTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountAvatar(null, "Gmail")
            PasswordStrengthMeter("Abcdefgh!234")
        }
    }
}
