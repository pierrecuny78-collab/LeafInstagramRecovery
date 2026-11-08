package com.leafvibe.leafinstagramrecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

data class MediaRecord(
    val name: String, val source: String, val hash: String, val bytes: Long,
    val mime: String, val storedName: String, val category: String = "À classer"
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}
data class CatalogRecord(val category: String, val source: String, val storedName: String, val items: Int, val bytes: Long, val hash: String)
data class ImportSummary(val posts: Int = 0, val collections: Int = 0, val music: Int = 0, val copied: Int = 0, val duplicates: Int = 0, val warnings: Int = 0)
private data class ImportResult(val summary: ImportSummary, val newMedia: List<MediaRecord>, val newCatalogs: List<CatalogRecord>)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("leaf_recovery", MODE_PRIVATE) }
    private val media = mutableStateListOf<MediaRecord>()
    private val catalogs = mutableStateListOf<CatalogRecord>()
    private val categories = mutableStateListOf("À classer", "Humour", "Musique", "Sport", "Voyage", "Cuisine", "Animaux", "Tutoriels", "Inspiration", "Mode", "Technologie", "Autres")
    private var treeUri: Uri? = null
    private var status by mutableStateOf("Choisis le dossier de ta carte SD pour commencer.")
    private var summary by mutableStateOf(ImportSummary())
    private var busy by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var selectedCategory by mutableStateOf("Toutes")
    private var videoOnly by mutableStateOf(false)
    private var showNewCategory by mutableStateOf(false)
    private var newCategoryName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        treeUri = prefs.getString("tree", null)?.let(Uri::parse)
        loadIndexes()
        val savedCategories = prefs.getString("categories", null)
        if (savedCategories != null) runCatching {
            val a = JSONArray(savedCategories)
            categories.clear()
            for (i in 0 until a.length()) categories.add(a.getString(i))
        }
        setContent {
            MaterialTheme {
                val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        importZip(it)
                    }
                }
                val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let {
                        runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                        treeUri = it
                        prefs.edit().putString("tree", it.toString()).apply()
                        status = "Carte SD configurée. Les prochains médias seront copiés dedans."
                    }
                }
                val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) importSelectedVideos(uris)
                }
                if (showNewCategory) {
                    AlertDialog(
                        onDismissRequest = { showNewCategory = false },
                        title = { Text("Nouvelle catégorie") },
                        text = { OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("Nom") }, singleLine = true) },
                        confirmButton = {
                            TextButton(onClick = {
                                val name = newCategoryName.trim()
                                if (name.isNotEmpty() && categories.none { it.equals(name, true) }) {
                                    categories.add(name)
                                    saveCategories()
                                    selectedCategory = name
                                }
                                newCategoryName = ""
                                showNewCategory = false
                            }) { Text("Créer") }
                        },
                        dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("Annuler") } }
                    )
                }
                Scaffold(topBar = { TopAppBar(title = { Text("LeafInstagramRecovery") }) }) { padding ->
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Ta bibliothèque de vidéos et publications", style = MaterialTheme.typography.headlineSmall)
                            Text("Hors ligne • stockage SD • classement personnalisable", style = MaterialTheme.typography.bodyMedium)
                        }
                        item {
                            Card {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Stockage sur carte SD", style = MaterialTheme.typography.titleMedium)
                                    Text(if (treeUri == null) "Non configuré : sélectionne la carte SD." else "Dossier de destination enregistré.")
                                    Button(onClick = { folderPicker.launch(treeUri) }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("Choisir / changer le dossier SD")
                                    }
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Archive, null); Spacer(Modifier.width(6.dp)); Text("Importer ZIP")
                                }
                                OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Ajouter vidéos")
                                }
                            }
                        }
                        item {
                            Card {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("État et statistiques", style = MaterialTheme.typography.titleMedium)
                                    Text(status)
                                    Text("Vidéos : ${media.count { it.isVideo }} • Images : ${media.count { !it.isVideo }} • Total : ${media.size}")
                                    Text("ZIP : ${summary.posts} publications • ${summary.collections} collections • ${summary.music} musiques")
                                    Text("Dernier import : ${summary.copied} copiés • ${summary.duplicates} doublons • ${summary.warnings} alertes")
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                                label = { Text("Rechercher un fichier ou une catégorie") }
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !videoOnly, onClick = { videoOnly = false }, label = { Text("Tout") })
                                FilterChip(selected = videoOnly, onClick = { videoOnly = true }, label = { Text("Vidéos") })
                                FilterChip(selected = selectedCategory == "Toutes", onClick = { selectedCategory = "Toutes" }, label = { Text("Toutes catégories") })
                            }
                        }
                        item { Text("Catégories", style = MaterialTheme.typography.titleLarge) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.chunked(3).forEach { rowCategories ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        rowCategories.forEach { cat ->
                                            FilterChip(
                                                selected = selectedCategory == cat,
                                                onClick = { selectedCategory = if (selectedCategory == cat) "Toutes" else cat },
                                                label = { Text(cat) }
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(onClick = { showNewCategory = true }) { Text("+ Créer une catégorie") }
                            }
                        }
                        item { Text("Bibliothèque (${filteredMedia().size})", style = MaterialTheme.typography.titleLarge) }
                        if (filteredMedia().isEmpty()) item {
                            Text(if (media.isEmpty()) "Aucun fichier pour le moment. Ajoute des vidéos depuis ton téléphone ou importe une archive contenant les fichiers." else "Aucun résultat pour ces filtres.")
                        }
                        items(filteredMedia(), key = { it.hash }) { record ->
                            var menuOpen by remember(record.hash) { mutableStateOf(false) }
                            Card {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(if (record.isVideo) "▶  ${record.name}" else "▧  ${record.name}", style = MaterialTheme.typography.titleSmall)
                                    Text("${record.category} • ${record.bytes / 1024} Ko • ${record.mime}", style = MaterialTheme.typography.bodySmall)
                                    Text("Fichier SD : ${record.storedName}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { openMedia(record) }) { Text(if (record.isVideo) "Lire" else "Ouvrir") }
                                        OutlinedButton(onClick = { menuOpen = true }) { Text("Classer") }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            categories.forEach { cat ->
                                                DropdownMenuItem(text = { Text(cat) }, onClick = {
                                                    val index = media.indexOfFirst { it.hash == record.hash }
                                                    if (index >= 0) media[index] = record.copy(category = cat)
                                                    persistIndexes()
                                                    menuOpen = false
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Catalogues importés (${catalogs.size})", style = MaterialTheme.typography.titleLarge)
                        }
                        items(catalogs, key = { it.hash }) { c ->
                            ListItem(headlineContent = { Text(c.category) }, supportingContent = { Text("${c.items} entrées • ${c.bytes / 1024} Ko") })
                        }
                        item {
                            Text(
                                "Important : l'export Instagram contient généralement des liens et des métadonnées, pas les fichiers vidéo. Les vidéos doivent être ajoutées depuis des fichiers réellement accessibles ou être présentes dans l'archive. L'application ne contourne pas les accès privés ni les protections Instagram. Les originaux ne sont jamais supprimés automatiquement.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    private fun filteredMedia(): List<MediaRecord> = media.filter { m ->
        (!videoOnly || m.isVideo) &&
        (selectedCategory == "Toutes" || m.category == selectedCategory) &&
        (query.isBlank() || m.name.contains(query, true) || m.category.contains(query, true) || m.source.contains(query, true))
    }.sortedWith(compareByDescending<MediaRecord> { it.isVideo }.thenBy { it.name.lowercase(Locale.ROOT) })

    private fun openMedia(record: MediaRecord) {
        val root = treeUri?.let { DocumentFile.fromTreeUri(this, it) } ?: return
        val base = root.findFile("LeafInstagramRecovery") ?: root
        val categoryFolder = base.findFile(safeFolder(record.category)) ?: base
        val file = categoryFolder.findFile(record.storedName) ?: base.findFile(record.storedName)
        if (file == null) { status = "Fichier introuvable sur la carte SD. Reconfigure le dossier si nécessaire."; return }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, if (record.isVideo) "video/*" else record.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure { status = "Aucune application compatible pour ouvrir ce fichier." }
    }

    private fun importSelectedVideos(uris: List<Uri>) {
        val destination = treeUri
        if (destination == null) { status = "Choisis d'abord le dossier de la carte SD."; return }
        val existing = media.map { it.hash }.toSet()
        lifecycleScope.launch {
            busy = true
            var copied = 0; var duplicates = 0; var warnings = 0
            try {
                val added = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(this@MainActivity, destination) ?: error("Carte SD inaccessible")
                    val folder = root.findFile("LeafInstagramRecovery")?.takeIf { it.isDirectory } ?: root.createDirectory("LeafInstagramRecovery") ?: error("Création du dossier impossible")
                    val videoFolder = folder.findFile(safeFolder("À classer"))?.takeIf { it.isDirectory } ?: folder.createDirectory(safeFolder("À classer")) ?: error("Création du dossier vidéo impossible")
                    val hashes = existing.toMutableSet()
                    val result = mutableListOf<MediaRecord>()
                    uris.forEach { uri ->
                        var target: DocumentFile? = null
                        try {
                            val name = queryName(uri).ifBlank { "video_${UUID.randomUUID()}.mp4" }
                            val clean = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                            target = videoFolder.createFile(contentResolver.getType(uri) ?: "video/mp4", "${UUID.randomUUID().toString().take(8)}_$clean") ?: error("Création impossible")
                            val digest = MessageDigest.getInstance("SHA-256")
                            var size = 0L
                            contentResolver.openInputStream(uri).use { input ->
                                val source = input ?: error("Lecture impossible")
                                contentResolver.openOutputStream(target.uri, "w").use { output ->
                                    val out = output ?: error("Écriture impossible")
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        val n = source.read(buffer); if (n < 0) break
                                        digest.update(buffer, 0, n); out.write(buffer, 0, n); size += n
                                    }
                                    out.flush()
                                }
                            }
                            val hash = digest.digest().joinToString("") { "%02x".format(it) }
                            if (!hashes.add(hash)) { target.delete(); duplicates++ }
                            else { result.add(MediaRecord(clean, uri.toString(), hash, size, contentResolver.getType(uri) ?: "video/*", target.name ?: clean)); copied++ }
                        } catch (_: Exception) { target?.delete(); warnings++ }
                    }
                    result
                }
                media.addAll(added)
                persistIndexes()
                status = "Ajout terminé : $copied vidéo(s) copiée(s) sur la carte SD, $duplicates doublon(s), $warnings erreur(s)."
                summary = summary.copy(copied = copied, duplicates = duplicates, warnings = warnings)
            } catch (e: Exception) { status = "Échec de la copie : ${e.message}" }
            finally { busy = false }
        }
    }

    private fun queryName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) ?: "" else ""
        } ?: ""
    }.getOrDefault("")

    private fun loadIndexes() {
        runCatching {
            val a = JSONArray(File(filesDir, "media-index.json").readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                media.add(MediaRecord(o.getString("name"), o.getString("source"), o.getString("hash"), o.getLong("bytes"), o.getString("mime"), o.optString("storedName", o.getString("name")), o.optString("category", "À classer")))
            }
        }
        runCatching {
            val a = JSONArray(File(filesDir, "catalog-index.json").readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                catalogs.add(CatalogRecord(o.getString("category"), o.getString("source"), o.getString("storedName"), o.getInt("items"), o.getLong("bytes"), o.getString("hash")))
            }
        }
    }

    private fun persistIndexes() {
        val mediaSnapshot = media.toList()
        val catalogSnapshot = catalogs.toList()
        lifecycleScope.launch(Dispatchers.IO) { saveIndexes(mediaSnapshot, catalogSnapshot) }
    }

    private fun saveIndexes(allMedia: List<MediaRecord>, allCatalogs: List<CatalogRecord>) {
        val ma = JSONArray()
        allMedia.forEach { m -> ma.put(JSONObject().put("name", m.name).put("source", m.source).put("hash", m.hash).put("bytes", m.bytes).put("mime", m.mime).put("storedName", m.storedName).put("category", m.category)) }
        val ca = JSONArray()
        allCatalogs.forEach { c -> ca.put(JSONObject().put("category", c.category).put("source", c.source).put("storedName", c.storedName).put("items", c.items).put("bytes", c.bytes).put("hash", c.hash)) }
        writeAtomically(File(filesDir, "media-index.json"), ma.toString())
        writeAtomically(File(filesDir, "catalog-index.json"), ca.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(content)
        if (target.exists() && !target.delete()) error("Impossible de remplacer l'index")
        if (!temp.renameTo(target)) error("Impossible d'enregistrer l'index")
    }

    private fun saveCategories() { prefs.edit().putString("categories", JSONArray(categories).toString()).apply() }

    private fun importZip(uri: Uri) {
        val destination = treeUri
        if (destination == null) { status = "Sélectionne d'abord le dossier SD."; return }
        val knownHashes = media.map { it.hash }.toSet()
        val knownCatalogHashes = catalogs.map { it.hash }.toSet()
        lifecycleScope.launch {
            busy = true; status = "Analyse de l'archive…"
            try {
                val result = withContext(Dispatchers.IO) { readZip(uri, destination, knownHashes, knownCatalogHashes) }
                media.addAll(result.newMedia); catalogs.addAll(result.newCatalogs)
                summary = result.summary
                val ms = media.toList(); val cs = catalogs.toList()
                withContext(Dispatchers.IO) { saveIndexes(ms, cs) }
                status = "Import ZIP terminé : ${result.summary.copied} média(s) ajouté(s)."
            } catch (e: Exception) { status = "Import interrompu : ${e.message ?: "erreur inconnue"}." }
            finally { busy = false }
        }
    }

    private fun readZip(uri: Uri, destination: Uri, knownMedia: Set<String>, knownCatalogs: Set<String>): ImportResult {
        val root = DocumentFile.fromTreeUri(this, destination) ?: error("Dossier SD inaccessible")
        val folder = root.findFile("LeafInstagramRecovery")?.takeIf { it.isDirectory } ?: root.createDirectory("LeafInstagramRecovery") ?: error("Création du dossier impossible")
        val catalogFolder = folder.findFile("catalogues")?.takeIf { it.isDirectory } ?: folder.createDirectory("catalogues") ?: error("Création du dossier catalogues impossible")
        val mediaHashes = knownMedia.toMutableSet(); val catalogHashes = knownCatalogs.toMutableSet()
        val addedMedia = mutableListOf<MediaRecord>(); val addedCatalogs = mutableListOf<CatalogRecord>()
        var posts = 0; var collections = 0; var music = 0; var copied = 0; var duplicates = 0; var warnings = 0
        contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(BufferedInputStream(input ?: error("Archive illisible"))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val path = entry.name; val lower = path.lowercase(Locale.ROOT)
                        val cat = when {
                            lower.endsWith("saved_posts.json") -> "Publications enregistrées"
                            lower.endsWith("saved_collections.json") -> "Collections enregistrées"
                            lower.endsWith("saved_music.json") -> "Musiques enregistrées"
                            else -> null
                        }
                        if (cat != null) {
                            try {
                                val bytes = readEntryBounded(zip, 64 * 1024 * 1024); val hash = sha256(bytes)
                                val count = countItems(bytes.toString(Charsets.UTF_8))
                                when (cat) { "Publications enregistrées" -> posts = count; "Collections enregistrées" -> collections = count; else -> music = count }
                                if (catalogHashes.add(hash)) {
                                    val name = "catalogue_${hash.take(16)}.json"
                                    val file = catalogFolder.createFile("application/json", name) ?: error("Création catalogue impossible")
                                    try {
                                        contentResolver.openOutputStream(file.uri, "w").use { (it ?: error("Écriture impossible")).write(bytes) }
                                        addedCatalogs.add(CatalogRecord(cat, path, file.name ?: name, count, bytes.size.toLong(), hash))
                                    } catch (e: Exception) { file.delete(); throw e }
                                } else duplicates++
                            } catch (_: Exception) { warnings++ }
                        } else if (isMedia(lower)) {
                            var created: DocumentFile? = null
                            try {
                                val isVideo = isVideo(lower)
                                val targetFolderName = if (isVideo) safeFolder("À classer") else "Images"
                                val targetFolder = folder.findFile(targetFolderName)?.takeIf { it.isDirectory } ?: folder.createDirectory(targetFolderName) ?: error("Création dossier impossible")
                                val clean = path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "media" }
                                val unique = "${UUID.randomUUID().toString().take(8)}_$clean"
                                created = targetFolder.createFile(mime(lower), unique) ?: error("Création média impossible")
                                val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
                                contentResolver.openOutputStream(created.uri, "w").use { raw ->
                                    val out = raw ?: error("Écriture média impossible"); val buffer = ByteArray(128 * 1024)
                                    while (true) { val n = zip.read(buffer); if (n < 0) break; digest.update(buffer, 0, n); out.write(buffer, 0, n); size += n }
                                    out.flush()
                                }
                                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                                if (!mediaHashes.add(hash)) { created.delete(); duplicates++ }
                                else { addedMedia.add(MediaRecord(clean, path, hash, size, mime(lower), created.name ?: unique, if (isVideo) "À classer" else "Images")); copied++ }
                            } catch (_: Exception) { created?.delete(); warnings++ }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return ImportResult(ImportSummary(posts, collections, music, copied, duplicates, warnings), addedMedia, addedCatalogs)
    }

    private fun readEntryBounded(zip: ZipInputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024); var total = 0
        while (true) { val n = zip.read(buffer); if (n < 0) break; total += n; if (total > maxBytes) error("JSON supérieur à 64 Mo"); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun countItems(content: String): Int = runCatching {
        when (val v = org.json.JSONTokener(content).nextValue()) {
            is JSONArray -> v.length()
            is JSONObject -> { var n = 0; val it = v.keys(); while (it.hasNext()) { val x = v.opt(it.next()); if (x is JSONArray) n = maxOf(n, x.length()) }; n }
            else -> 0
        }
    }.getOrDefault(0)
    private fun isVideo(path: String) = listOf(".mp4", ".mov", ".m4v", ".webm", ".mkv", ".3gp").any { path.endsWith(it) }
    private fun isMedia(path: String) = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".mp4", ".mov", ".m4v", ".webm", ".mkv", ".3gp").any { path.endsWith(it) }
    private fun mime(path: String) = when {
        isVideo(path) -> when { path.endsWith(".webm") -> "video/webm"; path.endsWith(".mov") -> "video/quicktime"; path.endsWith(".3gp") -> "video/3gpp"; else -> "video/mp4" }
        path.endsWith(".png") -> "image/png"; path.endsWith(".webp") -> "image/webp"; path.endsWith(".gif") -> "image/gif"; path.endsWith(".heic") -> "image/heic"; else -> "image/jpeg"
    }
    private fun safeFolder(name: String) = name.replace(Regex("[^A-Za-z0-9 _-]"), "_")
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
