import java.io.*;
import java.net.Socket;

public class Joueur extends Thread {
    private Socket socket;
    private Jeu jeu;
    private char symbole;
    private Joueur adversaire;
    private BufferedReader entree;
    private PrintWriter sortie;
    private boolean estPret;

    public Joueur(Socket socket, Jeu jeu, char symbole) {
        this.socket = socket;
        this.jeu = jeu;
        this.symbole = symbole;
        this.estPret = false;
        try {
            entree = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sortie = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'initialisation du joueur: " + e.getMessage());
        }
    }

    public void definirAdversaire(Joueur adversaire) {
        this.adversaire = adversaire;
    }

    public char getSymbole() {
        return symbole;
    }

    public void envoyerMessage(String message) {
        sortie.println(message);
    }

    @Override
    public void run() {
        try {
            envoyerMessage("BIENVENUE " + symbole);
            envoyerMessage("ATTENTE");

            synchronized (this) {
                estPret = true;
                notifyAll();
            }

            while (!jeu.estPartieTerminee() && !socket.isClosed()) {
                if (jeu.getJoueurActuel() == symbole) {
                    envoyerMessage("VOTRE_TOUR");
                    envoyerMessage("GRILLE" + jeu.obtenirGrille());

                    String ligne = entree.readLine();
                    if (ligne == null) {
                        break;
                    }

                    traiterCoup(ligne.trim());
                } else {
                    synchronized (this) {
                        wait(100);
                    }
                }
            }

            if (jeu.estPartieTerminee()) {
                envoyerMessage("GRILLE" + jeu.obtenirGrille());
                char gagnant = jeu.getGagnant();
                if (gagnant == 'N') {
                    envoyerMessage("MATCH_NUL");
                } else if (gagnant == symbole) {
                    envoyerMessage("VICTOIRE");
                } else {
                    envoyerMessage("DEFAITE");
                }
            }

        } catch (IOException e) {
            System.err.println("Erreur de communication avec le joueur " + symbole + ": " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Thread interrompu pour le joueur " + symbole);
        } finally {
            fermerConnexion();
        }
    }

    private void traiterCoup(String coup) {
        try {
            String[] parties = coup.split(" ");
            if (parties.length != 2) {
                envoyerMessage("ERREUR Format invalide. Utilisez: ligne colonne");
                return;
            }

            int ligne = Integer.parseInt(parties[0]);
            int colonne = Integer.parseInt(parties[1]);

            if (jeu.jouerCoup(ligne, colonne, symbole)) {
                envoyerMessage("COUP_VALIDE");
                if (adversaire != null) {
                    synchronized (adversaire) {
                        adversaire.notifyAll();
                    }
                    adversaire.envoyerMessage("COUP_ADVERSAIRE " + ligne + " " + colonne);
                }
            } else {
                envoyerMessage("COUP_INVALIDE");
            }
        } catch (NumberFormatException e) {
            envoyerMessage("ERREUR Les coordonnees doivent etre des nombres");
        }
    }

    private void fermerConnexion() {
        try {
            if (entree != null) entree.close();
            if (sortie != null) sortie.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erreur lors de la fermeture de la connexion: " + e.getMessage());
        }
    }

    public synchronized void attendrePreparation() throws InterruptedException {
        while (!estPret) {
            wait();
        }
    }
}
