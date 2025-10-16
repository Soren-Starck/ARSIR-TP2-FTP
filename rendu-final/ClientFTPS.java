import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Scanner;

/**
 * ClientFTPS - client en ligne de commande pour le serveur {@link ServeurFTPS}.
 *
 * <p>Le client implémente toutes les fonctionnalités des exercices 1 à 4 :
 * authentification, sélection des modes actif/passif, transfert de liste et
 * de fichiers ainsi que l'interrogation temporelle.</p>
 */
public final class ClientFTPS {

    private static final String HOTE_DEFAUT = "localhost";
    private static final int PORT_DEFAUT = ServeurFTPS.PORT_CONTROLE;

    private Socket socketControle;
    private BufferedReader lecteur;
    private PrintWriter ecrivain;

    private ModeClient modeTransfert = ModeClient.PASV;
    private ServerSocket socketActif;
    private InetAddress adressePassif;
    private int portPassif;

    private ClientFTPS() {
    }

    /**
     * Établit la connexion de contrôle avec le serveur.
     */
    private boolean connecter(String hote, int port) {
        try {
            socketControle = new Socket(hote, port);
            lecteur = new BufferedReader(new InputStreamReader(socketControle.getInputStream(), StandardCharsets.UTF_8));
            ecrivain = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socketControle.getOutputStream(), StandardCharsets.UTF_8)), true);
            System.out.println(lireReponse());
            return true;
        } catch (IOException e) {
            System.err.println("[CLIENT] Impossible de se connecter : " + e.getMessage());
            return false;
        }
    }

    /**
     * Ferme la connexion de contrôle.
     */
    private void deconnecter() {
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
        } catch (IOException e) {
            System.err.println("[CLIENT] Erreur lors de la déconnexion : " + e.getMessage());
        }
    }

    private String envoyerCommande(String commande) throws IOException {
        System.out.println("--> " + commande);
        ecrivain.print(commande + "\r\n");
        ecrivain.flush();
        return lireReponse();
    }

    private String lireReponse() throws IOException {
        return lecteur.readLine();
    }

    /**
     * Active le mode actif en créant un {@link ServerSocket} d'attente.
     */
    private String activerPort() throws IOException {
        if (socketActif != null && !socketActif.isClosed()) {
            socketActif.close();
        }
        socketActif = new ServerSocket(0);
        int portLocal = socketActif.getLocalPort();
        InetAddress adresseLocale = socketControle.getLocalAddress();
        String[] octets = adresseLocale.getHostAddress().split("\\.");
        int p1 = portLocal / 256;
        int p2 = portLocal % 256;
        modeTransfert = ModeClient.ACTIF;
        adressePassif = null;
        portPassif = -1;
        return envoyerCommande(String.format(Locale.ROOT, "PORT %s,%s,%s,%s,%d,%d",
            octets[0], octets[1], octets[2], octets[3], p1, p2));
    }

    /**
     * Active le mode passif en analysant la réponse du serveur.
     */
    private String activerPasv() throws IOException {
        String reponse = envoyerCommande("PASV");
        if (reponse != null && reponse.startsWith("227")) {
            if (socketActif != null && !socketActif.isClosed()) {
                socketActif.close();
                socketActif = null;
            }
            int debut = reponse.indexOf('(');
            int fin = reponse.indexOf(')');
            if (debut >= 0 && fin > debut) {
                String[] elements = reponse.substring(debut + 1, fin).split(",");
                if (elements.length == 6) {
                    String host = String.format("%s.%s.%s.%s",
                        elements[0], elements[1], elements[2], elements[3]);
                    int p1 = Integer.parseInt(elements[4]);
                    int p2 = Integer.parseInt(elements[5]);
                    adressePassif = InetAddress.getByName(host);
                    portPassif = p1 * 256 + p2;
                    modeTransfert = ModeClient.PASV;
                }
            }
        }
        return reponse;
    }

    private Socket preparerConnexionPassif() throws IOException {
        if (adressePassif == null || portPassif <= 0) {
            throw new IOException("Mode passif non initialisé (utiliser PASV)");
        }
        return new Socket(adressePassif, portPassif);
    }

    private Socket attendreConnexionActive() throws IOException {
        if (socketActif == null) {
            throw new IOException("Mode actif non initialisé (utiliser PORT)");
        }
        return socketActif.accept();
    }

    private void recevoirListe(String commande) throws IOException {
        Socket socketDonnees = null;
        try {
            if (modeTransfert == ModeClient.PASV) {
                System.out.println(activerPasv());
                socketDonnees = preparerConnexionPassif();
            }
            String reponse = envoyerCommande(commande);
            System.out.println(reponse);
            if (!reponse.startsWith("150")) {
                if (socketDonnees != null) {
                    socketDonnees.close();
                }
                return;
            }
            if (modeTransfert == ModeClient.ACTIF) {
                socketDonnees = attendreConnexionActive();
            }
            try (BufferedReader lecteurDonnees = new BufferedReader(new InputStreamReader(socketDonnees.getInputStream(), StandardCharsets.UTF_8))) {
                String ligne;
                while ((ligne = lecteurDonnees.readLine()) != null) {
                    System.out.println(ligne);
                }
            }
            System.out.println(lireReponse());
        } finally {
            if (socketDonnees != null && !socketDonnees.isClosed()) {
                socketDonnees.close();
            }
        }
    }

    private void telechargerFichier(String commande, Path destination) throws IOException {
        Socket socketDonnees = null;
        try {
            if (modeTransfert == ModeClient.PASV) {
                System.out.println(activerPasv());
                socketDonnees = preparerConnexionPassif();
            }
            String reponse = envoyerCommande(commande);
            System.out.println(reponse);
            if (!reponse.startsWith("150")) {
                if (socketDonnees != null) {
                    socketDonnees.close();
                }
                return;
            }
            if (modeTransfert == ModeClient.ACTIF) {
                socketDonnees = attendreConnexionActive();
            }
            try (var in = socketDonnees.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println(lireReponse());
            System.out.println("Fichier enregistré sous : " + destination.toAbsolutePath());
        } finally {
            if (socketDonnees != null && !socketDonnees.isClosed()) {
                socketDonnees.close();
            }
        }
    }

    private void boucleInteraction() {
        Scanner scanner = new Scanner(System.in);
        try {
            System.out.println("Tapez 'help' pour l'aide.");
            while (true) {
                System.out.print("ftps> ");
                String ligne = scanner.nextLine().trim();
                if (ligne.isEmpty()) {
                    continue;
                }
                String[] parties = ligne.split(" ", 2);
                String commande = parties[0].toLowerCase(Locale.ROOT);
                String argument = parties.length > 1 ? parties[1] : "";
                switch (commande) {
                    case "user":
                        System.out.println(envoyerCommande("USER " + argument));
                        break;
                    case "pass":
                        System.out.println(envoyerCommande("PASS " + argument));
                        break;
                    case "quit":
                        System.out.println(envoyerCommande("QUIT"));
                        return;
                    case "port":
                        System.out.println(activerPort());
                        break;
                    case "pasv":
                        System.out.println(activerPasv());
                        break;
                    case "list":
                        recevoirListe(argument.isEmpty() ? "LIST" : "LIST " + argument);
                        break;
                    case "retr":
                        if (argument.isEmpty()) {
                            System.out.println("Usage : retr <fichier>");
                            break;
                        }
                        Path destination = Paths.get(argument).getFileName();
                        telechargerFichier("RETR " + argument, destination);
                        break;
                    case "pwd":
                        System.out.println(envoyerCommande("PWD"));
                        break;
                    case "cwd":
                        if (argument.isEmpty()) {
                            System.out.println("Usage : cwd <répertoire>");
                            break;
                        }
                        System.out.println(envoyerCommande("CWD " + argument));
                        break;
                    case "date":
                    case "hour":
                    case "full":
                        System.out.println(envoyerCommande(commande.toUpperCase(Locale.ROOT)));
                        break;
                    case "shutdown":
                        System.out.println(envoyerCommande("SHUTDOWN"));
                        return;
                    case "noop":
                        System.out.println(envoyerCommande("NOOP"));
                        break;
                    case "help":
                        afficherAide();
                        break;
                    default:
                        System.out.println(envoyerCommande(ligne));
                }
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] Erreur : " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    private static void afficherAide() {
        System.out.println("Commandes disponibles :");
        System.out.println("  user <login>     -> USER");
        System.out.println("  pass <mdp>       -> PASS");
        System.out.println("  pasv / port      -> Choix du mode de transfert");
        System.out.println("  list [chemin]    -> LIST");
        System.out.println("  retr <fichier>   -> RETR");
        System.out.println("  pwd / cwd        -> Navigation");
        System.out.println("  date | hour | full -> Informations temporelles");
        System.out.println("  noop             -> NOOP");
        System.out.println("  shutdown         -> arrêt du serveur (localhost uniquement)");
        System.out.println("  quit             -> Quitter");
    }

    public static void main(String[] args) {
        String hote = args.length > 0 ? args[0] : HOTE_DEFAUT;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : PORT_DEFAUT;

        ClientFTPS client = new ClientFTPS();
        if (client.connecter(hote, port)) {
            try {
                client.boucleInteraction();
            } finally {
                client.deconnecter();
            }
        }
    }

    private enum ModeClient {
        ACTIF,
        PASV
    }
}
