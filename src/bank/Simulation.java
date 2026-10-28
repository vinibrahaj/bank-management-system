package bank;

import java.nio.file.*;
import java.util.Comparator;

public final class Simulation {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("bank-simulation-");
        try {
            long alice;
            long bob;
            try (Bank bank = new Bank(directory, Bank.money("69.00"))) {
                alice = bank.create(profile("Alice Demo"), "alice-demo-123");
                bob = bank.create(profile("Bob Demo"), "bob-demo-123");
                System.out.println("Created Alice " + alice + " and Bob " + bob + "; each starts with 69.00.");
                if (!bank.login(alice, "alice-demo-123")) throw new AssertionError("Login failed");
                bank.transfer(alice, bob, Bank.money("20.50"), "Lunch repayment");
                System.out.println("Alice sent Bob 20.50: Alice=" + bank.account(alice).balance()
                        + ", Bob=" + bank.account(bob).balance());
                try {
                    bank.transfer(alice, bob, Bank.money("100.00"), "Too much");
                    throw new AssertionError("Overdraft accepted");
                } catch (IllegalArgumentException e) { System.out.println("Rejected: " + e.getMessage()); }
                bank.transfer(alice, bob, bank.account(alice).balance(), "Remaining funds");
                bank.closeAccount(alice);
                System.out.println("Alice transferred her remaining 48.50 and closed her account.");
            }
            try (Bank bank = new Bank(directory, Bank.money("69.00"))) {
                if (!bank.account(alice).closed() || bank.account(bob).balance().compareTo(Bank.money("138.00")) != 0)
                    throw new AssertionError("Persistence failed");
                System.out.println("Reopened storage: Bob=" + bank.account(bob).balance() + "; Alice closed=true.");
                for (var entry : bank.statement(bob)) System.out.println("CREDIT " + entry.amount() + " | " + entry.remarks());
                System.out.println("Simulation passed. Temporary data removed on exit.");
            }
        } finally { removeDirectory(directory); }
    }

    static Bank.Profile profile(String name) {
        return new Bank.Profile(name, "1995-01-15", "Unspecified", "12 Example Street, Tirana",
                "+355000000000", "demo@example.test", "DEMO-001");
    }

    static void removeDirectory(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
