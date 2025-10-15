import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Scanner;

/**
 * ClientFTP - Client FTP complet en ligne de commande
 *
 * Fonctionnalités :
 * - Exercice 1 : Authentification (USER/PASS)
 * - Exercice 2 : Mode actif (PORT) et mode passif (PASV)
 * - Exercice 3 : Manipulation de fichiers (LIST, CWD, RETR)
 * - Exercice 4 : Support complet pour tous les utilisateurs
 *
 * @author TP2 - ARSIR Polytech 4A
 * @version Finale - Exercices 1-4
 */
public class ClientFTP {

    // ==================== CONSTANTES ====================

    /** Hôte du serveur FTP par défaut */
    private static final String HOTE_SERVEUR = "localhost";

    /** Port de contrôle du serveur FTP */
    private static final int PORT_SERVEUR = 2121;

    // ==================== VARIABLES D'INSTANCE ====================

    /** Socket de contrôle pour communiquer avec le serveur */
    private Socket socketControle;

    /** Lecteur pour recevoir les réponses du serveur */
    private BufferedReader lecteur;

    /** Écrivain pour envoyer les commandes au serveur */
    private PrintWriter ecrivain;

    /** Socket serveur pour recevoir la connexion en mode actif */
    private ServerSocket socketServeurActif;

    /** Socket de données pour les transferts */
    private Socket socketDonnees;

    /** Port local utilisé pour le mode actif */
    private int portLocalActif;

    /** Adresse et port du serveur pour le mode passif */
    private String adresseServeurPassif;
    private int portServeurPassif;

    /** Mode de transfert configuré */
    private String modeTransfert;

    /** Indique si le client est connecté */
    private boolean estConnecte;

    /** Indique si l'utilisateur est authentifié */
    private boolean estAuthentifie;

    // ==================== CONSTRUCTEUR ====================

    /**
     * Crée une instance du client FTP
     */
    public ClientFTP() {
        this.estConnecte = false;
        this.estAuthentifie = false;
        this.modeTransfert = null;
    }

    // ==================== MÉTHODES DE CONNEXION ====================

    /**
     * Établit la connexion de contrôle avec le serveur FTP
     *
     * @return true si la connexion réussit, false sinon
     */
    public boolean connecter() {
        try {
            socketControle = new Socket(HOTE_SERVEUR, PORT_SERVEUR);
            lecteur = new BufferedReader(new InputStreamReader(socketControle.getInputStream()));
            ecrivain = new PrintWriter(socketControle.getOutputStream(), true);
            estConnecte = true;

            // Lire le message de bienvenue
            String reponse = lireReponse();
            System.out.println(reponse);

            return true;

        } catch (IOException e) {
            System.err.println("[ERREUR] Impossible de se connecter au serveur : " + e.getMessage());
            return false;
        }
    }

    /**
     * Ferme la connexion avec le serveur
     */
    public void deconnecter() {
        try {
            if (lecteur != null) {
                lecteur.close();
            }
            if (ecrivain != null) {
                ecrivain.close();
            }
            if (socketControle != null && !socketControle.isClosed()) {
                socketControle.close();
            }
            if (socketServeurActif != null && !socketServeurActif.isClosed()) {
                socketServeurActif.close();
            }
            estConnecte = false;
            estAuthentifie = false;
            System.out.println("[CLIENT] Déconnecté du serveur");
        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de la déconnexion : " + e.getMessage());
        }
    }

    // ==================== MÉTHODES DE COMMANDES ====================

    /**
     * Envoie la commande USER pour s'identifier (Exercice 1)
     *
     * @param nomUtilisateur Nom d'utilisateur
     * @return Réponse du serveur
     */
    public String envoyerUser(String nomUtilisateur) {
        String reponse = envoyerCommande("USER " + nomUtilisateur);
        if (reponse != null && (reponse.startsWith("230") || reponse.startsWith("331"))) {
            // Utilisateur reconnu
            if (reponse.startsWith("230")) {
                estAuthentifie = true;
            }
        }
        return reponse;
    }

    /**
     * Envoie la commande PASS pour s'authentifier (Exercice 1)
     *
     * @param motDePasse Mot de passe
     * @return Réponse du serveur
     */
    public String envoyerPass(String motDePasse) {
        String reponse = envoyerCommande("PASS " + motDePasse);
        if (reponse != null && reponse.startsWith("230")) {
            estAuthentifie = true;
        }
        return reponse;
    }

