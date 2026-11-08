# État d'implémentation

## Version 0.2.0 — socle Android en construction

- [x] Projet Android Kotlin / Jetpack Compose.
- [x] Sélecteur SAF pour archive ZIP et conservation des permissions URI.
- [x] Sélecteur SAF pour dossier de destination sur carte SD.
- [x] Lecture des catalogues Instagram `saved_posts.json`, `saved_collections.json` et `saved_music.json`.
- [x] Conservation des fichiers JSON bruts reconnus dans `LeafInstagramRecovery/catalogues/` sur le stockage choisi.
- [x] Affichage du nombre d'éléments et index local des catalogues archivés.
- [x] Détection des fichiers image et vidéo présents dans le ZIP.
- [x] Copie des médias en flux par blocs (pas de chargement intégral des gros médias en mémoire).
- [x] Calcul SHA-256 pendant la copie et détection des doublons.
- [x] Suppression uniquement du fichier nouvellement créé si sa copie échoue ou s'il est un doublon ; aucun fichier préexistant n'est touché.
- [x] Index JSON local persistant pour les médias et catalogues.
- [x] Écriture atomique des index locaux.
- [x] Workflow GitHub Actions pour produire une APK debug ; première exécution confirmée avec artefact.

## À terminer avant une version complète

- [ ] Base de données structurée Room et journal détaillé de chaque import.
- [ ] Reprise transactionnelle complète des imports interrompus et vérification post-écriture des médias.
- [ ] Export JSON/CSV de toutes les métadonnées et catégories.
- [ ] Galerie avec aperçu photo/vidéo, recherche, filtres et ouverture des fichiers.
- [ ] Conversion des catalogues JSON en fiches consultables (légendes, dates, liens, identifiants, artistes).
- [ ] Prévisualisation et correction d'encodage des textes sans altérer les originaux.
- [ ] Catégories personnalisées et liens collections-publications uniquement si démontrés par l'archive.
- [ ] Tests unitaires ZIP/JSON/SHA-256 et tests sur appareil Android.
- [ ] Génération d'une APK release signée par une clé fournie et contrôlée par l'utilisateur.

## Protections et limites

Les archives sources restent intactes. Les nouveaux médias sont écrits sous des noms uniques. Un doublon est identifié par son SHA-256 ; le fichier nouvellement copié est alors supprimé, jamais l'original. Les catalogues JSON reconnus sont conservés bruts sur le stockage sélectionné, afin de ne pas perdre les champs encore non interprétés.

La limite de lecture des fichiers JSON individuels est de 64 Mo. Les fichiers médias sont copiés en flux par blocs. Les URL Instagram ne sont pas des copies des médias. Un contenu absent de l'archive et de toute autre sauvegarde ne peut pas être restauré.

La version actuelle reste un MVP technique : l'APK peut être compilée, mais les fonctions listées dans « À terminer » ne doivent pas être présentées comme disponibles.
