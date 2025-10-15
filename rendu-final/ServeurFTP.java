import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * ServeurFTP - Implémentation complète d'un serveur FTP
 *
 * Fonctionnalités :
 * - Exercice 1 : Authentification (USER/PASS) avec support anonymous et utilisateur foo
 * - Exercice 2 : Mode actif (PORT) et mode passif (PASV)
 * - Exercice 3 : Manipulation de fichiers (LIST, CWD, RETR)
 * - Exercice 4 : Gestion des droits par utilisateur
 *
 * @author TP2 - ARSIR Polytech 4A
 * @version Finale - Exercices 1-4
 */
public class ServeurFTP {

    // ==================== CONSTANTES ====================

    /** Port d'écoute pour la socket de contrôle (port non-standard) */
    private static final int PORT_CONTROLE = 2121;

    /** Plage de ports pour les connexions de données en mode passif */
    private static final int PORT_PASSIF_MIN = 5000;
    private static final int PORT_PASSIF_MAX = 5100;

    /** Répertoire racine contenant les données */
    private static final String REPERTOIRE_RACINE = "Data";

    /** Base de données des utilisateurs (login -> mot de passe) */
    private static final Map<String, String> BASE_UTILISATEURS = new HashMap<>();

    static {
        BASE_UTILISATEURS.put("foo", "bar");
        BASE_UTILISATEURS.put("anonymous", ""); // Accepte n'importe quel mot de passe
    }

    // ==================== CODES DE RÉPONSE FTP ====================

    private static final String CODE_150 = "150 Ouverture de la connexion en cours";
    private static final String CODE_200 = "200 Action demandée accomplie avec succès";
    private static final String CODE_213 = "213"; // Suivi de la taille du fichier
    private static final String CODE_220 = "220 Serveur FTP prêt";
    private static final String CODE_221 = "221 Fermeture de la connexion";
    private static final String CODE_226 = "226 Transfert terminé avec succès";
    private static final String CODE_227 = "227 Mode passif activé";
    private static final String CODE_230 = "230 Utilisateur connecté";
    private static final String CODE_250 = "250 Répertoire courant changé";
    private static final String CODE_331 = "331 Utilisateur reconnu, en attente du mot de passe";
    private static final String CODE_430 = "430 Identifiant ou mot de passe incorrect";
    private static final String CODE_501 = "501 Erreur de syntaxe";
    private static final String CODE_530 = "530 Non authentifié";
    private static final String CODE_550 = "550 Fichier non trouvé ou accès refusé";

    // ==================== VARIABLES D'INSTANCE ====================

    /** Socket de contrôle pour communiquer avec le client */
    private Socket socketControle;

    /** Lecteur pour recevoir les commandes du client */
    private BufferedReader lecteur;

    /** Écrivain pour envoyer les réponses au client */
    private PrintWriter ecrivain;

    /** Nom d'utilisateur authentifié (null si pas encore authentifié) */
    private String utilisateurCourant;

    /** Nom d'utilisateur en cours de connexion */
    private String utilisateurEnCoursConnexion;

    /** Indique si l'utilisateur est authentifié */
    private boolean estAuthentifie;

    /** Socket serveur pour le mode passif */
    private ServerSocket socketServeurPassif;

    /** Socket de données pour transférer les fichiers */
    private Socket socketDonnees;

    /** Adresse IP du client pour le mode actif */
    private InetAddress adresseClientActif;

    /** Port du client pour le mode actif */
    private int portClientActif;

    /** Mode de transfert : "actif" ou "passif" */
    private String modeTransfert;

    /** Répertoire courant de l'utilisateur */
    private Path repertoireCourant;

    /** Répertoire racine de l'utilisateur (pour les restrictions d'accès) */
    private Path repertoireRacineUtilisateur;

    // ==================== CONSTRUCTEUR ====================

    /**
     * Crée une instance du serveur FTP pour gérer une connexion client
     *
     * @param socketControle Socket de contrôle établie avec le client
     * @throws IOException Si erreur lors de la création des flux d'entrée/sortie
     */
    public ServeurFTP(Socket socketControle) throws IOException {
        this.socketControle = socketControle;
        this.lecteur = new BufferedReader(new InputStreamReader(socketControle.getInputStream()));
        this.ecrivain = new PrintWriter(socketControle.getOutputStream(), true);
        this.estAuthentifie = false;
        this.utilisateurCourant = null;
        this.utilisateurEnCoursConnexion = null;
        this.modeTransfert = null;
    }

    // ==================== MÉTHODES PRINCIPALES ====================

