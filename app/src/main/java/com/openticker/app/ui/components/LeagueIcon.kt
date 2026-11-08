package com.openticker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.League
import com.openticker.app.ui.theme.*

/** Native reproduction of the reference's league shield badges, without network assets. */
@Composable fun LeagueIcon(league: League) {
    Box(Modifier.size(42.dp).background(if(league==League.NFL) Color(0xFF145D63) else Color(0xFF34474A),
        GenericShape { size,_ ->
            moveTo(size.width*.08f,0f);lineTo(size.width*.92f,0f);lineTo(size.width,size.height*.80f)
            lineTo(size.width*.5f,size.height);lineTo(0f,size.height*.80f);close()
        }).semantics { contentDescription="${league.name} league" },contentAlignment=Alignment.Center) {
        Text(if(league==League.NCAAF) "CFB" else league.name,fontFamily=DisplayFont,color=TextStrong,fontSize=13.sp,lineHeight=17.sp)
    }
}
