# Recherche technique — sauvegarde Android LeafVibe

Date de référence : 11 novembre 2026.

## Décisions d'architecture

### 1. Travail en arrière-plan : WorkManager

Le projet utilise WorkManager pour les sauvegardes différées et périodiques, avec un travail unique afin d'éviter d'empiler des demandes identiques. Une périodicité de 24 heures est demandée ; Android peut décaler l'exécution selon Doze, les optimisations batterie et les contraintes. La périodicité minimale prise en charge par PeriodicWorkRequest est de 15 minutes. La sauvegarde locale n'a volontairement aucune contrainte réseau.

Références :
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/reference/androidx/work/PeriodicWorkRequest

### 2. Emplacement privé local

Les archives sont stockées sous `files/leaf_backups`, dans le stockage privé de l'application. Le stockage interne spécifique à l'application est isolé des autres applications et chiffré sur Android 10/API 29 et versions ultérieures. Il est toutefois supprimé à la désinstallation : il ne constitue donc pas une sauvegarde indépendante à long terme.

Références :
- https://developer.android.com/training/data-storage/app-specific
- https://developer.android.com/training/data-storage/use-cases

### 3. Carte SD et clé USB : Storage Access Framework

La future exportation vers la carte SD/USB doit utiliser le sélecteur système (Storage Access Framework), demander un dossier choisi par l'utilisateur et conserver l'autorisation URI persistante. Il ne faut pas demander un accès global au stockage ni supposer que tous les fournisseurs permettent les mêmes opérations. Un support amovible peut être retiré ; chaque écriture doit vérifier sa disponibilité.

Références :
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/training/data-storage/use-cases

### 4. Intégrité et chiffrement

Le manifeste SHA-256 actuel sert à détecter une corruption ou une modification accidentelle ; ce n'est pas un chiffrement et ne protège pas contre un attaquant qui remplace à la fois le fichier et son manifeste. La prochaine version devra vérifier chaque empreinte avant restauration. Pour les archives exportées, envisager un chiffrement authentifié (par exemple AES-GCM) et gérer les clés avec Android Keystore. Une clé de chiffrement ne doit jamais être codée en dur ni être stockée à côté de l'archive en clair.

Références :
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/cryptography

### 5. Sauvegarde Android native : complément, pas substitut

Android Auto Backup est un mécanisme distinct, soumis aux paramètres système et à des conditions de plateforme. Il ne remplace pas les archives locales hors ligne de LeafVibe. Les règles d'inclusion/exclusion doivent être configurées séparément pour Android 11 et versions antérieures et pour Android 12+ lorsque l'application cible API 31+.

Référence :
- https://developer.android.com/identity/data/autobackup

## Contrôles à réaliser avant diffusion

- Construire les trois APK depuis les commits de cette branche.
- Installer chaque APK sur Android 13+ et contrôler le démarrage.
- Vérifier qu'un ZIP est créé après fermeture de session puis après la périodicité.
- Ouvrir chaque ZIP et vérifier le manifeste et les SHA-256.
- Simuler espace disque insuffisant, arrêt de processus, fichier volumineux, nom Unicode et interruption.
- Confirmer qu'aucune donnée n'est supprimée lors d'un échec.
- Vérifier que les archives sont exclues de la sauvegarde suivante et que la rétention garde exactement les sept plus récentes.
- Tester la persistance des autorisations SAF après redémarrage et le retrait/réinsertion de la carte SD.
- Ajouter un parcours de restauration transactionnelle avant de qualifier la fonctionnalité de restauration.

## Limite de la version actuelle

Le code présent réalise des snapshots locaux planifiés et des empreintes à la création. Il n'implémente pas encore l'écran de restauration, la validation des empreintes à la restauration, le chiffrement des exports, la copie automatique vers SD/USB ni la synchronisation multi-appareils. Ces points restent explicitement planifiés et ne sont pas présentés comme disponibles.
