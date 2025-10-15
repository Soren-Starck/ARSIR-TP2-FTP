import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ServeurFTPS - implémentation pédagogique d'un mini serveur FTP sécurisé.
 *
 * <p>Le serveur implémente l'ensemble des fonctionnalités demandées dans les
 * exercices 1 à 4 du TP 2 : authentification, modes actif/passif, commandes
 * de navigation/listing/téléchargement et extensions temporelles. Toutes les
 * réponses suivent la convention FTP (codes numériques à trois chiffres).
 */
public final class ServeurFTPS {

    /** Port d'écoute de la connexion de contrôle (non privilégié). */
    public static final int PORT_CONTROLE = 2121;

    /** Répertoire racine contenant les ressources du serveur. */
    private static final Path REPERTOIRE_RACINE =
        Paths.get("rendu-final", "donnees").toAbsolutePath().normalize();

    /** Mapping login -> mot de passe attendu. */
    private static final Map<String, String> UTILISATEURS = new HashMap<>();

    /** Mapping login -> répertoire de base. */
    private static final Map<String, Path> REPERTOIRES_UTILISATEURS = new HashMap<>();

    static {
        UTILISATEURS.put("foo", "bar");
        UTILISATEURS.put("anonymous", "");

        REPERTOIRES_UTILISATEURS.put("foo", REPERTOIRE_RACINE);
        REPERTOIRES_UTILISATEURS.put("anonymous",
            REPERTOIRE_RACINE.resolve("anonymous"));
    }

    /** Nombre maximum de clients traités en parallèle. */
    private static final int NB_THREADS = 16;

