package com.google.ai.edge.gallery.util

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.core.content.getSystemService
import com.google.ai.edge.gallery.R

/**
 * Utility class for handling accessibility features and providing accessible UI components.
 */
object AccessibilityUtils {

    /**
     * Returns true if accessibility services are enabled on the device.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService<AccessibilityManager>()
        return accessibilityManager?.isEnabled == true || accessibilityManager?.isTouchExplorationEnabled == true
    }

    /**
     * Returns true if the user has requested reduced motion.
     */
    @Composable
    fun isReduceMotionEnabled(): Boolean {
        val context = LocalContext.current
        return remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val enabled = context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_TYPE_MASK ==
                        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                val windowManager = context.getSystemService<android.view.WindowManager>()
                val metrics = windowManager?.defaultDisplay?.metrics
                val isLargeScreen = metrics?.let { it.widthPixels > 1920 || it.heightPixels > 1920 } ?: false
                enabled || isLargeScreen
            } else {
                false
            }
        }
    }

    /**
     * Returns true if the user has requested high contrast text.
     */
    @Composable
    fun isHighContrastTextEnabled(): Boolean {
        val context = LocalContext.current
        return remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                context.resources.configuration.fontScale >= 1.3f
            } else {
                false
            }
        }
    }

    /**
     * Returns the appropriate content description for an image based on its type and state.
     */
    @Composable
    fun getContentDescription(
        label: String,
        isSelected: Boolean = false,
        isEnabled: Boolean = true,
        hasError: Boolean = false
    ): String {
        val context = LocalContext.current
        return remember(label, isSelected, isEnabled, hasError) {
            buildString {
                append(label)
                if (hasError) {
                    append(", ").append(context.getString(R.string.error_state))
                } else if (!isEnabled) {
                    append(", ").append(context.getString(R.string.not_enabled))
                }
                if (isSelected) {
                    append(", ").append(context.getString(R.string.selected))
                }
            }
        }
    }

    /**
     * Returns the appropriate state description for a component based on its state.
     */
    @Composable
    fun getStateDescription(
        isExpanded: Boolean = false,
        isSelected: Boolean = false,
        isChecked: Boolean = false,
        itemCount: Int = 0
    ): String {
        val context = LocalContext.current
        return remember(isExpanded, isSelected, isChecked, itemCount) {
            when {
                isExpanded -> context.getString(R.string.expanded)
                isSelected -> context.getString(R.string.selected)
                isChecked -> context.getString(R.string.checked)
                itemCount > 0 -> context.resources.getQuantityString(
                    R.plurals.item_count,
                    itemCount,
                    itemCount
                )
                else -> ""
            }
        }
    }

    /**
     * Returns the appropriate semantics properties for a clickable element.
     */
    fun Modifier.clickableWithAccessibility(
        label: String,
        onClick: () -> Unit,
        role: Role = Role.Button,
        enabled: Boolean = true,
        onClickLabel: String? = null,
        onLongClickLabel: String? = null,
        onLongClick: (() -> Unit)? = null
    ): Modifier = this.then(
        Modifier.semantics {
            this.contentDescription = label
            this.role = role
            this.isTraversalGroup = true
            this.isHeading = false
            this.stateDescription = if (enabled) "" else "disabled"
            onClickLabel?.let { this.customActions = listOf(CustomAccessibilityAction(it, onClick)) }
            onLongClickLabel?.let { this.customActions = listOf(CustomAccessibilityAction(it, onLongClick ?: {})) }
        }.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick,
            role = role,
            onLongClick = onLongClick
        )
    )

    /**
     * Returns the appropriate semantics properties for an interactive element.
     */
    fun Modifier.interactiveElement(
        label: String,
        stateDescription: String? = null,
        role: Role = Role.Button,
        isEnabled: Boolean = true,
        isSelected: Boolean = false,
        isChecked: Boolean? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null
    ): Modifier = this.then(
        Modifier.semantics {
            this.contentDescription = label
            this.role = role
            this.isTraversalGroup = true
            this.isHeading = false
            this.stateDescription = stateDescription ?: when {
                !isEnabled -> "disabled"
                isSelected -> "selected"
                isChecked == true -> "checked"
                isChecked == false -> "not checked"
                else -> ""
            }
            
            if (onClick != null) {
                onClick(label = null, action = null)
            }
            
            if (onLongClick != null) {
                onLongClick(label = null, action = null)
            }
            
            isSelected?.let { this.isSelected = it }
            isChecked?.let { this.isChecked = it }
        }
    )

    /**
     * Returns the appropriate text style for the current accessibility settings.
     */
    @Composable
    fun getAccessibleTextStyle(
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false
    ) = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (isBold || isHighContrastTextEnabled()) {
            MaterialTheme.typography.bodyLarge.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal
        } else {
            androidx.compose.ui.text.font.FontWeight.Normal
        },
        fontStyle = if (isItalic) {
            androidx.compose.ui.text.font.FontStyle.Italic
        } else {
            androidx.compose.ui.text.font.FontStyle.Normal
        },
        textDecoration = if (isUnderline) {
            androidx.compose.ui.text.style.TextDecoration.Underline
        } else {
            androidx.compose.ui.text.style.TextDecoration.None
        },
        color = if (isHighContrastTextEnabled()) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    /**
     * Returns the appropriate color for text based on the current theme and accessibility settings.
     */
    @Composable
    fun getAccessibleTextColor(
        isEnabled: Boolean = true,
        isError: Boolean = false,
        isSelected: Boolean = false
    ) = when {
        isError -> MaterialTheme.colorScheme.error
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.primary
        isHighContrastTextEnabled() -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    /**
     * Returns the appropriate color for a surface based on the current theme and accessibility settings.
     */
    @Composable
    fun getAccessibleSurfaceColor(
        isSelected: Boolean = false,
        isEnabled: Boolean = true,
        isError: Boolean = false
    ) = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isHighContrastTextEnabled() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    /**
     * Returns the appropriate content color for a surface based on the current theme and accessibility settings.
     */
    @Composable
    fun getAccessibleContentColorFor(
        surfaceColor: androidx.compose.ui.graphics.Color,
        isEnabled: Boolean = true,
        isError: Boolean = false
    ): androidx.compose.ui.graphics.Color {
        return when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            surfaceColor == MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimary
            surfaceColor == MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.onSecondary
            surfaceColor == MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.onTertiary
            surfaceColor == MaterialTheme.colorScheme.primaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer
            surfaceColor == MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
            surfaceColor == MaterialTheme.colorScheme.tertiaryContainer -> MaterialTheme.colorScheme.onTertiaryContainer
            surfaceColor == MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.onError
            surfaceColor == MaterialTheme.colorScheme.errorContainer -> MaterialTheme.colorScheme.onErrorContainer
            surfaceColor == MaterialTheme.colorScheme.background -> MaterialTheme.colorScheme.onBackground
            surfaceColor == MaterialTheme.colorScheme.surface -> MaterialTheme.colorScheme.onSurface
            surfaceColor == MaterialTheme.colorScheme.surfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
            surfaceColor == MaterialTheme.colorScheme.inverseSurface -> MaterialTheme.colorScheme.inverseOnSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
    }
}
