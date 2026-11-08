# État d'implémentation

## Version 0.2.0 — socle initial
- [x] Projet Android Kotlin / Jetpack Compose.
- [x] Sélecteur SAF pour archive ZIP.
- [x] Sélecteur SAF pour dossier de destination SD.
- [x] Lecture de saved_posts.json, saved_collections.json et saved_music.json pour compter les éléments.
- [x] Détection des fichiers image et vidéo présents dans le ZIP.
- [x] Empreintes SHA-256 et détection de doublons au cours des imports.
- [x] Copie vers un dossier LeafInstagramRecovery sans remplacement volontaire.
- [x] Index JSON local persistant.
- [x] Workflow GitHub Actions pour produire une APK debug.

## À terminer avant une version complète
- [ ] Base Room et journal d'import détaillé.
- [ ] Traitement en flux des très gros médias, sans chargement complet en mémoire.
- [ ] Export JSON/CSV des métadonnées et catégories.
- [ ] Galerie avec aperçu photo/vidéo, recherche et filtres.
- [ ] Prévisualisation et correction d'encodage des textes.
- [ ] Relation collections-publications uniquement si démontrée par les données exportées.
- [ ] Reprise robuste d'un import interrompu et vérification des copies.
- [ ] Tests unitaires ZIP/JSON/SHA-256 et tests sur appareil.
- [ ] Génération d'une APK release signée par une clé fournie et contrôlée par l'utilisateur.

## Limites connues
Les premières fonctions d'interface et d'import constituent un MVP technique. Elles ne représentent pas encore l'ensemble du cahier des charges. Les fichiers JSON sont actuellement comptés, pas encore intégralement convertis en fiches consultables.
