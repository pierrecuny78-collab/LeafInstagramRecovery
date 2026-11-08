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
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

data class MediaRecord(val name: String, val source: String, val hash: String, val bytes: Long, val mime: String, val storedName: String)
data class CatalogRecord(val category: String, val source: String, val storedName: String, val items: Int, val bytes: Long, val hash: String)
data class ImportSummary(val posts: Int = 0, val collections: Int = 0, val music: Int = 0, val copied: Int = 0, val duplicates: Int = 0, val warnings: Int = 0)

private data class ImportResult(val summary: ImportSummary, val newMedia: List<MediaRecord>, val newCatalogs: List<CatalogRecord>)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("leaf_recovery", MODE_PRIVATE) }
    private val media = mutableStateListOf<MediaRecord>()
    private val catalogs = mutableStateListOf<CatalogRecord>()
    private var treeUri: Uri? = null
    private var status by mutableStateOf("Choisis un dossier sur ta carte SD, puis importe ton archive Instagram.")
    private var summary by mutableStateOf(ImportSummary())
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        treeUri = prefs.getString("tree", null)?.let(Uri::parse)
        loadIndexes()
        setContent {
            MaterialTheme {
                val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        importZip(it)
                    }
                }
                val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        treeUri = it
                        prefs.edit().putString("tree", it.toString()).apply()
                        status = "Dossier de stockage enregistré."
                    }
                }
                Scaffold(topBar = { TopAppBar(title = { Text("LeafInstagramRecovery") }) }) { padding ->
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Bibliothèque Instagram hors ligne", style = MaterialTheme.typography.headlineSmall)
                            Text("Version 0.2.0 • Import non destructif")
                        }
                        item {
                            Card {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Stockage sur carte SD", style = MaterialTheme.typography.titleMedium)
                                    Text(if (treeUri == null) "Aucun dossier choisi" else "Dossier de destination configuré")
                                    Button(onClick = { folderPicker.launch(treeUri) }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("Choisir la carte SD / dossier")
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp))
                                Text(if (busy) "Import en cours…" else "Importer une archive ZIP Instagram")
                            }
                        }
                        item {
                            Card {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("État du dernier import", style = MaterialTheme.typography.titleMedium)
                                    Text(status)
                                    Text("Publications : ${summary.posts} • Collections : ${summary.collections} • Musiques : ${summary.music}")
                                    Text("Médias copiés : ${summary.copied} • Doublons : ${summary.duplicates} • Alertes : ${summary.warnings}")
                                }
                            }
                        }
                        item { Text("Catalogues Instagram enregistrés (${catalogs.size})", style = MaterialTheme.typography.titleLarge) }
                        if (catalogs.isEmpty()) item { Text("Aucun catalogue importé pour le moment.") }
                        items(catalogs, key = { it.hash }) { c ->
                            ListItem(
                                headlineContent = { Text(c.category) },
                                supportingContent = { Text("${c.items} éléments • ${c.bytes / 1024} Ko • ${c.storedName}") }
                            )
                        }
                        item { Text("Galerie des médias (${media.size})", style = MaterialTheme.typography.titleLarge) }
                        if (media.isEmpty()) item { Text("Aucun média indexé pour le moment.") }
                        items(media, key = { it.hash }) { m ->
                            ListItem(
                                headlineContent = { Text(m.name) },
                                supportingContent = { Text("${m.mime} • ${m.bytes / 1024} Ko • SHA-256 ${m.hash.take(16)}…") }
                            )
                        }
                        item {
                            Text(
                                "Protection : les archives sources restent intactes. Aucun fichier existant n'est supprimé ou remplacé. Les doublons sont détectés par SHA-256. Une URL Instagram n'est pas une copie du média ; un contenu absent de l'archive et de toute sauvegarde ne peut pas être restauré.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadIndexes() {
        runCatching {
            val file = File(filesDir, "media-index.json")
            if (file.exists()) {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    media.add(MediaRecord(o.getString("name"), o.getString("source"), o.getString("hash"), o.getLong("bytes"), o.getString("mime"), o.optString("storedName", o.getString("name"))))
                }
            }
        }
        runCatching {
            val file = File(filesDir, "catalog-index.json")
            if (file.exists()) {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    catalogs.add(CatalogRecord(o.getString("category"), o.getString("source"), o.getString("storedName"), o.getInt("items"), o.getLong("bytes"), o.getString("hash")))
                }
            }
        }
    }

    private fun saveIndexes(allMedia: List<MediaRecord>, allCatalogs: List<CatalogRecord>) {
        val mediaArray = JSONArray()
        allMedia.forEach { m ->
            mediaArray.put(JSONObject().put("name", m.name).put("source", m.source).put("hash", m.hash).put("bytes", m.bytes).put("mime", m.mime).put("storedName", m.storedName))
        }
        val catalogArray = JSONArray()
        allCatalogs.forEach { c ->
            catalogArray.put(JSONObject().put("category", c.category).put("source", c.source).put("storedName", c.storedName).put("items", c.items).put("bytes", c.bytes).put("hash", c.hash))
        }
        writeAtomically(File(filesDir, "media-index.json"), mediaArray.toString())
        writeAtomically(File(filesDir, "catalog-index.json"), catalogArray.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(content)
        if (target.exists() && !target.delete()) error("Impossible de remplacer l'index temporaire")
        if (!temp.renameTo(target)) error("Impossible d'enregistrer l'index")
    }

    private fun importZip(uri: Uri) {
        val destination = treeUri
        if (destination == null) {
            status = "Sélectionne d'abord le dossier de stockage SD."
            return
        }
        val knownHashes = media.map { it.hash }.toSet()
        val knownCatalogHashes = catalogs.map { it.hash }.toSet()
        lifecycleScope.launch {
            busy = true
            status = "Lecture et vérification de l'archive…"
            try {
                val result = withContext(Dispatchers.IO) {
                    readZip(uri, destination, knownHashes, knownCatalogHashes)
                }
                media.addAll(result.newMedia)
                catalogs.addAll(result.newCatalogs)
                summary = result.summary
                val mediaSnapshot = media.toList()
                val catalogSnapshot = catalogs.toList()
                withContext(Dispatchers.IO) { saveIndexes(mediaSnapshot, catalogSnapshot) }
                status = "Import terminé. ${result.newMedia.size} média(s) ajouté(s), ${result.newCatalogs.size} catalogue(s) archivé(s)."
            } catch (e: Exception) {
                status = "Import interrompu : ${e.message ?: "erreur inconnue"}. Les fichiers déjà copiés restent conservés ; relance l'import pour reprendre."
            } finally {
                busy = false
            }
        }
    }

    private fun readZip(uri: Uri, destination: Uri, knownMedia: Set<String>, knownCatalogs: Set<String>): ImportResult {
        val root = DocumentFile.fromTreeUri(this, destination) ?: error("Dossier de destination inaccessible")
        val folder = root.findFile("LeafInstagramRecovery")?.takeIf { it.isDirectory }
            ?: root.createDirectory("LeafInstagramRecovery") ?: error("Création du dossier impossible")
        val catalogFolder = folder.findFile("catalogues")?.takeIf { it.isDirectory }
            ?: folder.createDirectory("catalogues") ?: error("Création du dossier catalogues impossible")

        val mediaHashes = knownMedia.toMutableSet()
        val catalogHashes = knownCatalogs.toMutableSet()
        val addedMedia = mutableListOf<MediaRecord>()
        val addedCatalogs = mutableListOf<CatalogRecord>()
        var posts = 0; var collections = 0; var music = 0
        var copied = 0; var duplicates = 0; var warnings = 0

        contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(BufferedInputStream(input ?: error("Archive illisible"))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val path = entry.name
                        val lower = path.lowercase(Locale.ROOT)
                        val catalogCategory = when {
                            lower.endsWith("saved_posts.json") -> "Publications enregistrées"
                            lower.endsWith("saved_collections.json") -> "Collections enregistrées"
                            lower.endsWith("saved_music.json") -> "Musiques enregistrées"
                            else -> null
                        }
                        if (catalogCategory != null) {
                            try {
                                val bytes = readEntryBounded(zip, 64 * 1024 * 1024)
                                val hash = sha256(bytes)
                                val count = countItems(bytes.toString(Charsets.UTF_8))
                                when (catalogCategory) {
                                    "Publications enregistrées" -> posts = count
                                    "Collections enregistrées" -> collections = count
                                    "Musiques enregistrées" -> music = count
                                }
                                if (catalogHashes.add(hash)) {
                                    val stored = "catalogue_${hash.take(16)}.json"
                                    val file = catalogFolder.createFile("application/json", stored)
                                    if (file == null) warnings++ else {
                                        try {
                                            contentResolver.openOutputStream(file.uri, "w").use { out ->
                                                (out ?: error("Écriture du catalogue impossible")).write(bytes)
                                            }
                                            addedCatalogs.add(CatalogRecord(catalogCategory, path, file.name ?: stored, count, bytes.size.toLong(), hash))
                                        } catch (e: Exception) {
                                            file.delete()
                                            throw e
                                        }
                                    }
                                } else duplicates++
                            } catch (e: Exception) {
                                warnings++
                            }
                        } else if (isMedia(lower)) {
                            var created: DocumentFile? = null
                            try {
                                val cleanName = path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "media" }
                                val uniqueName = UUID.randomUUID().toString().take(8) + "_" + cleanName
                                created = folder.createFile(mime(lower), uniqueName) ?: error("Création du fichier impossible")
                                val digest = MessageDigest.getInstance("SHA-256")
                                var size = 0L
                                contentResolver.openOutputStream(created.uri, "w").use { rawOut ->
                                    val out = rawOut ?: error("Ouverture du fichier de destination impossible")
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        digest.update(buffer, 0, read)
                                        out.write(buffer, 0, read)
                                        size += read
                                    }
                                    out.flush()
                                }
                                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                                if (!mediaHashes.add(hash)) {
                                    created.delete()
                                    duplicates++
                                } else {
                                    addedMedia.add(MediaRecord(cleanName, path, hash, size, mime(lower), created.name ?: uniqueName))
                                    copied++
                                }
                            } catch (e: Exception) {
                                created?.delete()
                                warnings++
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val resultSummary = ImportSummary(posts, collections, music, copied, duplicates, warnings)
        return ImportResult(resultSummary, addedMedia, addedCatalogs)
    }

    private fun readEntryBounded(zip: ZipInputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) error("Fichier JSON trop volumineux (limite de sécurité : 64 Mo)")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun countItems(content: String): Int = runCatching {
        when (val value = org.json.JSONTokener(content).nextValue()) {
            is JSONArray -> value.length()
            is JSONObject -> {
                var count = 0
                val keys = value.keys()
                while (keys.hasNext()) {
                    val item = value.opt(keys.next())
                    if (item is JSONArray) count = maxOf(count, item.length())
                }
                count
            }
            else -> 0
        }
    }.getOrDefault(0)

    private fun isMedia(path: String) = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".mp4", ".mov", ".m4v", ".webm", ".heic").any { path.endsWith(it) }

    private fun mime(path: String) = when {
        listOf(".mp4", ".mov", ".m4v", ".webm").any { path.endsWith(it) } -> "video/*"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".gif") -> "image/gif"
        path.endsWith(".heic") -> "image/heic"
        else -> "image/jpeg"
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
