Rien à cacher ?

Un host héberge la game en local avec un pin généré aléatoirement en WIFI P2P(Wifi Direct).
Chaque utilisateur lui envoie ces données.
Broadcast pour demander les photos.
Choisir son nom + photo de profil (caméra)-> donnée persistante.
pop up quand on est connecté au salon.

Choisir random 10 photos si on veut pas on secoue pour en avoir 10 nouvelles.

Chaque manche dure 15 sec -> plus on répond vite plus on a de points. Il a des strick avec des multiplicateurs.

Intent qui renvoit vers le repo du github du projet, et le readme

Quand on lance la première fois l'application on arrive sur la création de profil :
	- Photo de profil -> appareil photo ou galerie
	- Pseudo
	
Accueil :
	- Création de la partie
	- Rejoindre une partie
	- Profil
	- Paramètres
	- Bouton pour lancer la game
	- Icone pour renvoyer sur le readme "?"

Profil :
	- Photo avec un bouton pour changer
	- Edit text nom déja rempli avec l'autres
	- Bouton valider
	- Bouton pour revenir en arrière croix rouge

Paramètres :
	- Changement de langues
	- Bouton pour le readme
	- Bouton pour le repo
	- Bouton revenir en arriere croix rouge
	
Quand on clique sur le bouton création d'une partie: vue rounds :
	- On choisi le nombre de rounds
	
Quand on a choisi les rounds: vue photos:
	- Choisi 10 photos aléatoires
	- Bouton accepté
	- Bouton relancé

Quand on clique sur rejoindre une partie :
	- Ca nous demande nos photos -> vues des photos
	- Rentrer le code pin -> renvoie vers la vues accueil game et si ca marche pas pop up code pin invalide // partie non trouvé 
	
Quand on clique sur accepté : accueil game:
	- Affichage du pin de la game
	- Affichage du nom des joueurs + leur photo de profile
	- Bouton pour rechoisir des images
	- Bouton pour rechoisir le nombre de rounds -> uniquement l'host, grisé pour les autres
	- Bouton pour lancer la partie
	
Vue de partie * fois par rapport au nombre de rounds :
	Page de jeu :
		- En haut le nombre de rounds/nombre total
		- une barre avec le temps qui défile
		- La photo 
		- En bas de la photo des cases à cliquer avec nom + photo => Que 4 max (1 à 3 joueurs tous afficher, à 4 et + ca prends aléatoirement 4 joueurs à chaque tours
		
   Quand le temps est fini :
	  floutage de la photo, on peut plus répondre et ça affiche la bonne réponse en vert et si on a mis la mauvaise elle passe en rouge (ça passe automotiquement)
		
   Page de semi-classement :
		- Le Classement de tout le monde avec à leur streak si il a, le plus rapide avec l'éclair et le plus lent avec l'escargot (ça passe automotiquement)
	
Page classement :
	- podium pour les 3 premiers
	- les autres en dessous
	- bouton pour passer à la suite // localement
	
Page stats fin :
	- Le plus rapide à avoir répondu à une question + son temps
	- La plus longue série + son nombre
	- bouton rejouer (l'host clique dessus ca affiche une pop up aux autres pour le rejoindre, passer le pin automatiquement), bouton grisé pour les autres // on revient au sélecteur de photos, vue photos
	- bouton pour revoir le classement
	
