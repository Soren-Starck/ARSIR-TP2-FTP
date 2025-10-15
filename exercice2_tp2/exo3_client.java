import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.nio.file.*;

public class exo3_client {
    public static void main(String[] args) {
        final String SERVER_ADDRESS = "localhost"; // Adresse du serveur
        final int SERVER_PORT = 21; // Port du serveur
        String user;
        String password;
        //FTPClient ftp;

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             InputStream dataIn = socket.getInputStream();
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            System.out.println("Client : connecté au serveur.");

            Scanner scanner = new Scanner(System.in);
            System.out.print("Client : USER => ");
            user = scanner.nextLine();
            out.println("USER " + user);
            String responseUser = in.readLine();
            System.out.println("Client : réponse du serveur => " + responseUser);

            if (!user.equals("anonymous")) {
                // Pas de mot de passe pour utilisateur anonyme
                System.out.print("Client : PASS => ");
                password = scanner.nextLine();
                out.println("PASS " + password);
                String responsePass = in.readLine();
                System.out.println("Client : réponse du serveur => " + responsePass);
            }

            System.out.print("Client : PASV or PORT => ");
            String mode = scanner.nextLine();
            out.println(mode);
            String responseMode = in.readLine();
            System.out.println("Client : réponse du serveur => " + responseMode);

            while(true){
                System.out.print("Client : Commande (LIST, CWD, RETR, QUIT) => ");
                String command = scanner.nextLine();
                out.println(command);
                if(command.toUpperCase().equals("QUIT")){
                    break;
                }

                String response = in.readLine();
                if (command.toUpperCase().startsWith("LIST")) {
                    // Lire les lignes jusqu'à la fin de la réponse (226)
                    while(!response.contains("226")){
                        System.out.println("Client : reçu => " + response);
                        response = in.readLine();
                    }
                }
                long fileSize = 0;
                if (command.toUpperCase().startsWith("RETR")){
                    Path outputFile = Paths.get(command.split(" ")[1]);
                    if (response.contains("213")){
                        String size = response.split(" ")[1];
                        fileSize = Long.parseLong(size);
                    }
                    try (OutputStream fileOut = Files.newOutputStream(outputFile)) {
                        byte[] buffer = new byte[4096];
                        long bytesRead = 0;

                        while (bytesRead < fileSize) {
                            int read = dataIn.read(buffer, 0, (int)Math.min(buffer.length, fileSize - bytesRead));
                            if (read == -1) break;
                            fileOut.write(buffer, 0, read);
                            bytesRead += read;
                        }
                    }
                    System.out.println("Downloaded: " + command.split(" ")[1] + " (" + fileSize + " bytes)");
                }

                System.out.println("Client : réponse du serveur => " + response);
            }
        } catch (IOException e) {
            System.err.println("Exception client => " + e.getMessage());
        }
    }
}