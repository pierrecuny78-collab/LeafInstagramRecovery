# LeafInstagramRecovery — feuille de route ambitieuse

## Identité
- Icône Android dédiée, palette LeafVibe Oasis #206B4D et Noyer #6B4935 avec accent prune.

## Évolutions proposées
- Archive locale indépendante des services Instagram.
- Import multi-fichiers JSON avec aperçu et validation avant intégration.
- Export complet versionné et restauration depuis sauvegarde.
- Catalogue de liens, descriptions, médias locaux et métadonnées.
- Détection de doublons sans suppression automatique.
- Galerie locale avec filtres, recherche et état de disponibilité des médias.
- Historique des imports, exports et restaurations.

## Protection des données
- Créer une sauvegarde avant toute restauration ou fusion.
- Fusionner sans écraser les catégories existantes.
- Conserver les entrées inconnues et les champs JSON non reconnus.
- Aucune suppression automatique de collection, lien ou média.
- Prévisualiser les conflits et demander validation avant remplacement.
- Ne télécharger que les contenus accessibles et autorisés.

## Hors ligne et tests
- Le catalogue et les fichiers déjà archivés doivent rester consultables hors ligne.
- Tests : import/export aller-retour, doublons, catégories, liens invalides, restauration et gros fichiers.
- Les fonctions proposées restent à implémenter et valider.
