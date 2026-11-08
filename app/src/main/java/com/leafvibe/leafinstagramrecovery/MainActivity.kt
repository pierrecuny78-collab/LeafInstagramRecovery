package com.leafvibe.leafinstagramrecovery

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class MediaRecord(val name:String,val source:String,val hash:String,val bytes:Long,val mime:String)
data class ImportSummary(val posts:Int=0,val collections:Int=0,val music:Int=0,val copied:Int=0,val duplicates:Int=0,val warnings:Int=0)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity:ComponentActivity(){
 private val prefs by lazy{getSharedPreferences("leaf_recovery",MODE_PRIVATE)}
 private val media= mutableListOf<MediaRecord>()
 private var galleryRevision by mutableIntStateOf(0)
 private var treeUri:Uri?=null
 private var status by mutableStateOf("Choisis un dossier sur ta carte SD, puis importe ton archive Instagram.")
 private var summary by mutableStateOf(ImportSummary())
 private var busy by mutableStateOf(false)

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  treeUri=prefs.getString("tree",null)?.let(Uri::parse)
  loadIndex()
  setContent{
   MaterialTheme{
    val zipPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);importZip(it)}}
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->u?.let{contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);treeUri=it;prefs.edit().putString("tree",it.toString()).apply();status="Dossier de stockage enregistré."}}
    Scaffold(topBar={TopAppBar(title={Text("LeafInstagramRecovery")})}){pad->
     LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      item{Text("Bibliothèque Instagram hors ligne",style=MaterialTheme.typography.headlineSmall);Text("Version 0.2.0 • Import non destructif")}
      item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Text("Stockage SD",style=MaterialTheme.typography.titleMedium)
       Text(if(treeUri==null)"Aucun dossier choisi" else "Dossier de destination configuré")
       Button(onClick={folderPicker.launch(null)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Folder,null);Spacer(Modifier.width(8.dp));Text("Choisir la carte SD / dossier")}
      }}}
      item{Button(onClick={zipPicker.launch(arrayOf("application/zip","application/x-zip-compressed","*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Archive,null);Spacer(Modifier.width(8.dp));Text(if(busy)"Import en cours…" else "Importer une archive ZIP Instagram")}}
      item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Text("Dernier import",style=MaterialTheme.typography.titleMedium);Text(status)
       Text("Publications : "+summary.posts+"   Collections : "+summary.collections+"   Musiques : "+summary.music)
       Text("Médias copiés : "+summary.copied+"   Doublons : "+summary.duplicates+"   Alertes : "+summary.warnings)
      }}}
      item{Text("Galerie indexée ("+media.size+") • "+galleryRevision,style=MaterialTheme.typography.titleLarge)}
      if(media.isEmpty())item{Text("Aucun média indexé pour le moment.")}
      items(media.size){i->val m=media[i];ListItem(headlineContent={Text(m.name)},supportingContent={Text(m.mime+" • "+(m.bytes/1024)+" Ko • SHA-256 "+m.hash.take(12)+"…")})}
      item{Text("Protection : aucun fichier sain existant n'est supprimé ou remplacé automatiquement. Une URL Instagram n'est pas une copie du média. Un contenu supprimé sans sauvegarde locale ne peut pas être restauré.",style=MaterialTheme.typography.bodySmall)}
     }
    }
   }
  }
 }

 private fun loadIndex(){runCatching{
  val f=java.io.File(filesDir,"media-index.json")
  if(f.exists()){val a=JSONArray(f.readText());for(i in 0 until a.length()){val o=a.getJSONObject(i);media.add(MediaRecord(o.getString("name"),o.getString("source"),o.getString("hash"),o.getLong("bytes"),o.getString("mime")))}}
 }}
 private fun saveIndex(){val a=JSONArray();media.forEach{a.put(JSONObject().put("name",it.name).put("source",it.source).put("hash",it.hash).put("bytes",it.bytes).put("mime",it.mime))};java.io.File(filesDir,"media-index.json").writeText(a.toString())}
 private fun importZip(uri:Uri){
  if(treeUri==null){status="Sélectionne d'abord le dossier de stockage SD.";return}
  lifecycleScope.launch{busy=true;status="Lecture de l'archive…";try{summary=withContext(Dispatchers.IO){readZip(uri)};galleryRevision++;status="Import terminé. Les fichiers originaux n'ont pas été modifiés."}catch(e:Exception){status="Import interrompu : "+(e.message?:"erreur inconnue")+". Tu peux le relancer."}finally{busy=false}}
 }
 private fun readZip(uri:Uri):ImportSummary{
  val root=DocumentFile.fromTreeUri(this,treeUri!!)?:error("Dossier de destination inaccessible")
  val folder=root.findFile("LeafInstagramRecovery")?.takeIf{it.isDirectory}?:root.createDirectory("LeafInstagramRecovery")?:error("Création du dossier impossible")
  val hashes=media.map{it.hash}.toMutableSet()
  var posts=0;var collections=0;var music=0;var copied=0;var duplicates=0;var warnings=0
  contentResolver.openInputStream(uri).use{input->ZipInputStream(BufferedInputStream(input?:error("Archive illisible"))).use{zip->
   while(true){val entry=zip.nextEntry?:break
    if(!entry.isDirectory){val path=entry.name;val lower=path.lowercase()
     if(lower.endsWith(".json")){
      val text=zip.readBytes().toString(Charsets.UTF_8)
      when{lower.endsWith("saved_posts.json")->posts=countItems(text);lower.endsWith("saved_collections.json")->collections=countItems(text);lower.endsWith("saved_music.json")->music=countItems(text)}
     }else if(isMedia(lower)){
      val bytes=zip.readBytes();val hash=sha(bytes)
      if(hashes.add(hash)){
       val name=path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"),"_").ifBlank{"media_"+hash.take(10)}
       val doc=folder.createFile(mime(lower),hash.take(12)+"_"+name)
       if(doc!=null){contentResolver.openOutputStream(doc.uri,"w")?.use{it.write(bytes)}?:error("Écriture impossible");media.add(MediaRecord(name,path,hash,bytes.size.toLong(),mime(lower)));copied++}else warnings++
      }else duplicates++
     }
    }
    zip.closeEntry()
   }
  }}
  saveIndex();return ImportSummary(posts,collections,music,copied,duplicates,warnings)
 }
 private fun countItems(s:String):Int=runCatching{when(val v=org.json.JSONTokener(s).nextValue()){is JSONArray->v.length();is JSONObject->{var n=0;val k=v.keys();while(k.hasNext()){val x=v.opt(k.next());if(x is JSONArray)n=maxOf(n,x.length())};n};else->0}}.getOrDefault(0)
 private fun isMedia(p:String)=listOf(".jpg",".jpeg",".png",".webp",".gif",".mp4",".mov",".m4v",".webm",".heic").any{p.endsWith(it)}
 private fun mime(p:String)=if(listOf(".mp4",".mov",".m4v",".webm").any{p.endsWith(it)})"video/*" else when{p.endsWith(".png")->"image/png";p.endsWith(".webp")->"image/webp";p.endsWith(".gif")->"image/gif";p.endsWith(".heic")->"image/heic";else->"image/jpeg"}
 private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
}
