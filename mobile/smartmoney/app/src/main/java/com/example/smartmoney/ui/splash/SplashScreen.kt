package com.example.smartmoney.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.smartmoney.R

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var isAnimated by remember { mutableStateOf(false) }
    
    // Text animation states
    var showFullWord by remember { mutableStateOf(true) }
    var showHyphen by remember { mutableStateOf(false) }
    var showIntelligence by remember { mutableStateOf(false) }

    // Left half animation (Slides in from the left)
    val leftOffsetX by animateDpAsState(
        targetValue = if (isAnimated) 0.dp else (-130).dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
    )

    // Right half animation (Slides in from the right)
    val rightOffsetX by animateDpAsState(
        targetValue = if (isAnimated) 0.dp else 130.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
    )

    // Fade in
    val alpha by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(durationMillis = 600)
    )

    LaunchedEffect(Unit) {
        // Trigger the animation shortly after composition
        delay(100)
        isAnimated = true
        
        delay(600)
        showFullWord = false
        
        delay(400)
        showHyphen = true
        
        delay(200)
        showIntelligence = true
        
        delay(1500)
        onSplashFinished()
    }

    val lightLogoColor = Color(0xFFDAEBE3) // Pale Mint Green from brand palette for high contrast

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF263228)), // Match windowSplashScreenBackground
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(260.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.logo_left),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(lightLogoColor),
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = leftOffsetX)
                        .alpha(alpha)
                )
                Image(
                    painter = painterResource(id = R.drawable.logo_right),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(lightLogoColor),
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = rightOffsetX)
                        .alpha(alpha)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "S",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                AnimatedVisibility(visible = showFullWord) {
                    Text(
                        text = "mart",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Text(
                    text = "M",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                AnimatedVisibility(visible = showFullWord) {
                    Text(
                        text = "oney",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                AnimatedVisibility(visible = showHyphen) {
                    Text(
                        text = " - ",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }
                
                AnimatedVisibility(visible = showIntelligence) {
                    Text(
                        text = "Intelligence",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }
            }
        }
    }
}
