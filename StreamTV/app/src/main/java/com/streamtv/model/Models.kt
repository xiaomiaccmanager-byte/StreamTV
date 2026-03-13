package com.streamtv.model
import java.util.Date
data class Channel(val id:Int,val name:String,val emoji:String,val sourceCategory:String,val color:Int,var shows:List<Show>=emptyList())
data class Show(val title:String,val posterUrl:String,val year:String,val description:String,val episodes:List<Episode>,val genres:List<String>,val rating:String="")
data class Episode(val title:String,val streamUrl:String,val duration:Int,val season:Int=0,val episode:Int=0,val pageUrl:String="")
data class EpgItem(val show:Show,val episode:Episode,val startTime:Date,val endTime:Date){
  val isLive:Boolean get(){val n=Date();return n.after(startTime)&&n.before(endTime)}
  val progressPercent:Int get(){if(!isLive)return 0;val n=Date().time;val t=endTime.time-startTime.time;val e=n-startTime.time;return((e.toFloat()/t)*100).toInt().coerceIn(0,100)}
  val minutesLeft:Int get(){return((endTime.time-Date().time)/60000).toInt().coerceAtLeast(0)}
}
data class SourceConfig(val url:String,val name:String)