    /**
     * Envoie la commande PORT pour configurer le mode actif (Exercice 2)
     * Le client ouvre un port local et communique ses coordonnées au serveur
     *
     * @return Réponse du serveur
     */
    public String envoyerPort() {
        try {
            // Fermer l'ancien socket s'il existe
            if (socketServeurActif != null && !socketServeurActif.isClosed()) {
                socketServeurActif.close();
            }

            // Créer un socket serveur sur un port disponible
            socketServeurActif = new ServerSocket(0); // Port automatique
            portLocalActif = socketServeurActif.getLocalPort();

            // Obtenir l'adresse IP locale
            String adresseIP = socketControle.getLocalAddress().getHostAddress();

            // Convertir au format FTP : a,b,c,d,e,f
            String[] octets = adresseIP.split("\\.");
            int p1 = portLocalActif / 256;
            int p2 = portLocalActif % 256;

            String argument = String.format("%s,%s,%s,%s,%d,%d",
                octets[0], octets[1], octets[2], octets[3], p1, p2);

            System.out.println("[CLIENT] Mode actif configuré sur le port local : " + portLocalActif);

            String reponse = envoyerCommande("PORT " + argument);
            if (reponse != null && reponse.startsWith("200")) {
                modeTransfert = "actif";
            }
            return reponse;

        } catch (IOException e) {
            System.err.println("[ERREUR] Impossible de créer le socket pour le mode actif : " + e.getMessage());
            return null;
        }
    }

    /**
     * Envoie la commande PASV pour configurer le mode passif (Exercice 2)
     * Le serveur communique le port sur lequel le client doit se connecter
     *
     * @return Réponse du serveur
     */
    public String envoyerPasv() {
        String reponse = envoyerCommande("PASV");

        if (reponse != null && reponse.startsWith("227")) {
            // Parser la réponse pour extraire l'adresse et le port
            // Format : 227 Mode passif activé (a,b,c,d,e,f)
            try {
                int debut = reponse.indexOf('(');
                int fin = reponse.indexOf(')');

                if (debut != -1 && fin != -1) {
                    String coordonnees = reponse.substring(debut + 1, fin);
                    String[] parties = coordonnees.split(",");

                    if (parties.length == 6) {
                        adresseServeurPassif = String.format("%s.%s.%s.%s",
                            parties[0], parties[1], parties[2], parties[3]);
                        portServeurPassif = Integer.parseInt(parties[4]) * 256 + Integer.parseInt(parties[5]);
                        modeTransfert = "passif";

                        System.out.println(String.format("[CLIENT] Mode passif configuré : %s:%d",
                            adresseServeurPassif, portServeurPassif));
                    }
                }
            } catch (Exception e) {
                System.err.println("[ERREUR] Impossible de parser la réponse PASV : " + e.getMessage());
            }
        }

        return reponse;
    }

    /**
     * Envoie la commande LIST pour lister les fichiers (Exercice 3)
     *
     * @return Réponse du serveur
     */
    public String envoyerList() {
        String reponse = envoyerCommande("LIST");

        if (reponse != null && reponse.startsWith("150")) {
            // Établir la connexion de données
            if (etablirConnexionDonnees()) {
                try {
                    // Lire les données
                    BufferedReader lecteurDonnees = new BufferedReader(
                        new InputStreamReader(socketDonnees.getInputStream()));

                    System.out.println("\n--- LISTE DES FICHIERS ---");

                    String ligne;
                    while ((ligne = lecteurDonnees.readLine()) != null) {
                        System.out.println(ligne);
                    }

                    System.out.println("--- FIN DE LA LISTE ---\n");

                    socketDonnees.close();

                    // Lire la réponse finale (226)
                    reponse = lireReponse();
                    System.out.println(reponse);

                } catch (IOException e) {
                    System.err.println("[ERREUR] Erreur lors de la lecture de la liste : " + e.getMessage());
                }
            }
        }

        return reponse;
    }

    /**
     * Envoie la commande CWD pour changer de répertoire (Exercice 3)
     *
     * @param dossier Nom du dossier
     * @return Réponse du serveur
     */
    public String envoyerCwd(String dossier) {
        return envoyerCommande("CWD " + dossier);
    }

