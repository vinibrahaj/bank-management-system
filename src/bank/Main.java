package bank;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    private final Scanner input = new Scanner(System.in);
    private final Bank bank;
    private Long session;
    private boolean running = true;

    private Main(Bank bank) { this.bank = bank; }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: ./bank.sh run [configuration.properties]");
            System.exit(1);
        }
        try {
            Path configuration = Path.of(args.length == 0 ? "config/bank.properties" : args[0]).toAbsolutePath();
            Properties config = new Properties();
            try (Reader reader = Files.newBufferedReader(configuration)) { config.load(reader); }
            Path directory = configuration.getParent().resolve(config.getProperty("data.dir", "../data")).normalize();
            try (Bank bank = new Bank(directory, Bank.money(config.getProperty("opening.balance", "69.00")))) {
                new Main(bank).run();
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Cannot run bank: " + e.getMessage());
            System.exit(1);
        }
    }

    private void run() {
        System.out.println("Bank Management System");
        while (running) {
            try {
                if (session == null) home(); else menu();
            } catch (NoSuchElementException e) {
                running = false;
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Storage error: " + e.getMessage() + ". No change was committed.");
            }
        }
    }

    private void home() throws IOException {
        System.out.println("\n1 Login\n2 Create account\n0 Exit");
        switch (ask("Choice: ")) {
            case "1" -> {
                long number = number("Account number: ");
                if (bank.login(number, password("Password: "))) session = number;
                else System.out.println("Invalid credentials or closed account.");
            }
            case "2" -> {
                Bank.Profile profile = new Bank.Profile(ask("Full name: "), ask("Birth date (YYYY-MM-DD): "),
                        ask("Gender: "), ask("Address: "), ask("Phone: "), ask("Email: "), ask("Identification: "));
                session = bank.create(profile, password("Password (8+ characters): "));
                System.out.println("Account created: " + session);
            }
            case "0" -> running = false;
            default -> System.out.println("Choose a valid option.");
        }
    }

    private void menu() throws IOException {
        System.out.println("\n1 Balance\n2 Account details\n3 Transfer\n4 Statement\n5 Close account\n6 Logout\n0 Exit");
        switch (ask("Choice: ")) {
            case "1" -> System.out.println("Balance: " + bank.account(session).balance());
            case "2" -> {
                Bank.Account account = bank.account(session);
                Bank.Profile profile = account.profile();
                System.out.printf("Account: %d%nName: %s%nBirth date: %s%nGender: %s%nAddress: %s%nPhone: %s%nEmail: %s%nIdentification: %s%n",
                        account.number(), profile.name(), profile.birthDate(), profile.gender(), profile.address(),
                        profile.phone(), profile.email(), profile.identification());
            }
            case "3" -> {
                long recipient = number("Recipient account: ");
                var amount = Bank.money(ask("Amount: "));
                bank.transfer(session, recipient, amount, ask("Remarks: "));
                System.out.println("Transfer completed.");
            }
            case "4" -> {
                var entries = bank.statement(session);
                if (entries.isEmpty()) System.out.println("No transfers yet.");
                for (var entry : entries) System.out.printf("%d %s %s %s %d | %s%n", entry.number(), entry.time(),
                        entry.sender() == session ? "DEBIT" : "CREDIT", entry.amount(),
                        entry.sender() == session ? entry.receiver() : entry.sender(), entry.remarks());
            }
            case "5" -> {
                if (ask("Type CLOSE to confirm: ").equals("CLOSE")) {
                    bank.closeAccount(session);
                    session = null;
                    System.out.println("Account closed.");
                }
            }
            case "6" -> session = null;
            case "0" -> running = false;
            default -> System.out.println("Choose a valid option.");
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return input.nextLine().strip();
    }

    private String password(String prompt) {
        if (System.console() == null) return ask(prompt);
        char[] value = System.console().readPassword("%s", prompt);
        if (value == null) throw new NoSuchElementException();
        try { return new String(value); } finally { Arrays.fill(value, '\0'); }
    }

    private long number(String prompt) {
        try { return Long.parseLong(ask(prompt)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a valid account number."); }
    }
}
