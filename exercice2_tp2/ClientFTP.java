import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ClientFTP - Client FTP en ligne de commande
 *
 * Ce client permet de :
 * - Se connecter à un serveur FTP
 * - S'authentifier (USER/PASS)
 * - Configurer le mode de transfert (PORT pour actif, PASV pour passif)
 * - Se déconnecter (QUIT)
 *
 * @author TP2 - ARSIR Polytech 4A
 * @version 1.0
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

    /** Port local utilisé pour le mode actif */
    private int portLocalActif;

    /** Indique si le client est connecté */
    private boolean estConnecte;

    // ==================== CONSTRUCTEUR ====================

    /**
     * Crée une instance du client FTP
     */
    public ClientFTP() {
        this.estConnecte = false;
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
            estConnecte = false;
            System.out.println("[CLIENT] Déconnecté du serveur");
        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de la déconnexion : " + e.getMessage());
        }
    }

    // ==================== MÉTHODES DE COMMANDES ====================

    /**
     * Envoie la commande USER pour s'identifier
     *
     * @param nomUtilisateur Nom d'utilisateur
     * @return Réponse du serveur
     */
    public String envoyerUser(String nomUtilisateur) {
        return envoyerCommande("USER " + nomUtilisateur);
    }

    /**
     * Envoie la commande PASS pour s'authentifier
     *
     * @param motDePasse Mot de passe
     * @return Réponse du serveur
     */
    public String envoyerPass(String motDePasse) {
        return envoyerCommande("PASS " + motDePasse);
    }

    /**
     * Envoie la commande PORT pour configurer le mode actif
     * Le client ouvre un port local et communique ses coordonnées au serveur
     *
     * @return Réponse du serveur
     */
    public String envoyerPort() {
        try {
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

            return envoyerCommande("PORT " + argument);

        } catch (IOException e) {
            System.err.println("[ERREUR] Impossible de créer le socket pour le mode actif : " + e.getMessage());
            return null;
        }
    }

    /**
     * Envoie la commande PASV pour configurer le mode passif
     * Le serveur communique le port sur lequel le client doit se connecter
     *
     * @return Réponse du serveur (contient les coordonnées du serveur)
     */
    public String envoyerPasv() {
        return envoyerCommande("PASV");
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

    // ==================== MÉTHODE MAIN ====================

    /**
     * Point d'entrée du client FTP
     * Interface en ligne de commande interactive
     *
     * @param args Arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  CLIENT FTP - Exercice 2 TP2");
        System.out.println("========================================");
        System.out.println("Connexion au serveur : " + HOTE_SERVEUR + ":" + PORT_SERVEUR);
        System.out.println("========================================\n");

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
                    // Permettre l'envoi de commandes arbitraires
                    client.envoyerCommande(ligne);
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
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              COMMANDES FTP DISPONIBLES                     ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ user <nom>     : Envoyer le nom d'utilisateur              ║");
        System.out.println("║ pass <mdp>     : Envoyer le mot de passe                   ║");
        System.out.println("║ port           : Activer le mode actif                     ║");
        System.out.println("║ pasv           : Activer le mode passif                    ║");
        System.out.println("║ quit / exit    : Se déconnecter et quitter                 ║");
        System.out.println("║ help / aide    : Afficher cette aide                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ UTILISATEURS DE TEST :                                     ║");
        System.out.println("║   - anonymous (mot de passe quelconque)                    ║");
        System.out.println("║   - foo (mot de passe : bar)                               ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
}