    /**
     * Envoie la commande RETR pour télécharger un fichier (Exercice 3)
     *
     * @param nomFichier Nom du fichier à télécharger
     * @return Réponse du serveur
     */
    public String envoyerRetr(String nomFichier) {
        String reponse = envoyerCommande("RETR " + nomFichier);

        if (reponse != null && reponse.startsWith("213")) {
            // Extraire la taille du fichier
            String[] parties = reponse.split(" ");
            long tailleFichier = 0;

            if (parties.length >= 2) {
                try {
                    tailleFichier = Long.parseLong(parties[1]);
                } catch (NumberFormatException e) {
                    System.err.println("[ERREUR] Impossible de lire la taille du fichier");
                }
            }

            // Lire la réponse 150
            try {
                reponse = lireReponse();
                System.out.println(reponse);

                if (reponse.startsWith("150")) {
                    // Établir la connexion de données
                    if (etablirConnexionDonnees()) {
                        // Télécharger le fichier
                        telechargerFichier(nomFichier, tailleFichier);

                        socketDonnees.close();

                        // Lire la réponse finale (226)
                        reponse = lireReponse();
                        System.out.println(reponse);
                    }
                }
            } catch (IOException e) {
                System.err.println("[ERREUR] Erreur lors du téléchargement : " + e.getMessage());
            }
        }

        return reponse;
    }

