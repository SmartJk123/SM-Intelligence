package com.example.smartmoney.ui.warmup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.R
import com.example.smartmoney.ui.components.SystemLogo

/**
 * Branded warm-up screen presented post-authentication while accounts and transactions
 * are hydrated into the local Room database.
 *
 * Adopts the identical auth wallpaper backdrop (from LoginScreen/SignUpScreen) to provide
 * seamless visual continuity after login.
 */
@Composable
fun WarmUpSyncScreen(
    viewModel: WarmUpSyncViewModel,
    onWarmUpComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isReady) {
        if (uiState.isReady) {
            onWarmUpComplete()
        }
    }

    // Smooth animated progress bar fill
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ProgressBarAnimation"
    )

    // Gentle ambient pulsing scale for the logo
    val infiniteTransition = rememberInfiniteTransition(label = "LogoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    val darkSlate = Color(0xFF263228)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkSlate),
        contentAlignment = Alignment.Center
    ) {
        // Wallpaper Graphic Overlay (Matches LoginScreen and SignUpScreen)
        Image(
            painter = painterResource(id = R.drawable.wallpaper_overlap),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Branded System Logo with pulsing scale
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale),
                contentAlignment = Alignment.Center
            ) {
                SystemLogo(modifier = Modifier.size(96.dp))
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Rounded Gradient Loading Bar
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF384D3D))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF34D399), // Emerald Mint
                                    Color(0xFFF59E0B)  // Energy Amber
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Step Message with smooth slide/fade animation
            AnimatedContent(
                targetState = uiState.message,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(250)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(200)))
                },
                label = "SyncMessageTransition"
            ) { targetMessage ->
                Text(
                    text = targetMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFDAEBE3), // Pale Mint text
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle security hint
            Text(
                text = "Secured end-to-end",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8FA89B),
                fontSize = 11.sp
            )
        }
    }
}
