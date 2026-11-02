package com.example.smartmoney.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.model.TimeHorizon
import com.example.smartmoney.ui.analytics.model.TrajectoryPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionTrajectoryCard(
    trajectory: List<TrajectoryPoint>,
    currentHorizon: TimeHorizon,
    onHorizonChanged: (TimeHorizon) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Multi-Horizon Wealth Trajectory", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Canvas for Trajectory Curve
            if (trajectory.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    val maxAmount = trajectory.maxOf { it.optimizedAmount.toFloat() }.coerceAtLeast(1f)
                    val minAmount = trajectory.minOf { it.baselineAmount.toFloat() }.coerceAtMost(0f)
                    
                    val width = size.width
                    val height = size.height
                    val range = maxAmount - minAmount
                    
                    val stepX = width / (trajectory.size - 1).coerceAtLeast(1)
                    
                    val baselinePath = Path()
                    val optimizedPath = Path()
                    
                    trajectory.forEachIndexed { index, point ->
                        val x = index * stepX
                        val baseNorm = (point.baselineAmount.toFloat() - minAmount) / range
                        val optNorm = (point.optimizedAmount.toFloat() - minAmount) / range
                        
                        val baseY = height - (baseNorm * height)
                        val optY = height - (optNorm * height)
                        
                        if (index == 0) {
                            baselinePath.moveTo(x, baseY)
                            optimizedPath.moveTo(x, optY)
                        } else {
                            baselinePath.lineTo(x, baseY)
                            optimizedPath.lineTo(x, optY)
                        }
                    }
                    
                    drawPath(
                        path = baselinePath,
                        color = Color(0xFF607D8B),
                        style = Stroke(width = 4f)
                    )
                    
                    drawPath(
                        path = optimizedPath,
                        color = Color(0xFF00C853),
                        style = Stroke(width = 6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Horizon tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeHorizon.entries.forEach { horizon ->
                    FilterChip(
                        selected = currentHorizon == horizon,
                        onClick = { onHorizonChanged(horizon) },
                        label = { Text(horizon.displayName) }
                    )
                }
            }
        }
    }
}