    /**
     * Télécharge un fichier depuis le serveur
     *
     * @param nomFichier Nom du fichier
     * @param tailleFichier Taille du fichier en octets
     */
    private void telechargerFichier(String nomFichier, long tailleFichier) {
        try {
            Path cheminSortie = Paths.get(nomFichier);

            try (InputStream entree = socketDonnees.getInputStream();
                 OutputStream sortie = Files.newOutputStream(cheminSortie)) {

                byte[] tampon = new byte[4096];
                long octetsLus = 0;
                int lecture;

                System.out.println("[CLIENT] Téléchargement de " + nomFichier + " (" + tailleFichier + " octets)...");

                while (octetsLus < tailleFichier && (lecture = entree.read(tampon)) != -1) {
                    sortie.write(tampon, 0, lecture);
                    octetsLus += lecture;

                    // Afficher la progression
                    int pourcentage = (int) ((octetsLus * 100) / tailleFichier);
                    System.out.print("\r[CLIENT] Progression : " + pourcentage + "% (" + octetsLus + "/" + tailleFichier + " octets)");
                }

                System.out.println();
                System.out.println("[CLIENT] Fichier téléchargé avec succès : " + cheminSortie.toAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors du téléchargement du fichier : " + e.getMessage());
        }
    }

    /**
     * Envoie la commande QUIT pour se déconnecter
     *
     * @return Réponse du serveur
     */
    public String envoyerQuit() {
        String reponse = envoyerCommande("QUIT");
        deconnecter();
        return reponse;
    }

    /**
     * Envoie une commande quelconque au serveur
     *
     * @param commande Commande à envoyer
     * @return Réponse du serveur
     */
    public String envoyerCommande(String commande) {
        if (!estConnecte) {
            System.err.println("[ERREUR] Non connecté au serveur");
            return null;
        }

        try {
            System.out.println("[CLIENT] Envoi : " + commande);
            ecrivain.println(commande);

            String reponse = lireReponse();
            System.out.println("[CLIENT] Réponse : " + reponse);

            return reponse;

        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de l'envoi de la commande : " + e.getMessage());
            return null;
        }
    }

    /**
     * Lit une réponse du serveur
     *
     * @return Réponse lue
     * @throws IOException Si erreur de lecture
     */
    private String lireReponse() throws IOException {
        return lecteur.readLine();
    }

    /**
     * Établit la connexion de données selon le mode configuré
     *
     * @return true si succès, false sinon
     */
    private boolean etablirConnexionDonnees() {
        try {
            if ("actif".equals(modeTransfert)) {
                // Mode actif : attendre la connexion du serveur
                System.out.println("[CLIENT] Attente de la connexion du serveur en mode actif...");
                socketDonnees = socketServeurActif.accept();
                System.out.println("[CLIENT] Connexion de données établie en mode actif");
                return true;

            } else if ("passif".equals(modeTransfert)) {
                // Mode passif : se connecter au serveur
                System.out.println("[CLIENT] Connexion au serveur en mode passif...");
                socketDonnees = new Socket(adresseServeurPassif, portServeurPassif);
                System.out.println("[CLIENT] Connexion de données établie en mode passif");
                return true;
            } else {
                System.err.println("[ERREUR] Aucun mode de transfert configuré (utilisez PORT ou PASV)");
                return false;
            }

        } catch (IOException e) {
            System.err.println("[ERREUR] Impossible d'établir la connexion de données : " + e.getMessage());
            return false;
        }
    }

    // ==================== MÉTHODE MAIN ====================

    /**
     * Point d'entrée du client FTP
     * Interface en ligne de commande interactive
     *
     * @param args Arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        System.out.println("===================================");
        System.out.println("  CLIENT FTP - TP2 Exercices 1-4");
        System.out.println("===================================");
        System.out.println("Connexion au serveur : " + HOTE_SERVEUR + ":" + PORT_SERVEUR);
        System.out.println("===================================\n");

        ClientFTP client = new ClientFTP();

        // Connexion au serveur
        if (!client.connecter()) {
            System.err.println("[ERREUR] Impossible de démarrer le client");
            return;
        }

        // Interface interactive
        Scanner scanner = new Scanner(System.in);
        afficherAide();

        boolean continuer = true;

        while (continuer) {
            System.out.print("\nftp> ");
            String ligne = scanner.nextLine().trim();

            if (ligne.isEmpty()) {
                continue;
            }

            String[] parties = ligne.split(" ", 2);
            String commande = parties[0].toLowerCase();
            String argument = parties.length > 1 ? parties[1] : "";

            switch (commande) {
                case "user":
                    if (argument.isEmpty()) {
                        System.out.println("Usage : user <nom_utilisateur>");
                    } else {
                        client.envoyerUser(argument);
                    }
                    break;

                case "pass":
                    if (argument.isEmpty()) {
                        System.out.println("Usage : pass <mot_de_passe>");
                    } else {
                        client.envoyerPass(argument);
                    }
                    break;

                case "port":
                    client.envoyerPort();
                    break;

                case "pasv":
                    client.envoyerPasv();
                    break;

                case "list":
                case "ls":
                    if (!client.estAuthentifie) {
                        System.out.println("[ERREUR] Vous devez vous authentifier d'abord");
                    } else if (client.modeTransfert == null) {
                        System.out.println("[ERREUR] Vous devez configurer le mode de transfert (PORT ou PASV)");
                    } else {
                        client.envoyerList();
                    }
                    break;

                case "cwd":
                case "cd":
                    if (argument.isEmpty()) {
                        System.out.println("Usage : cwd <dossier>");
                    } else if (!client.estAuthentifie) {
                        System.out.println("[ERREUR] Vous devez vous authentifier d'abord");
                    } else {
                        client.envoyerCwd(argument);
                    }
                    break;

                case "retr":
                case "get":
                    if (argument.isEmpty()) {
                        System.out.println("Usage : retr <fichier>");
                    } else if (!client.estAuthentifie) {
                        System.out.println("[ERREUR] Vous devez vous authentifier d'abord");
                    } else if (client.modeTransfert == null) {
                        System.out.println("[ERREUR] Vous devez configurer le mode de transfert (PORT ou PASV)");
                    } else {
                        client.envoyerRetr(argument);
                    }
                    break;

                case "quit":
                case "exit":
                    client.envoyerQuit();
                    continuer = false;
                    break;

                case "help":
                case "aide":
                case "?":
                    afficherAide();
                    break;

                default:
                    System.out.println("Commande inconnue. Tapez 'help' pour voir la liste des commandes.");
                    break;
            }
        }

        scanner.close();
        System.out.println("\n[CLIENT] Au revoir !");
    }

    /**
     * Affiche l'aide des commandes disponibles
     */
    private static void afficherAide() {
        System.out.println("\n=== COMMANDES FTP DISPONIBLES ===");
        System.out.println("\nAUTHENTIFICATION (Exercice 1) :");
        System.out.println("  user <nom>     : Envoyer le nom d'utilisateur");
        System.out.println("  pass <mdp>     : Envoyer le mot de passe");
        System.out.println("\nMODE DE TRANSFERT (Exercice 2) :");
        System.out.println("  port           : Activer le mode actif");
        System.out.println("  pasv           : Activer le mode passif");
        System.out.println("\nMANIPULATION DE FICHIERS (Exercice 3) :");
        System.out.println("  list / ls      : Lister les fichiers du répertoire");
        System.out.println("  cwd / cd <dir> : Changer de répertoire");
        System.out.println("  retr / get <f> : Télécharger un fichier");
        System.out.println("\nAUTRES :");
        System.out.println("  quit / exit    : Se déconnecter et quitter");
        System.out.println("  help / aide    : Afficher cette aide");
        System.out.println("\nUTILISATEURS DE TEST :");
        System.out.println("  - anonymous (mot de passe quelconque)");
        System.out.println("  - foo (mot de passe : bar)");
        System.out.println("==================================\n");
    }
}