    /**
     * Démarre le traitement des commandes du client
     * Boucle principale qui lit et traite chaque commande reçue
     */
    public void demarrer() {
        try {
            // Message de bienvenue
            envoyerReponse(CODE_220);

            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                ligne = ligne.trim();

                if (ligne.isEmpty()) {
                    continue;
                }

                System.out.println("[SERVEUR] Commande reçue : " + ligne);

                // Traiter la commande
                boolean continuer = traiterCommande(ligne);

                if (!continuer) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de la communication : " + e.getMessage());
        } finally {
            fermerConnexions();
        }
    }

    /**
     * Traite une commande reçue du client
     *
     * @param ligne Ligne de commande complète reçue
     * @return true pour continuer, false pour arrêter le serveur
     */
    private boolean traiterCommande(String ligne) {
        String[] parties = ligne.split(" ", 2);
        String commande = parties[0].toUpperCase();
        String argument = parties.length > 1 ? parties[1] : "";

        switch (commande) {
            case "USER":
                gererCommandeUser(argument);
                break;

            case "PASS":
                gererCommandePass(argument);
                break;

            case "PORT":
                gererCommandePort(argument);
                break;

            case "PASV":
                gererCommandePasv();
                break;

            case "LIST":
                gererCommandeList();
                break;

            case "CWD":
                gererCommandeCwd(argument);
                break;

            case "RETR":
                gererCommandeRetr(argument);
                break;

            case "QUIT":
                gererCommandeQuit();
                return false;

            default:
                envoyerReponse(CODE_501);
                break;
        }

        return true;
    }

    // ==================== GESTION DES COMMANDES ====================

    /**
     * Gère la commande USER pour l'authentification (Exercice 1)
     * Première étape de l'authentification : vérification du nom d'utilisateur
     *
     * @param nomUtilisateur Nom d'utilisateur fourni par le client
     */
    private void gererCommandeUser(String nomUtilisateur) {
        if (nomUtilisateur.isEmpty()) {
            envoyerReponse(CODE_501);
            return;
        }

        // Vérifier si l'utilisateur existe
        if (BASE_UTILISATEURS.containsKey(nomUtilisateur)) {
            this.utilisateurEnCoursConnexion = nomUtilisateur;

            // Pour anonymous, authentification immédiate
            if (nomUtilisateur.equals("anonymous")) {
                this.utilisateurCourant = nomUtilisateur;
                this.estAuthentifie = true;
                initialiserRepertoireUtilisateur();
                envoyerReponse(CODE_230);
                System.out.println("[SERVEUR] Utilisateur anonyme connecté");
            } else {
                envoyerReponse(CODE_331);
                System.out.println("[SERVEUR] Utilisateur reconnu : " + nomUtilisateur);
            }
        } else {
            this.utilisateurEnCoursConnexion = null;
            envoyerReponse(CODE_430);
            System.out.println("[SERVEUR] Utilisateur inconnu : " + nomUtilisateur);
        }
    }

    /**
     * Gère la commande PASS pour l'authentification (Exercice 1)
     * Deuxième étape de l'authentification : vérification du mot de passe
     *
     * @param motDePasse Mot de passe fourni par le client
     */
    private void gererCommandePass(String motDePasse) {
        if (utilisateurEnCoursConnexion == null) {
            envoyerReponse(CODE_530);
            return;
        }

        String motDePasseAttendu = BASE_UTILISATEURS.get(utilisateurEnCoursConnexion);

        // Vérifier le mot de passe
        if (motDePasse.equals(motDePasseAttendu)) {
            estAuthentifie = true;
            utilisateurCourant = utilisateurEnCoursConnexion;
            initialiserRepertoireUtilisateur();
            envoyerReponse(CODE_230);
            System.out.println("[SERVEUR] Authentification réussie pour : " + utilisateurCourant);
        } else {
            estAuthentifie = false;
            utilisateurEnCoursConnexion = null;
            envoyerReponse(CODE_430);
            System.out.println("[SERVEUR] Échec d'authentification : mot de passe incorrect");
        }
    }

    /**
     * Initialise le répertoire de l'utilisateur selon ses droits (Exercice 4)
     */
    private void initialiserRepertoireUtilisateur() {
        try {
            Path racine = Paths.get(REPERTOIRE_RACINE, utilisateurCourant).toAbsolutePath();

            // Créer le répertoire s'il n'existe pas
            if (!Files.exists(racine)) {
                Files.createDirectories(racine);
            }

            this.repertoireRacineUtilisateur = racine;
            this.repertoireCourant = racine;

            System.out.println("[SERVEUR] Répertoire initialisé : " + repertoireCourant);
        } catch (IOException e) {
            System.err.println("[ERREUR] Impossible de créer le répertoire utilisateur : " + e.getMessage());
        }
    }

