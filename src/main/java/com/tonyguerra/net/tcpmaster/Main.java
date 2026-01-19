package com.tonyguerra.net.tcpmaster;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import com.tonyguerra.net.tcpmaster.core.TcpClient;
import com.tonyguerra.net.tcpmaster.core.TcpServer;

public final class Main {
    public static void main(String[] args) {
        try (final var scanner = new Scanner(System.in)) {
            System.out.println("Welcome, please select an option:");
            System.out.println("> 1 - Server");
            System.out.println("> 2 - Client");
            System.out.println("> 0 - Exit");

            final int option = Integer.parseInt(scanner.nextLine());

            final int port;
            switch (option) {
                case 1:
                    System.out.print("Please set a port: ");
                    port = Integer.parseInt(scanner.nextLine());

                    startServer(scanner, port);
                    break;
                case 2:
                    System.out.print("Please set an ip: ");
                    final String ip = scanner.nextLine();
                    System.out.print("Now set a port: ");
                    port = Integer.parseInt(scanner.nextLine());

                    startClient(scanner, ip, port);
                    break;
                default:
                    throw new IllegalArgumentException("Please select a valid option");
            }

        } catch (NumberFormatException ex) {
            System.err.println("Please select a valid number");
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }

    private static void startServer(Scanner scanner, int port) {
        try (final var server = new TcpServer(port)) {
            server.start();

            while (server.isStarted()) {
                final String text = scanner.nextLine();

                if (text.equals("/exit")) {
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void startClient(Scanner scanner, String ip, int port) {
        try (final var client = new TcpClient(ip, port)) {
            client.connect();

            System.out.print("Set a file: ");
            final String filename = scanner.nextLine();

            final var file = Path.of(filename);
            final long fileSize = Files.size(file);

            // NEW: tell the server where to store + size
            final String remotePath = file.getFileName().toString(); // simplest
            final String serverResponse = client.sendMessage("!file.put " + remotePath + " " + fileSize, false);
            System.out.println("SERVER >> " + serverResponse);

            try (final var in = Files.newInputStream(file)) {
                client.sendBinary(in, fileSize);
            }

            // Temporary: trigger a read so we can consume server's "OK STORED ..."
            final String confirm = client.readNextResponse();
            System.out.println("SERVER >> " + confirm);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
