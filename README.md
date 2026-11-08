# LeafInstagramRecovery

Application Android hors ligne pour importer, organiser et préserver les données exportées par Instagram.

## Objectifs
- Importer les archives ZIP Instagram avec le sélecteur Android.
- Lire les exports JSON : publications enregistrées, collections et musiques.
- Préserver les fichiers originaux et éviter toute suppression automatique.
- Stocker les médias sur un emplacement choisi via Storage Access Framework (carte SD prise en charge).
- Calculer les empreintes SHA-256 et détecter les doublons.
- Construire une galerie locale consultable hors ligne.
- Exporter les index et métadonnées.
- Générer une APK de débogage avec GitHub Actions.

## Principes de sécurité
- Aucun contournement d'accès Instagram ni récupération de contenus privés sans autorisation.
- Une URL de publication n'est pas une copie du média.
- Un contenu supprimé sans copie locale ou sauvegarde ne peut pas être restauré.
- Les imports sont non destructifs : aucun média sain n'est remplacé ou supprimé automatiquement.

## État
Initialisation du projet — version 0.2.0 en développement.
