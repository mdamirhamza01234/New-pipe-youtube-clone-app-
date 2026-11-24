package com.youtubeclone.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.download.DownloadCenter
import com.youtubeclone.app.player.DlKind
import com.youtubeclone.app.player.Playback
import org.schabi.newpipe.extractor.stream.StreamInfo

/** NewPipe jaisa download dialog: file name, Video/Audio/Subtitles, quality choose karo. */
@Composable
fun DownloadDialog(info: StreamInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val options = remember(info) { Playback.downloadOptions(info) }
    val kinds = remember(info) { DlKind.values().filter { k -> options.any { it.kind == k } } }
    var kind by remember(info) { mutableStateOf(kinds.firstOrNull() ?: DlKind.VIDEO) }
    val list = options.filter { it.kind == kind }
    var sel by remember(kind) { mutableIntStateOf(0) }
    var name by remember(info) { mutableStateOf(Playback.safeName(info.name)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k -> YtChip(k.label, k == kind) { kind = k } }
                }
                Spacer(Modifier.height(6.dp))
                if (list.isEmpty()) {
                    Text("Is video ke liye koi download option nahi mila.", fontSize = 14.sp)
                } else {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        list.forEachIndexed { i, o ->
                            Row(
                                Modifier.fillMaxWidth().clickable { sel = i }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = sel == i, onClick = { sel = i })
                                Text(o.label, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = list.isNotEmpty() && name.isNotBlank(),
                onClick = {
                    val o = list.getOrNull(sel)
                    if (o != null) {
                        val clean = Playback.safeName(name)
                        val file = if (o.kind == DlKind.AUDIO) "$clean (audio).${o.ext}" else "$clean.${o.ext}"
                        DownloadCenter.enqueue(info.name, file, o.mime, o.urls, o.merge)
                        Toast.makeText(ctx, "Download shuru hua. You → Downloads mein dekhein", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
            ) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
