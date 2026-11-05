package com.edenpractical.comms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared native design primitives. Visual-only: no networking, device
 * permissions, crypto status authority or retention decisions live here.
 */
internal object FieldPalette {
    val Background = Color(0xFF0D1112)
    val Surface = Color(0xFF161B1D)
    val Raised = Color(0xFF1F2628)
    val Accent = Color(0xFF8FAA6A)
    val AccentAlternate = Color(0xFF6C8A5B)
    val Text = Color(0xFFE6E8E6)
    val TextMuted = Color(0xFF9AA3A3)
}

/** Subtle consistent masthead line, reusable across all navigation screens. */
@Composable
internal fun FieldHeaderRule() {
    Box(
        modifier = Modifier.fillMaxWidth().height(3.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    ) {
        Box(
            modifier = Modifier.width(80.dp).height(3.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * Reusable angular panel with a tiny brand accent. Uses native text/layout
 * children so scaling, touch and accessibility remain Android-managed.
 */
@Composable
internal fun FieldFrame(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (accent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(40.dp).height(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.weight(1f).height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    )
                }
            }
            content()
        }
    }
}
