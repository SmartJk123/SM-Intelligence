package com.example.smartmoney.ui.budget.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.smartmoney.ui.theme.LocalDarkTheme

data class CategoryVisual(
    val icon: ImageVector,
    val lightBg: Color,
    val lightTint: Color,
    val darkBg: Color,
    val darkTint: Color
) {
    @Composable
    fun currentBg(): Color = if (LocalDarkTheme.current) darkBg else lightBg

    @Composable
    fun currentTint(): Color = if (LocalDarkTheme.current) darkTint else lightTint
}

object BudgetCategoryVisuals {

    fun getVisual(category: String): CategoryVisual {
        val lower = category.lowercase().trim()
        return when {
            lower.contains("grocer") || lower.contains("food") || lower.contains("supermarket") -> {
                CategoryVisual(
                    icon = Icons.Outlined.ShoppingCart,
                    lightBg = Color(0xFFE8F5E9),
                    lightTint = Color(0xFF2E7D32),
                    darkBg = Color(0xFF1B382B),
                    darkTint = Color(0xFF4ADE80)
                )
            }
            lower.contains("util") || lower.contains("power") || lower.contains("electric") || lower.contains("water") -> {
                CategoryVisual(
                    icon = Icons.Outlined.Bolt,
                    lightBg = Color(0xFFFFF8E1),
                    lightTint = Color(0xFFF57F17),
                    darkBg = Color(0xFF382F16),
                    darkTint = Color(0xFFFACC15)
                )
            }
            lower.contains("shop") || lower.contains("cloth") || lower.contains("mall") -> {
                CategoryVisual(
                    icon = Icons.Outlined.ShoppingBag,
                    lightBg = Color(0xFFFCE4EC),
                    lightTint = Color(0xFFC2185B),
                    darkBg = Color(0xFF3D1924),
                    darkTint = Color(0xFFF472B6)
                )
            }
            lower.contains("din") || lower.contains("leisure") || lower.contains("restaur") || lower.contains("cafe") -> {
                CategoryVisual(
                    icon = Icons.Outlined.Restaurant,
                    lightBg = Color(0xFFFFF3E0),
                    lightTint = Color(0xFFE65100),
                    darkBg = Color(0xFF382618),
                    darkTint = Color(0xFFFB923C)
                )
            }
            lower.contains("trans") || lower.contains("fuel") || lower.contains("car") || lower.contains("uber") -> {
                CategoryVisual(
                    icon = Icons.Outlined.DirectionsCar,
                    lightBg = Color(0xFFE1F5FE),
                    lightTint = Color(0xFF0288D1),
                    darkBg = Color(0xFF162E3D),
                    darkTint = Color(0xFF38BDF8)
                )
            }
            lower.contains("rent") || lower.contains("hous") || lower.contains("mortgage") -> {
                CategoryVisual(
                    icon = Icons.Outlined.Home,
                    lightBg = Color(0xFFEDE7F6),
                    lightTint = Color(0xFF512DA8),
                    darkBg = Color(0xFF252136),
                    darkTint = Color(0xFFA78BFA)
                )
            }
            lower.contains("health") || lower.contains("med") || lower.contains("hosp") -> {
                CategoryVisual(
                    icon = Icons.Outlined.LocalHospital,
                    lightBg = Color(0xFFFFEBEE),
                    lightTint = Color(0xFFC62828),
                    darkBg = Color(0xFF381B1D),
                    darkTint = Color(0xFFF87171)
                )
            }
            lower.contains("educ") || lower.contains("school") || lower.contains("course") -> {
                CategoryVisual(
                    icon = Icons.Outlined.School,
                    lightBg = Color(0xFFE8EAF6),
                    lightTint = Color(0xFF283593),
                    darkBg = Color(0xFF1C2238),
                    darkTint = Color(0xFF818CF8)
                )
            }
            lower.contains("travel") || lower.contains("flight") || lower.contains("holiday") -> {
                CategoryVisual(
                    icon = Icons.Outlined.Flight,
                    lightBg = Color(0xFFE0F7FA),
                    lightTint = Color(0xFF00838F),
                    darkBg = Color(0xFF143033),
                    darkTint = Color(0xFF22D3EE)
                )
            }
            lower.contains("tax") || lower.contains("bill") || lower.contains("insur") -> {
                CategoryVisual(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    lightBg = Color(0xFFF3E5F5),
                    lightTint = Color(0xFF7B1FA2),
                    darkBg = Color(0xFF2F1A33),
                    darkTint = Color(0xFFC084FC)
                )
            }
            else -> {
                CategoryVisual(
                    icon = Icons.Outlined.Category,
                    lightBg = Color(0xFFF1F8E9),
                    lightTint = Color(0xFF558B2F),
                    darkBg = Color(0xFF223120),
                    darkTint = Color(0xFF86EFAC)
                )
            }
        }
    }
}
