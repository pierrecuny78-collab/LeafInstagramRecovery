# Cahier des charges — LeafInstagramRecovery 0.2.0

## 1. Finalité
Application Android personnelle, locale et utilisable hors ligne pour importer les archives officielles de données Instagram, préserver les médias disponibles et rendre les éléments catalogués consultables.

## 2. Périmètre fonctionnel
- Import ZIP depuis le sélecteur Android SAF.
- Analyse des exports JSON saved_posts, saved_collections et saved_music.
- Indexation des médias image/vidéo réellement présents dans l'archive.
- Destination choisie par l'utilisateur, notamment une carte SD.
- Empreinte SHA-256, détection des doublons et journal d'import.
- Galerie locale, recherche et filtres par type/catégorie.
- Export de l'index et des métadonnées.
- Reprise après interruption sans supprimer les fichiers déjà présents.
- Compilation automatisée d'une APK Android via GitHub Actions.

## 3. Règles de préservation
1. Ne jamais supprimer automatiquement un fichier sain.
2. Ne jamais écraser automatiquement un fichier existant.
3. Détecter les doublons par SHA-256, pas uniquement par nom.
4. Garder les originaux de l'archive intacts.
5. Signaler les éléments qui n'ont pas de fichier média dans l'export.
6. Une URL de publication n'est pas un fichier média.
7. Ne pas prétendre restaurer un média supprimé sans copie locale ou sauvegarde.

## 4. Confidentialité et limites
L'application n'accède qu'aux fichiers choisis par l'utilisateur et aux données accessibles normalement à son compte. Aucun contournement de compte privé, aucune collecte de mot de passe ou de cookie et aucun scraping de contenu inaccessible.

## 5. Architecture cible
- Interface : Kotlin + Jetpack Compose.
- Import : Android Storage Access Framework.
- Stockage externe : DocumentFile / URI persistante.
- Index : base locale persistante (migration vers Room prévue).
- Intégrité : SHA-256.
- Build : Gradle + GitHub Actions.

## 6. Compatibilité
Android API 26 minimum, cible Android moderne, fonctionnement hors ligne après installation.
