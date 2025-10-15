public class Jeu {
    private char[][] grille;
    private char joueurActuel;
    private boolean partieTerminee;
    private char gagnant;

    public Jeu() {
        grille = new char[3][3];
        initialiserGrille();
        joueurActuel = 'X';
        partieTerminee = false;
        gagnant = ' ';
    }

    private void initialiserGrille() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                grille[i][j] = ' ';
            }
        }
    }

    public synchronized boolean estCoupValide(int ligne, int colonne) {
        if (ligne < 0 || ligne > 2 || colonne < 0 || colonne > 2) {
            return false;
        }
        if (grille[ligne][colonne] != ' ') {
            return false;
        }
        if (partieTerminee) {
            return false;
        }
        return true;
    }

    public synchronized boolean jouerCoup(int ligne, int colonne, char symbole) {
        if (!estCoupValide(ligne, colonne)) {
            return false;
        }
        if (symbole != joueurActuel) {
            return false;
        }

        grille[ligne][colonne] = symbole;

        if (verifierVictoire(symbole)) {
            partieTerminee = true;
            gagnant = symbole;
        } else if (verifierMatchNul()) {
            partieTerminee = true;
            gagnant = 'N';
        } else {
            joueurActuel = (joueurActuel == 'X') ? 'O' : 'X';
        }

        return true;
    }

    private boolean verifierVictoire(char symbole) {
        for (int i = 0; i < 3; i++) {
            if (grille[i][0] == symbole && grille[i][1] == symbole && grille[i][2] == symbole) {
                return true;
            }
        }

        for (int j = 0; j < 3; j++) {
            if (grille[0][j] == symbole && grille[1][j] == symbole && grille[2][j] == symbole) {
                return true;
            }
        }

        if (grille[0][0] == symbole && grille[1][1] == symbole && grille[2][2] == symbole) {
            return true;
        }
        if (grille[0][2] == symbole && grille[1][1] == symbole && grille[2][0] == symbole) {
            return true;
        }

        return false;
    }

    private boolean verifierMatchNul() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (grille[i][j] == ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized char getJoueurActuel() {
        return joueurActuel;
    }

    public synchronized boolean estPartieTerminee() {
        return partieTerminee;
    }

    public synchronized char getGagnant() {
        return gagnant;
    }

    public synchronized String obtenirGrille() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int i = 0; i < 3; i++) {
            sb.append(" ");
            for (int j = 0; j < 3; j++) {
                sb.append(grille[i][j] == ' ' ? '.' : grille[i][j]);
                if (j < 2) sb.append(" | ");
            }
            sb.append("\n");
            if (i < 2) {
                sb.append("-----------\n");
            }
        }
        return sb.toString();
    }

    public synchronized void definirJoueurDepart(char symbole) {
        joueurActuel = symbole;
    }
}
