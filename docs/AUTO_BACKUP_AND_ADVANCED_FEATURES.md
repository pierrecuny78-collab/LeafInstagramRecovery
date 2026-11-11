# LeafInstagramRecovery — sauvegarde locale automatique et feuille de route

## Sauvegarde réellement intégrée sur cette branche

- Une sauvegarde locale est planifiée après la fermeture d'une session d'application (délai de 30 secondes) et une sauvegarde périodique est planifiée toutes les 24 heures par Android WorkManager.
- Les archives ZIP sont stockées dans le répertoire privé de l'application : `files/leaf_backups/`.
- Le mécanisme conserve les 7 dernières archives et écrit un manifeste JSON avec taille et empreinte SHA-256 de chaque fichier inclus.
- Les fichiers temporaires ne sont renommés en archive finale qu'après fermeture correcte du ZIP.
- Les caches, données de session WebView, `no_backup` et anciennes archives sont exclus pour éviter de sauvegarder des secrets de session ou de créer des archives récursives.
- Le traitement est local et ne requiert ni compte LeafVibe, ni réseau, ni service cloud.

## Limites importantes

- WorkManager est différé par Android selon l'économie d'énergie ; 24 h est une périodicité demandée, pas une heure garantie.
- Cette première version sauvegarde les données privées accessibles dans le répertoire de données de l'application. Elle ne copie pas automatiquement les fichiers d'un dossier SAF/carte SD choisi par l'utilisateur, les données d'autres applications, ni les données du serveur Termux.
- La restauration guidée et la copie vers une carte SD ou une clé USB sont des étapes suivantes : ne pas considérer une archive ZIP comme restaurable depuis l'interface tant que le flux de restauration avec validation d'intégrité n'est pas livré.
- Les archives restent dans l'espace privé Android et peuvent être supprimées si l'application est désinstallée. Exporter une copie vers un support externe reste recommandé.

## Améliorations avancées à développer ensuite

1. Écran « Sauvegardes » : date, taille, état, empreinte, export, vérification et restauration avec aperçu.
2. Restauration transactionnelle : validation du manifeste et des SHA-256, extraction en zone temporaire, contrôle de version, confirmation humaine, puis remplacement atomique avec rollback.
3. Destination SAF : carte SD, USB OTG ou dossier partagé choisi par l'utilisateur, sans permission globale de stockage.
4. Politique de rétention configurable, déduplication par empreinte et snapshots différentiels.
5. Chiffrement authentifié des exports avec clé créée/stockée par Android Keystore et phrase de récupération fournie par l'utilisateur ; aucune clé codée en dur.
6. Journal d'audit local, diagnostic des échecs et contrôle de l'espace disponible.
7. Tests automatisés : archive vide, fichiers Unicode, interruption en cours, stockage plein, intégrité altérée, migration de version et restauration sans perte.

## Politique de sûreté

- Ne jamais supprimer ni écraser les données originales pendant une sauvegarde.
- Ne jamais restaurer automatiquement une archive sans action explicite.
- Ne jamais inclure cookies, tokens, mots de passe ou caches WebView.
- Signaler clairement toute sauvegarde partielle ou échouée ; ne jamais afficher « sauvegardé » avant succès.
- Garder les fonctionnalités métier indépendantes du mécanisme de sauvegarde.
