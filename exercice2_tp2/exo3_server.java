import java.io.*;
import java.net.*;
import java.nio.file.*;

public class exo3_server {
    public static void main(String[] args) {
        final int PORT = 21;
        final String USER = "foo";
        final String PASS = "bar";

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("FTP : démarré sur " + PORT + ", en attente d'un client...");

            // Accepter une connexion client
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 OutputStream dataOut = socket.getOutputStream();
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("FTP : client connecté => " + socket.getInetAddress());

                String command;
                String commandType;
                String commandData;

                Path directory = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

                // Boucle de traitement des commandes du client
                while ((command = in.readLine()) != null) {
                    System.out.println("FTP : commande reçue => " + command);
                    // Séparer la commande et ses arguments
                    commandType = command.split(" ")[0];

                    switch (commandType.toUpperCase()) {
                        case "USER":
                            commandData = command.split(" ")[1];
                            if (commandData.equals(USER)) {
                                out.println("331 Utilisateur a besoin du MDP.");
                            }
                            else if (commandData.equals("anonymous")) {
                                out.println("230 Utilisateur anonyme connecté.");
                            }
                            else {
                                out.println("530 Pas connecté.");
                            }
                            break;
                        case "PASS":
                            commandData = command.split(" ")[1];
                            if (commandData.equals(PASS)) {
                                out.println("230 Utilisateur connecté.");
                            }
                            else {
                                out.println("FTP : 430 Identifiant ou MDP incorrect.");
                            }
                            break;
                        case "PORT":
                            commandData = command.split(" ")[1];
                            out.println("200 Mode actif activé sur " + commandData + ".");
                            break;
                        case "PASV":
                            out.println("227 Mode passif activé.");
                            break;
                        case "LIST":
                            if (!Files.isDirectory(directory)) {
                                out.println("501 Invalid directory: " + directory);
                                return;
                            }
                            out.println("150 Ouverture de la connection pour ls.");
                            try (var stream = Files.list(directory)) {
                                stream.forEach(path -> out.println(path.getFileName()));
                            }
                            out.println("226 Fin de la liste.");
                            break;
                        case "CWD":
                            commandData = command.split(" ")[1];
                            Path newPath = directory.resolve(commandData).normalize();

                            if (commandData.equals("..")) {
                                directory = directory.getParent() != null ? directory.getParent() : directory;
                                out.println("250 Répertoire courant changé.");
                            } else if (Files.isDirectory(newPath)) {
                                directory = newPath;
                                out.println("250 Répertoire courant changé.");
                            } else {
                                System.out.println("501 Invalid directory => " + commandData);
                            }
                            break;
                        case "RETR":
                            commandData = command.split(" ")[1];
                            Path filePath = directory.resolve(commandData);
                            long fileSize = Files.size(filePath);

                            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                                out.println("150 Ouverture de la connection pour dw.");
                                out.println("213 " + fileSize); // send file size

                                // send file bytes
                                try (InputStream fileIn = Files.newInputStream(filePath)) {
                                    fileIn.transferTo(dataOut);
                                }
                                System.out.println("226 Transfert de " + commandData + " terminé.");
                            } else {
                                out.println("501 Fichier pas trouvé.");
                            }
                            break;
                        case "QUIT":
                            out.println("au revoir !");
                            System.out.println("FTP : déconnexion demandée par le client.");
                            return; // Sort de la boucle et ferme le serveur

                        default:
                            out.println("FTP : commande inconnue");
                    }
                }
            }
            // Fermeture du serveur après la déconnexion du client
            System.out.println("FTP : fermé.");
        } catch (IOException e) {
            System.err.println("Exception serveur => " + e.getMessage());
        }
    }
}
