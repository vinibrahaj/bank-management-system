package bank;

import java.io.IOException;
import java.nio.file.*;

public final class BankTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("bank-test-");
        try {
            long alice;
            long bob;
            try (Bank bank = new Bank(directory, Bank.money("69.00"))) {
                alice = bank.create(Simulation.profile("Alice Maria Demo"), "password-alice");
                bob = bank.create(Simulation.profile("Bob Demo"), "password-bob");
                check(alice != bob, "Unique account numbers");
                reject(() -> bank.create(Simulation.profile("Short Password"), "short"));
                reject(() -> bank.create(new Bank.Profile("Demo", "invalid", "X", "Street", "Phone", "Email", "ID"), "password-demo"));
                check(bank.login(alice, "password-alice"), "Correct login");
                check(!bank.login(alice, "wrong") && !bank.login(999, "password-alice"), "Invalid login");
                check(bank.account(alice).profile().address().equals("12 Example Street, Tirana"), "Multiword fields");
                reject(() -> bank.transfer(alice, bob, Bank.money("-1"), ""));
                reject(() -> bank.transfer(alice, bob, Bank.money("0"), ""));
                reject(() -> bank.transfer(alice, bob, Bank.money("70"), ""));
                reject(() -> bank.transfer(alice, alice, Bank.money("1"), ""));
                reject(() -> bank.transfer(alice, 999, Bank.money("1"), ""));
                reject(() -> bank.transfer(alice, bob, Bank.money("1.001"), ""));
                reject(() -> bank.transfer(alice, bob, Bank.money("1"), "x".repeat(201)));
                check(bank.account(alice).balance().equals(Bank.money("69")) && bank.statement(alice).isEmpty(),
                        "Rejected transfers have no effects");
                reject(() -> bank.closeAccount(alice));
                bank.transfer(alice, bob, Bank.money("20.50"), "Spaces, equals= and unicode ë");
                Path saved = directory.resolve("bank.properties");
                Path backup = directory.resolve("backup.properties");
                Files.move(saved, backup);
                Files.createDirectory(saved);
                try {
                    try {
                        bank.transfer(alice, bob, Bank.money("1"), "Must fail to save");
                        throw new AssertionError("Failed save was accepted");
                    } catch (IOException expected) { checks++; }
                    check(bank.statement(alice).size() == 1, "Failed save leaves history unchanged");
                } finally {
                    Files.delete(saved);
                    Files.move(backup, saved);
                }
                check(bank.account(alice).balance().equals(Bank.money("48.50")), "Debit and failed-save rollback");
                check(bank.account(bob).balance().equals(Bank.money("89.50")), "Credit");
                check(bank.statement(alice).equals(bank.statement(bob)), "Shared statement entry");
                try (Bank ignored = new Bank(directory, Bank.money("0"))) {
                    throw new AssertionError("Concurrent store accepted");
                } catch (IOException expected) { checks++; }
                bank.transfer(alice, bob, Bank.money("48.50"), "Final transfer");
                bank.closeAccount(alice);
                check(!bank.login(alice, "password-alice"), "Closed login rejected");
                reject(() -> bank.transfer(bob, alice, Bank.money("1"), ""));
                long next = bank.create(Simulation.profile("Carol Demo"), "password-carol");
                check(next > bob, "Closed account numbers never reused");
            }
            try (Bank bank = new Bank(directory, Bank.money("0"))) {
                check(bank.account(bob).balance().equals(Bank.money("138.00")), "Balance persists");
                check(bank.account(alice).closed(), "Closure persists");
                check(bank.statement(bob).size() == 2, "History persists");
                check(bank.statement(bob).get(0).remarks().endsWith("unicode ë"), "Text round trip");
                check(bank.login(bob, "password-bob"), "Password hash persists");
            }
            check(!Files.readString(directory.resolve("bank.properties")).contains("password-bob"), "No plaintext password");
            System.out.println("Passed " + checks + " checks.");
        } finally { Simulation.removeDirectory(directory); }
    }

    private interface Action { void run() throws Exception; }

    private static void reject(Action action) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected validation failure");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
