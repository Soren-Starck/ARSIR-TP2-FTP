PESIC Louka
STARCK soren
ROJAS Juliette

Exercice 1
Dans cet exercice, afin de permettre un traitement des commandes du client dans le serveur, j'ai procédé comme le TP 
précédent (un échange de texte par sockets) auquel j'ai ajouté un traitement supplémentaire pour le texte du message
Lorsque le serveur reçoit le message, il est soit dans l'état connecté ou non connecté avec le client. 

Dans l'état déconnecté, les seules commandes disponibles sont USER, PASS et SHUTDOWN. 

La commande USER permet de donner le nom d'utilisateur, alors stocké dans une variable. Si le mot de passe entré avec 
PASS est celui correspondant au nom d'utilisateur en mémoire, alors le serveur passe en état connecté.

Dans l'état connecté, aux commandes précédentes vient s'ajouter la commande QUIT pour se déconnecter sans éteindre le
serveur.

Le programme est apte à capter les exceptions et textes non valides.

Le serveur renvoie des codes pour chaque action réalisée.