package bank;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Bank implements AutoCloseable {
    public record Profile(String name, String birthDate, String gender, String address,
                          String phone, String email, String identification) {}
    public record Account(long number, Profile profile, BigDecimal balance, boolean closed) {}
    public record Entry(long number, String time, long sender, long receiver,
                        BigDecimal amount, String remarks) {}

    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private final BigDecimal openingBalance;
    private Properties data = new Properties();

    public Bank(Path directory, BigDecimal openingBalance) throws IOException {
        this.openingBalance = money(openingBalance.toPlainString());
        if (this.openingBalance.signum() < 0) throw new IllegalArgumentException("Negative opening balance.");
        Files.createDirectories(directory);
        file = directory.resolve("bank.properties");
        channel = FileChannel.open(directory.resolve("bank.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = channel.tryLock();
        } catch (OverlappingFileLockException | IOException e) {
            channel.close();
            throw new IOException("Data directory is already in use.", e);
        }
        if (acquired == null) {
            channel.close();
            throw new IOException("Data directory is already in use.");
        }
        lock = acquired;
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) { data.load(reader); }
            } else {
                data.setProperty("nextAccount", "1000000");
                data.setProperty("nextTransaction", "1");
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    public static BigDecimal money(String value) {
        try {
            return new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Use a number with at most two decimal places.");
        }
    }

    public long create(Profile profile, String password) throws IOException {
        for (String value : List.of(profile.name(), profile.birthDate(), profile.gender(), profile.address(),
                profile.phone(), profile.email(), profile.identification())) {
            if (value.isBlank()) throw new IllegalArgumentException("All account fields are required.");
        }
        try {
            if (!LocalDate.parse(profile.birthDate()).isBefore(LocalDate.now()))
                throw new IllegalArgumentException("Birth date must be in the past.");
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Birth date must be YYYY-MM-DD.");
        }
        if (password.length() < 8) throw new IllegalArgumentException("Password needs at least 8 characters.");
        Properties next = copy();
        long number = Long.parseLong(data.getProperty("nextAccount"));
        String key = "account." + number + ".";
        String[] fields = {"name", "birthDate", "gender", "address", "phone", "email", "identification"};
        String[] values = {profile.name(), profile.birthDate(), profile.gender(), profile.address(),
                profile.phone(), profile.email(), profile.identification()};
        for (int i = 0; i < fields.length; i++) next.setProperty(key + fields[i], values[i]);
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        next.setProperty(key + "salt", Base64.getEncoder().encodeToString(salt));
        next.setProperty(key + "password", Base64.getEncoder().encodeToString(hash(password, salt)));
        next.setProperty(key + "balance", openingBalance.toPlainString());
        next.setProperty(key + "closed", "false");
        next.setProperty("nextAccount", Long.toString(Math.addExact(number, 1)));
        commit(next);
        return number;
    }

    public boolean login(long number, String password) {
        String key = "account." + number + ".";
        if (!data.containsKey(key + "name") || Boolean.parseBoolean(data.getProperty(key + "closed"))) return false;
        return MessageDigest.isEqual(Base64.getDecoder().decode(data.getProperty(key + "password")),
                hash(password, Base64.getDecoder().decode(data.getProperty(key + "salt"))));
    }

    public Account account(long number) {
        String key = "account." + number + ".";
        if (!data.containsKey(key + "name")) throw new IllegalArgumentException("Account does not exist.");
        return new Account(number, new Profile(data.getProperty(key + "name"), data.getProperty(key + "birthDate"),
                data.getProperty(key + "gender"), data.getProperty(key + "address"), data.getProperty(key + "phone"),
                data.getProperty(key + "email"), data.getProperty(key + "identification")),
                money(data.getProperty(key + "balance")), Boolean.parseBoolean(data.getProperty(key + "closed")));
    }

    private Account active(long number) {
        Account account = account(number);
        if (account.closed()) throw new IllegalArgumentException("Account is closed.");
        return account;
    }

    public void transfer(long sender, long receiver, BigDecimal amount, String remarks) throws IOException {
        amount = money(amount.toPlainString());
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive.");
        if (sender == receiver) throw new IllegalArgumentException("Choose a different recipient.");
        Account from = active(sender);
        Account to = active(receiver);
        if (from.balance().compareTo(amount) < 0) throw new IllegalArgumentException("Insufficient balance.");
        if (remarks.length() > 200) throw new IllegalArgumentException("Remarks must be 200 characters or fewer.");
        Properties next = copy();
        next.setProperty("account." + sender + ".balance", from.balance().subtract(amount).toPlainString());
        next.setProperty("account." + receiver + ".balance", to.balance().add(amount).toPlainString());
        long id = Long.parseLong(data.getProperty("nextTransaction"));
        String key = "transaction." + id + ".";
        next.setProperty(key + "time", Instant.now().toString());
        next.setProperty(key + "sender", Long.toString(sender));
        next.setProperty(key + "receiver", Long.toString(receiver));
        next.setProperty(key + "amount", amount.toPlainString());
        next.setProperty(key + "remarks", remarks);
        next.setProperty("nextTransaction", Long.toString(Math.addExact(id, 1)));
        commit(next);
    }

    public List<Entry> statement(long number) {
        account(number);
        List<Entry> entries = new ArrayList<>();
        long end = Long.parseLong(data.getProperty("nextTransaction"));
        for (long id = 1; id < end; id++) {
            String key = "transaction." + id + ".";
            long sender = Long.parseLong(data.getProperty(key + "sender"));
            long receiver = Long.parseLong(data.getProperty(key + "receiver"));
            if (sender == number || receiver == number) entries.add(new Entry(id, data.getProperty(key + "time"),
                    sender, receiver, money(data.getProperty(key + "amount")), data.getProperty(key + "remarks")));
        }
        return List.copyOf(entries);
    }

    public void closeAccount(long number) throws IOException {
        if (active(number).balance().signum() != 0)
            throw new IllegalArgumentException("Transfer the remaining balance before closing.");
        Properties next = copy();
        next.setProperty("account." + number + ".closed", "true");
        commit(next);
    }

    private Properties copy() {
        Properties next = new Properties();
        next.putAll(data);
        return next;
    }

    private void commit(Properties next) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "bank-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { next.store(writer, "Bank data v1"); }
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) { output.force(true); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] hash(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable.", e);
        } finally {
            spec.clearPassword();
        }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
