package com.openticker.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*

@Composable fun FeedHealthDialog(feeds: List<FeedHealth>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Data feed status")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Network connectivity does not guarantee fresh data. Feeds retry automatically while the app is visible.",color=Muted,fontSize=12.sp)
            feeds.forEach { feed ->
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("${feed.name} · ${feed.condition.label}",color=if(feed.needsAttention) Color(0xFFFFCA82) else Mint,fontSize=14.sp)
                    Text(feed.detail,fontSize=12.sp)
                    Text(feedHealthTime(feed.updated),color=Muted,fontSize=10.sp)
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Close")}})
}