    /**
     * Gère la commande PORT pour le mode actif (Exercice 2)
     * Le client fournit son adresse IP et son port pour la connexion de données
     * Format : PORT a,b,c,d,e,f où IP=a.b.c.d et port=256*e+f
     *
     * @param argument Chaîne au format a,b,c,d,e,f
     */
    private void gererCommandePort(String argument) {
        if (!estAuthentifie) {
            envoyerReponse(CODE_530);
            return;
        }

        try {
            // Parser l'argument : a,b,c,d,e,f
            String[] parties = argument.split(",");

            if (parties.length != 6) {
                envoyerReponse(CODE_501);
                return;
            }

            // Construire l'adresse IP : a.b.c.d
            String adresseIP = String.format("%s.%s.%s.%s",
                parties[0], parties[1], parties[2], parties[3]);

            // Calculer le port : 256 * e + f
            int port = Integer.parseInt(parties[4]) * 256 + Integer.parseInt(parties[5]);

            // Sauvegarder les informations pour la connexion future
            this.adresseClientActif = InetAddress.getByName(adresseIP);
            this.portClientActif = port;
            this.modeTransfert = "actif";

            envoyerReponse(CODE_200);
            System.out.println(String.format("[SERVEUR] Mode actif configuré : %s:%d", adresseIP, port));

        } catch (Exception e) {
            envoyerReponse(CODE_501);
            System.err.println("[ERREUR] Erreur lors du parsing de la commande PORT : " + e.getMessage());
        }
    }

    /**
     * Gère la commande PASV pour le mode passif (Exercice 2)
     * Le serveur ouvre un port et communique ses coordonnées au client
     * Format de réponse : 227 Mode passif activé (a,b,c,d,e,f)
     */
    private void gererCommandePasv() {
        if (!estAuthentifie) {
            envoyerReponse(CODE_530);
            return;
        }

        try {
            // Fermer l'ancien socket serveur passif s'il existe
            if (socketServeurPassif != null && !socketServeurPassif.isClosed()) {
                socketServeurPassif.close();
            }

            // Créer un nouveau socket serveur sur un port disponible
            socketServeurPassif = creerSocketServeurPassif();

            if (socketServeurPassif == null) {
                envoyerReponse("425 Impossible d'ouvrir la connexion de données");
                return;
            }

            int port = socketServeurPassif.getLocalPort();
            this.modeTransfert = "passif";

            // Obtenir l'adresse IP du serveur
            String adresseIP = socketControle.getLocalAddress().getHostAddress();

            // Convertir au format FTP : a,b,c,d,e,f
            String[] octets = adresseIP.split("\\.");
            int p1 = port / 256;
            int p2 = port % 256;

            String reponse = String.format("227 Mode passif activé (%s,%s,%s,%s,%d,%d)",
                octets[0], octets[1], octets[2], octets[3], p1, p2);

            envoyerReponse(reponse);
            System.out.println(String.format("[SERVEUR] Mode passif activé sur le port %d", port));

        } catch (IOException e) {
            envoyerReponse("425 Impossible d'ouvrir la connexion de données");
            System.err.println("[ERREUR] Erreur lors de la création du socket passif : " + e.getMessage());
        }
    }

    /**
     * Gère la commande LIST (Exercice 3)
     * Renvoie la liste des fichiers du dossier courant
     */
    private void gererCommandeList() {
        if (!estAuthentifie) {
            envoyerReponse(CODE_530);
            return;
        }

        try {
            // Vérifier que le répertoire existe
            if (!Files.isDirectory(repertoireCourant)) {
                envoyerReponse(CODE_550);
                return;
            }

            // Établir la connexion de données
            envoyerReponse(CODE_150);

            if (!etablirConnexionDonnees()) {
                envoyerReponse("425 Impossible d'ouvrir la connexion de données");
                return;
            }

            // Envoyer la liste des fichiers via la connexion de données
            PrintWriter ecrivainDonnees = new PrintWriter(socketDonnees.getOutputStream(), true);

            try (var stream = Files.list(repertoireCourant)) {
                stream.forEach(path -> {
                    String info = String.format("%s %10d %s",
                        Files.isDirectory(path) ? "DIR " : "FILE",
                        obtenirTailleFichier(path),
                        path.getFileName());
                    ecrivainDonnees.println(info);
                });
            }

            ecrivainDonnees.close();
            socketDonnees.close();

            envoyerReponse(CODE_226);
            System.out.println("[SERVEUR] Liste envoyée avec succès");

        } catch (IOException e) {
            envoyerReponse(CODE_550);
            System.err.println("[ERREUR] Erreur lors de l'envoi de la liste : " + e.getMessage());
        }
    }

