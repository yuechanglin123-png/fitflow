package com.fitflow.training.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun CompactNote(note: String, onOpen: () -> Unit) {
    Text(
        text = "备注：$note",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun NoteDetailDialog(note: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("完整备注", style = MaterialTheme.typography.titleLarge)
                Text(
                    note,
                    modifier = Modifier.fillMaxWidth().height(160.dp).verticalScroll(rememberScrollState()),
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}
