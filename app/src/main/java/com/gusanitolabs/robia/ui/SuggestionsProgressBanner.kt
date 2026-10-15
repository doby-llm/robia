package com.gusanitolabs.robia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R

/** Shown at the top of Browse while vectors for saved garments are still being computed (TS§3.8). */
@Composable
fun SuggestionsProgressBanner(remaining: Int, modifier: Modifier = Modifier) {
    val text = LocalContext.current.resources.getQuantityString(R.plurals.suggestions_preparing_count, remaining, remaining)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
