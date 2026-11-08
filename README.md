# LeafInstagramRecovery

Bibliothèque Android hors ligne pour importer, préserver et organiser les médias et métadonnées que l'utilisateur possède ou peut légalement consulter.

## Fonctionnalités de la version 0.3.0
- Import d'une archive ZIP d'export Instagram.
- Archivage local des catalogues `saved_posts.json`, `saved_collections.json` et `saved_music.json`.
- Import direct de vidéos choisies dans le sélecteur Android.
- Copie des médias sur un dossier choisi par l'utilisateur, y compris une carte microSD.
- Galerie défilante, filtre vidéos/tous médias, recherche et catégories personnalisables.
- Catégories initiales : À classer, Humour, Musique, Sport, Voyage, Cuisine, Animaux, Tutoriels, Inspiration, Mode, Technologie et Autres.
- Classement d'une vidéo dans une catégorie sans déplacer ni renommer le fichier.
- Ouverture des vidéos avec le lecteur installé sur Android.
- Détection des doublons SHA-256 et conservation des originaux.
- Index locaux persistants et reprise possible en réimportant une archive.

## Organisation du stockage
Les médias sont copiés dans `LeafInstagramRecovery/` sur le support choisi. Les catégories sont conservées dans l'index de l'application pour éviter de casser les chemins lorsque l'utilisateur reclasse un fichier. Les fichiers restent sur la carte SD après désinstallation si le dossier choisi est un dossier partagé, non privé de l'application.

## Limites importantes
L'export Instagram des éléments enregistrés fournit principalement des liens et des métadonnées. Un lien vers un Reel ou une publication n'est pas le fichier vidéo. L'application ne peut classer ni sauvegarder une vidéo absente de l'archive ou des fichiers accessibles à l'utilisateur. Elle ne contourne pas les connexions, contenus privés, DRM ou protections d'accès.

## Compilation
GitHub Actions construit l'APK de débogage à chaque push sur `main`. Ouvrir l'exécution terminée, puis télécharger l'artefact `LeafInstagramRecovery-0.3.0-debug` dans la section Artifacts.