    /**
     * Gère la commande CWD (Exercice 3)
     * Change le répertoire courant
     *
     * @param dossier Nom du dossier vers lequel naviguer
     */
    private void gererCommandeCwd(String dossier) {
        if (!estAuthentifie) {
            envoyerReponse(CODE_530);
            return;
        }

        if (dossier.isEmpty()) {
            envoyerReponse(CODE_501);
            return;
        }

        try {
            Path nouveauChemin;

            if (dossier.equals("..")) {
                // Remonter d'un niveau (mais pas au-dessus de la racine utilisateur)
                nouveauChemin = repertoireCourant.getParent();
                if (nouveauChemin == null || !nouveauChemin.startsWith(repertoireRacineUtilisateur)) {
                    nouveauChemin = repertoireRacineUtilisateur;
                }
            } else {
                // Naviguer vers un sous-dossier
                nouveauChemin = repertoireCourant.resolve(dossier).normalize().toAbsolutePath();
            }

            // Vérifier que le chemin est dans la racine utilisateur (Exercice 4)
            if (!nouveauChemin.startsWith(repertoireRacineUtilisateur)) {
                envoyerReponse(CODE_550);
                System.out.println("[SERVEUR] Accès refusé hors de la racine utilisateur");
                return;
            }

            // Vérifier que le répertoire existe
            if (Files.isDirectory(nouveauChemin)) {
                repertoireCourant = nouveauChemin;
                envoyerReponse(CODE_250);
                System.out.println("[SERVEUR] Répertoire changé : " + repertoireCourant);
            } else {
                envoyerReponse(CODE_550);
                System.out.println("[SERVEUR] Répertoire non trouvé : " + dossier);
            }

        } catch (Exception e) {
            envoyerReponse(CODE_550);
            System.err.println("[ERREUR] Erreur lors du changement de répertoire : " + e.getMessage());
        }
    }

    /**
     * Gère la commande RETR (Exercice 3)
     * Télécharge un fichier vers le client
     *
     * @param nomFichier Nom du fichier à télécharger
     */
    private void gererCommandeRetr(String nomFichier) {
        if (!estAuthentifie) {
            envoyerReponse(CODE_530);
            return;
        }

        if (nomFichier.isEmpty()) {
            envoyerReponse(CODE_501);
            return;
        }

        try {
            Path cheminFichier = repertoireCourant.resolve(nomFichier).normalize().toAbsolutePath();

            // Vérifier que le fichier est dans la racine utilisateur (Exercice 4)
            if (!cheminFichier.startsWith(repertoireRacineUtilisateur)) {
                envoyerReponse(CODE_550);
                System.out.println("[SERVEUR] Accès refusé hors de la racine utilisateur");
                return;
            }

            // Vérifier que le fichier existe et n'est pas un répertoire
            if (!Files.exists(cheminFichier) || Files.isDirectory(cheminFichier)) {
                envoyerReponse(CODE_550);
                System.out.println("[SERVEUR] Fichier non trouvé : " + nomFichier);
                return;
            }

            long tailleFichier = Files.size(cheminFichier);

            // Envoyer la taille du fichier
            envoyerReponse(CODE_213 + " " + tailleFichier);

            // Établir la connexion de données
            envoyerReponse(CODE_150);

            if (!etablirConnexionDonnees()) {
                envoyerReponse("425 Impossible d'ouvrir la connexion de données");
                return;
            }

            // Transférer le fichier via la connexion de données
            try (InputStream fichierEntree = Files.newInputStream(cheminFichier);
                 OutputStream sortie = socketDonnees.getOutputStream()) {

                byte[] tampon = new byte[4096];
                int octetsLus;

                while ((octetsLus = fichierEntree.read(tampon)) != -1) {
                    sortie.write(tampon, 0, octetsLus);
                }

                sortie.flush();
            }

            socketDonnees.close();

            envoyerReponse(CODE_226);
            System.out.println("[SERVEUR] Fichier envoyé avec succès : " + nomFichier + " (" + tailleFichier + " octets)");

        } catch (IOException e) {
            envoyerReponse(CODE_550);
            System.err.println("[ERREUR] Erreur lors du transfert du fichier : " + e.getMessage());
        }
    }