    /** Fabrique de threads nommés pour faciliter le débogage. */
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("client-ftps-" + thread.threadId());
        thread.setDaemon(true);
        return thread;
    };

    /** Pool de threads pour la gestion des clients. */
    private final ThreadPoolExecutor executant;

    /** Socket serveur principal. */
    private ServerSocket socketServeur;

    public ServeurFTPS() {
        executant = (ThreadPoolExecutor) Executors.newFixedThreadPool(NB_THREADS, THREAD_FACTORY);
    }

    /**
     * Démarre le serveur et accepte les connexions entrantes.
     */
    public void demarrerServeur() {
        verifierRepertoires();
        try (ServerSocket serveur = new ServerSocket(PORT_CONTROLE)) {
            socketServeur = serveur;
            logInfo("Serveur FTPS démarré sur le port %d", PORT_CONTROLE);
            while (!serveur.isClosed()) {
                Socket client = serveur.accept();
                executant.execute(new GestionClient(client));
            }
        } catch (IOException e) {
            logErreur("Erreur fatale du serveur : %s", e.getMessage());
        } finally {
            executant.shutdownNow();
        }
    }

    /**
     * Vérifie l'existence des répertoires et les crée si nécessaire.
     */
    private static void verifierRepertoires() {
        try {
            Files.createDirectories(REPERTOIRE_RACINE);
            for (Path path : REPERTOIRES_UTILISATEURS.values()) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de préparer les répertoires", e);
        }
    }

    /**
     * Arrête proprement le serveur (utilisé lors de la commande SHUTDOWN).
     */
    private synchronized void arreterServeur() {
        if (socketServeur != null && !socketServeur.isClosed()) {
            try {
                socketServeur.close();
            } catch (IOException e) {
                logErreur("Erreur lors de l'arrêt du serveur : %s", e.getMessage());
            }
        }
    }

    /**
     * Gestionnaire d'une session client.
     */
    private final class GestionClient implements Runnable {

        /** Socket de contrôle associée au client. */
        private final Socket socketControle;

        /** Lecteur pour les commandes entrantes. */
        private final BufferedReader lecteur;

        /** Écrivain pour les réponses. */
        private final PrintWriter ecrivain;

        /** Socket serveur utilisé en mode passif. */
        private ServerSocket socketPassif;

        /** Socket de données en cours. */
        private Socket socketDonnees;

        /** Adresse enregistrée pour le mode actif. */
        private InetAddress adresseActive;

        /** Port enregistré pour le mode actif. */
        private int portActif;

        /** Indique si l'utilisateur est authentifié. */
        private boolean estAuthentifie;

        /** Identité de l'utilisateur authentifié. */
        private String utilisateur;

        /** Répertoire courant. */
        private Path repertoireCourant;

        /** Répertoire de base autorisé. */
        private Path repertoireBase;

        /** Mode de transfert courant. */
        private ModeTransfert modeTransfert;

        GestionClient(Socket socket) throws IOException {
            this.socketControle = socket;
            this.lecteur = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.ecrivain = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            this.modeTransfert = ModeTransfert.NON_DEFINI;
        }

        @Override
        public void run() {
            logInfo("Client connecté depuis %s", socketControle.getRemoteSocketAddress());
            envoyerReponse("220 Service FTP pédagogique prêt");
            try {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    ligne = ligne.trim();
                    if (ligne.isEmpty()) {
                        continue;
                    }
                    logInfo("Commande reçue (%s) : %s", socketControle.getRemoteSocketAddress(), ligne);
                    if (!traiterCommande(ligne)) {
                        break;
                    }
                }
            } catch (SocketException e) {
                logInfo("Client %s déconnecté (%s)", socketControle.getRemoteSocketAddress(), e.getMessage());
            } catch (IOException e) {
                logErreur("Erreur côté client %s : %s", socketControle.getRemoteSocketAddress(), e.getMessage());
            } finally {
                fermerRessources();
            }
        }

        /**
         * Analyse et exécute une commande FTP.
         *
         * @param ligne ligne complète reçue.
         * @return {@code false} si la session doit être fermée.
         */
        private boolean traiterCommande(String ligne) {
            String[] morceaux = ligne.split(" ", 2);
            String commande = morceaux[0].toUpperCase(Locale.ROOT);
            String argument = morceaux.length > 1 ? morceaux[1].trim() : "";

            switch (commande) {
                case "USER":
                    traiterUser(argument);
                    break;
                case "PASS":
                    traiterPass(argument);
                    break;
                case "QUIT":
                    envoyerReponse("221 Fermeture de la session");
                    return false;
                case "SYST":
                    envoyerReponse("215 UNIX Type: L8");
                    break;
                case "TYPE":
                    envoyerReponse("200 Type défini sur " + argument);
                    break;
                case "FEAT":
                    envoyerReponse("211-Extensions disponibles\r\n DATE\r\n HOUR\r\n FULL\r\n211 Fin des extensions");
                    break;
                case "PWD":
                    repondrePwd();
                    break;
                case "CWD":
                    traiterCwd(argument);
                    break;
                case "LIST":
                    traiterList(argument);
                    break;
                case "PORT":
                    traiterPort(argument);
                    break;
                case "PASV":
                    traiterPasv();
                    break;
                case "RETR":
                    traiterRetr(argument);
                    break;
                case "DATE":
                    repondreDate();
                    break;
                case "HOUR":
                    repondreHeure();
                    break;
                case "FULL":
                    repondreDateHeure();
                    break;
                case "SHUTDOWN":
                    traiterShutdown();
                    break;
                case "NOOP":
                    envoyerReponse("200 Commande NOOP exécutée");
                    break;
                default:
                    envoyerReponse("502 Commande non implémentée");
            }
            return true;
        }

        private void traiterUser(String argument) {
            if (argument.isEmpty()) {
                envoyerReponse("501 Syntaxe USER invalide");
                return;
            }
            if (!UTILISATEURS.containsKey(argument)) {
                envoyerReponse("530 Utilisateur inconnu");
                utilisateur = null;
                estAuthentifie = false;
                return;
            }
            utilisateur = argument;
            repertoireBase = REPERTOIRES_UTILISATEURS.getOrDefault(argument, REPERTOIRE_RACINE);
            envoyerReponse("331 Nom d'utilisateur accepté, mot de passe requis");
        }

        private void traiterPass(String argument) {
            if (utilisateur == null) {
                envoyerReponse("503 Séquence de commandes incorrecte");
                return;
            }
            String motDePasseAttendu = UTILISATEURS.get(utilisateur);
            if (Objects.equals(utilisateur, "anonymous") || Objects.equals(motDePasseAttendu, argument)) {
                estAuthentifie = true;
                repertoireCourant = repertoireBase;
                envoyerReponse("230 Connexion réussie");
            } else {
                envoyerReponse("530 Mot de passe incorrect");
                estAuthentifie = false;
                utilisateur = null;
            }
        }

        private void repondrePwd() {
            if (!verifierAuthentification()) {
                return;
            }
            Path relatif = repertoireBase.relativize(repertoireCourant);
            String affichage = "/" + relatif.toString().replace('\\', '/');
            if (affichage.endsWith("/")) {
                affichage = affichage.substring(0, affichage.length() - 1);
            }
            envoyerReponse("257 \"" + (affichage.isEmpty() ? "/" : affichage) + "\"");
        }

        private void traiterCwd(String argument) {
            if (!verifierAuthentification()) {
                return;
            }
            if (argument.isEmpty()) {
                envoyerReponse("501 Syntaxe CWD invalide");
                return;
            }
            Path destination = normaliserChemin(argument);
            if (destination == null || !Files.isDirectory(destination)) {
                envoyerReponse("550 Répertoire introuvable");
                return;
            }
            repertoireCourant = destination;
            envoyerReponse("250 Répertoire courant changé");
        }

        private void traiterList(String argument) {
            if (!verifierAuthentification()) {
                return;
            }
            Optional<Path> cible = argument.isEmpty() ? Optional.of(repertoireCourant)
                : Optional.ofNullable(normaliserChemin(argument));
            if (cible.isEmpty() || !Files.exists(cible.get())) {
                envoyerReponse("550 Chemin inexistant");
                return;
            }
            try {
                Socket dataSocket = ouvrirConnexionDonnees();
                if (dataSocket == null) {
                    envoyerReponse("425 Impossible d'ouvrir la connexion de données");
                    return;
                }
                envoyerReponse("150 Ouverture de la connexion de données");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {
                    Files.list(cible.get()).sorted().forEach(path -> {
                        try {
                            writer.write(formaterEntreeListe(path));
                            writer.write("\r\n");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                envoyerReponse("226 Liste terminée");
            } catch (RuntimeException | IOException e) {
                envoyerReponse("451 Erreur lors du listage : " + e.getMessage());
            } finally {
                fermerConnexionDonnees();
            }
        }

        private void traiterPort(String argument) {
            if (!verifierAuthentification()) {
                return;
            }
            String[] parties = argument.split(",");
            if (parties.length != 6) {
                envoyerReponse("501 Syntaxe PORT invalide");
                return;
            }
            try {
                String adresse = String.format("%s.%s.%s.%s", parties[0], parties[1], parties[2], parties[3]);
                int port = Integer.parseInt(parties[4]) * 256 + Integer.parseInt(parties[5]);
                adresseActive = InetAddress.getByName(adresse);
                portActif = port;
                modeTransfert = ModeTransfert.ACTIF;
                fermerSocketPassif();
                envoyerReponse("200 Mode actif configuré");
            } catch (Exception e) {
                envoyerReponse("501 Paramètres PORT invalides");
            }
        }

        private void traiterPasv() {
            if (!verifierAuthentification()) {
                return;
            }
            try {
                fermerSocketPassif();
                socketPassif = creerSocketPassif();
                if (socketPassif == null) {
                    envoyerReponse("421 Aucun port passif disponible");
                    return;
                }
                modeTransfert = ModeTransfert.PASSIF;
                InetAddress adresseLocale = socketControle.getLocalAddress();
                int port = socketPassif.getLocalPort();
                String[] octets = adresseLocale.getHostAddress().split("\\.");
                int p1 = port / 256;
                int p2 = port % 256;
                envoyerReponse(String.format(Locale.ROOT,
                    "227 Entrée en mode passif (%s,%s,%s,%s,%d,%d)",
                    octets[0], octets[1], octets[2], octets[3], p1, p2));
            } catch (IOException e) {
                envoyerReponse("421 Erreur mode passif : " + e.getMessage());
            }
        }

        private void traiterRetr(String argument) {
            if (!verifierAuthentification()) {
                return;
            }
            if (argument.isEmpty()) {
                envoyerReponse("501 Syntaxe RETR invalide");
                return;
            }
            Path fichier = normaliserChemin(argument);
            if (fichier == null || !Files.exists(fichier) || Files.isDirectory(fichier)) {
                envoyerReponse("550 Fichier inexistant");
                return;
            }
            try {
                Socket dataSocket = ouvrirConnexionDonnees();
                if (dataSocket == null) {
                    envoyerReponse("425 Impossible d'ouvrir la connexion de données");
                    return;
                }
                envoyerReponse("150 Début du transfert de " + fichier.getFileName());
                try (var in = Files.newInputStream(fichier);
                     var out = dataSocket.getOutputStream()) {
                    in.transferTo(out);
                }
                envoyerReponse("226 Transfert terminé");
            } catch (IOException e) {
                envoyerReponse("451 Erreur de lecture : " + e.getMessage());
            } finally {
                fermerConnexionDonnees();
            }
        }

        private void repondreDate() {
            if (!verifierAuthentification()) {
                return;
            }
            String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            envoyerReponse("211 Date serveur : " + date);
        }

        private void repondreHeure() {
            if (!verifierAuthentification()) {
                return;
            }
            String heure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            envoyerReponse("211 Heure serveur : " + heure);
        }

        private void repondreDateHeure() {
            if (!verifierAuthentification()) {
                return;
            }
            String full = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            envoyerReponse("211 Horodatage complet : " + full);
        }

        private void traiterShutdown() {
            InetAddress adresse = socketControle.getInetAddress();
            if (!adresse.isLoopbackAddress()) {
                envoyerReponse("550 Commande SHUTDOWN réservée à l'administrateur local");
                return;
            }
            envoyerReponse("221 Arrêt du serveur en cours");
            fermerRessources();
            arreterServeur();
        }

        private boolean verifierAuthentification() {
            if (!estAuthentifie) {
                envoyerReponse("530 Authentification requise");
                return false;
            }
            return true;
        }

        private Path normaliserChemin(String argument) {
            Path destination = repertoireCourant.resolve(argument).normalize();
            if (!destination.startsWith(repertoireBase)) {
                return null; // tentative d'évasion
            }
            return destination;
        }

        private Socket ouvrirConnexionDonnees() throws IOException {
            if (modeTransfert == ModeTransfert.ACTIF) {
                socketDonnees = new Socket(adresseActive, portActif);
                return socketDonnees;
            }
            if (modeTransfert == ModeTransfert.PASSIF && socketPassif != null) {
                socketDonnees = socketPassif.accept();
                return socketDonnees;
            }
            return null;
        }

        private void fermerConnexionDonnees() {
            if (socketDonnees != null) {
                try {
                    socketDonnees.close();
                } catch (IOException ignored) {
                }
                socketDonnees = null;
            }
        }

        private void fermerSocketPassif() {
            if (socketPassif != null && !socketPassif.isClosed()) {
                try {
                    socketPassif.close();
                } catch (IOException ignored) {
                }
            }
            socketPassif = null;
        }

        private void fermerRessources() {
            fermerConnexionDonnees();
            fermerSocketPassif();
            try {
                lecteur.close();
            } catch (IOException ignored) {
            }
            ecrivain.close();
            try {
                socketControle.close();
            } catch (IOException ignored) {
            }
        }

        private ServerSocket creerSocketPassif() throws IOException {
            for (int port = 5000; port <= 5100; port++) {
                try {
                    return new ServerSocket(port);
                } catch (IOException ignored) {
                }
            }
            return null;
        }

        private String formaterEntreeListe(Path path) {
            try {
                String type = Files.isDirectory(path) ? "d" : "-";
                long taille = Files.isDirectory(path) ? 0L : Files.size(path);
                return String.format(Locale.ROOT, "%srwx------ 1 ftp ftp %8d %s", type, taille, path.getFileName());
            } catch (IOException e) {
                return path.getFileName().toString();
            }
        }

        private void envoyerReponse(String message) {
            ecrivain.print(message + "\r\n");
            ecrivain.flush();
        }
    }

    /**
     * Mode de transfert des données.
     */
    private enum ModeTransfert {
        ACTIF,
        PASSIF,
        NON_DEFINI
    }

    private static void logInfo(String message, Object... args) {
        System.out.printf(Locale.ROOT, "[INFO] " + message + "%n", args);
    }

    private static void logErreur(String message, Object... args) {
        System.err.printf(Locale.ROOT, "[ERREUR] " + message + "%n", args);
    }

    /**
     * Point d'entrée principal du serveur.
     */
    public static void main(String[] args) {
        ServeurFTPS serveur = new ServeurFTPS();
        serveur.demarrerServeur();
    }
}
