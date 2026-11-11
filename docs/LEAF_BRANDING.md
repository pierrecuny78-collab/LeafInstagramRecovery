# LeafBranding — identité visuelle commune LeafVibe

Version: 1.0  
Statut: référence commune à appliquer progressivement aux interfaces du dépôt.

## Palette LeafBranding Color

| Token | Hex | Usage |
|---|---|---|
| oasis | #206B4D | Couleur principale, actions principales, liens actifs |
| walnut | #6B4935 | Couleur secondaire, titres et surfaces secondaires |
| natural-gold | #C5A46D | Accent, indicateurs sélectionnés et détails ponctuels |
| cream | #DCCBB1 | Fonds clairs et surfaces douces |
| sand-border | #B69A7B | Bordures, séparateurs et contours |

Ne pas ajouter par défaut le chocolat noir, le vert sauge ou l'ivoire à cette palette. Les couleurs d'état (erreur, avertissement, succès) restent sémantiques et doivent être accessibles; elles ne remplacent pas les couleurs de marque.

## Règles de marque

- Chaque application affiche l'icône LeafVibe et le nom « LeafVibe » dans son en-tête lorsque la plateforme et l'espace le permettent.
- Le nom propre de l'application reste visible à proximité (ex. LeafNotes).
- Conserver les mêmes tokens et leur signification entre les applications.
- Prévoir les modes clair et sombre à partir de ces tokens, avec des surfaces et textes adaptés au contraste. Ne pas inverser simplement les couleurs.
- Ne pas utiliser l'or comme couleur de grande surface ni multiplier les bordures épaisses.
- Les bordures standard utilisent sand-border; les éléments actifs utilisent oasis ou natural-gold avec parcimonie.

## Responsive / adaptatif

L'interface doit se réorganiser selon l'espace disponible, pas simplement réduire tous les éléments.

- Compact (< 600 dp): une colonne, navigation compacte, textes horizontaux, commandes tactiles accessibles.
- Moyen (600–839 dp): deux colonnes si le contenu le permet, panneaux regroupés.
- Large (840–1199 dp): deux ou trois zones selon le contenu, navigation persistante si utile.
- Très large (>= 1200 dp): jusqu'à cinq cartes/colonnes lorsque la lisibilité le permet; garder des largeurs maximales pour les textes longs.

Exigences:
- Aucun texte vertical involontaire, rogné ou superposé.
- Aucun débordement horizontal non intentionnel.
- Les listes doivent défiler dans leur propre zone sans bloquer les commandes.
- Les formulaires et dialogues doivent rester utilisables en petit écran et en mode paysage.
- Respecter les zones système, les tailles de police et les préférences d'accessibilité.
- Tester au minimum un téléphone étroit, un téléphone standard et une fenêtre/tablette large.

## Intégration

Utiliser les tokens ci-dessus comme source de vérité. Éviter de recopier des codes hexadécimaux dans chaque composant: centraliser les couleurs dans un thème ou un module de design system propre à la plateforme. Les applications peuvent adapter la densité et la disposition, mais pas redéfinir arbitrairement l'identité de marque.

## Statut

La présence de ce document signifie que le dépôt adopte la référence LeafBranding. Elle ne signifie pas que chaque écran a déjà été migré ni testé sur chaque appareil.