    /**
     * Gère la commande QUIT pour fermer la connexion
     */
    private void gererCommandeQuit() {
        envoyerReponse(CODE_221);
        String infoClient = utilisateurCourant != null ? utilisateurCourant : socketControle.getInetAddress().toString();
        System.out.println("[SERVEUR] Déconnexion du client : " + infoClient);
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Crée un socket serveur pour le mode passif sur un port disponible
     *
     * @return ServerSocket créé, ou null si aucun port disponible
     */
    private ServerSocket creerSocketServeurPassif() {
        for (int port = PORT_PASSIF_MIN; port <= PORT_PASSIF_MAX; port++) {
            try {
                return new ServerSocket(port);
            } catch (IOException e) {
                // Port occupé, essayer le suivant
            }
        }
        return null;
    }

    /**
     * Établit la connexion de données selon le mode configuré (actif ou passif)
     *
     * @return true si la connexion est établie, false sinon
     */
    private boolean etablirConnexionDonnees() {
        try {
            if ("actif".equals(modeTransfert)) {
                // Mode actif : le serveur se connecte au client
                socketDonnees = new Socket(adresseClientActif, portClientActif);
                System.out.println("[SERVEUR] Connexion de données établie en mode actif");
                return true;

            } else if ("passif".equals(modeTransfert)) {
                // Mode passif : le serveur attend la connexion du client
                if (socketServeurPassif != null) {
                    socketDonnees = socketServeurPassif.accept();
                    System.out.println("[SERVEUR] Connexion de données établie en mode passif");
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de l'établissement de la connexion de données : " + e.getMessage());
        }
        return false;
    }

    /**
     * Obtient la taille d'un fichier (0 pour les répertoires)
     *
     * @param chemin Chemin du fichier
     * @return Taille en octets
     */
    private long obtenirTailleFichier(Path chemin) {
        try {
            return Files.isDirectory(chemin) ? 0 : Files.size(chemin);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Envoie une réponse au client via la socket de contrôle
     *
     * @param reponse Message de réponse à envoyer
     */
    private void envoyerReponse(String reponse) {
        ecrivain.println(reponse);
        System.out.println("[SERVEUR] Réponse envoyée : " + reponse);
    }

    /**
     * Ferme toutes les connexions et libère les ressources
     */
    private void fermerConnexions() {
        try {
            if (socketDonnees != null && !socketDonnees.isClosed()) {
                socketDonnees.close();
            }
            if (socketServeurPassif != null && !socketServeurPassif.isClosed()) {
                socketServeurPassif.close();
            }
            if (lecteur != null) {
                lecteur.close();
            }
            if (ecrivain != null) {
                ecrivain.close();
            }
            if (socketControle != null && !socketControle.isClosed()) {
                socketControle.close();
            }
            System.out.println("[SERVEUR] Connexions fermées");
        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur lors de la fermeture : " + e.getMessage());
        }
    }

    // ==================== MÉTHODE MAIN ====================

    /**
     * Point d'entrée du serveur FTP
     * Crée un socket serveur et attend les connexions des clients
     *
     * @param args Arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  SERVEUR FTP - TP2 Exercices 1-4");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("Port de contrôle : " + PORT_CONTROLE);
        System.out.println("Répertoire racine : " + REPERTOIRE_RACINE);
        System.out.println("\nUtilisateurs autorisés :");
        System.out.println("  - anonymous (mot de passe quelconque)");
        System.out.println("  - foo (mot de passe : bar)");
        System.out.println("\nFonctionnalités :");
        System.out.println("  - Authentification (USER/PASS)");
        System.out.println("  - Mode actif (PORT) et passif (PASV)");
        System.out.println("  - Commandes : LIST, CWD, RETR, QUIT");
        System.out.println("  - Gestion des droits par utilisateur");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        try (ServerSocket socketServeur = new ServerSocket(PORT_CONTROLE)) {
            System.out.println("[SERVEUR] Serveur FTP démarré, en attente de connexions...\n");

            while (true) {
                // Accepter une connexion client
                Socket socketClient = socketServeur.accept();
                System.out.println("[SERVEUR] Nouveau client connecté : " + socketClient.getInetAddress());

                // Créer une instance du serveur pour gérer ce client
                ServeurFTP serveur = new ServeurFTP(socketClient);

                // Démarrer le traitement dans un nouveau thread (pour supporter plusieurs clients)
                new Thread(serveur::demarrer).start();
            }

        } catch (IOException e) {
            System.err.println("[ERREUR] Erreur fatale du serveur : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
