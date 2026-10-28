# Bank Management System

A Java console application for creating bank accounts, authenticating users, checking balances, transferring funds, viewing statements, and closing accounts.

The application stores data locally and preserves it between runs. It requires no database server, frameworks, or external Java dependencies.

## Features

- Create an account with personal and contact details.
- Log in using an account number and password.
- View account details and current balance.
- Transfer funds between active accounts.
- View transaction statements with timestamps, amounts, counterparties, and remarks.
- Close accounts after transferring the remaining balance.
- Preserve balances, credentials, and transaction history across restarts.

## Requirements

- JDK 17 or newer, including `java` and `javac`.
- A local filesystem that supports file locking and atomic file replacement.
- A POSIX shell for the provided script, such as Bash, Git Bash, or WSL.

On Ubuntu or Debian:

```sh
sudo apt-get update
sudo apt-get install openjdk-17-jdk-headless
```

Check the installation:

```sh
java -version
javac -version
```

## Getting Started

From the project directory:

```sh
chmod +x bank.sh
./bank.sh
```

The script compiles the source files and starts the application.

To compile and run manually:

```sh
mkdir -p build
javac --release 17 -d build src/bank/*.java test/bank/*.java
java -cp build bank.Main
```

## Application Workflow

### Create an Account

Select **2 Create account** and provide:

- Full name
- Date of birth in `YYYY-MM-DD` format
- Gender
- Address
- Phone number
- Email address
- Identification number
- Password containing at least eight characters

All fields are required, and the birth date must be in the past.

The application assigns an account number and signs you in automatically. Save the account number for future logins.

New accounts receive a configurable simulated opening balance, which defaults to `69.00`.

### Log In

Select **1 Login** and enter your account number and password.

Unknown accounts, incorrect passwords, and closed accounts cannot log in.

Passwords are hidden when running in a supported terminal. IDE consoles and redirected input may display password input.

### Account Menu

| Option | Action |
|---|---|
| 1 | View balance |
| 2 | View account details |
| 3 | Transfer funds |
| 4 | View statement |
| 5 | Close account |
| 6 | Log out |
| 0 | Exit |

### Transfer Funds

Enter the recipient’s account number, transfer amount, and optional remarks.

A transfer requires:

- Two different, active accounts.
- A positive amount with no more than two decimal places.
- Sufficient funds in the sender’s account.
- Remarks of no more than 200 characters.

Successful transfers update both balances and create one shared transaction record. The sender sees a debit, and the recipient sees a credit.

### View Statements

Statements display:

- Transaction number
- UTC timestamp
- Debit or credit direction
- Amount
- Counterparty account number
- Remarks

The simulated opening balance is not listed as a transfer.

### Close an Account

An account must have a zero balance before it can be closed. Transfer any remaining funds to another active account, then select **5 Close account** and type `CLOSE`.

Closed accounts cannot log in, send funds, or receive transfers. Their records remain stored, and account numbers are never reused.

## Configuration

The default configuration is located at:

```text
config/bank.properties
```

```properties
data.dir=../data
opening.balance=69.00
```

| Setting | Description |
|---|---|
| `data.dir` | Storage directory, resolved relative to the configuration file. Absolute paths are supported. |
| `opening.balance` | Simulated balance assigned to newly created accounts. Existing balances are unaffected. |

Set `opening.balance=0.00` to disable opening grants.

Use a custom configuration file:

```sh
./bank.sh run /absolute/path/to/custom.properties
```

The application uses one unnamed currency and performs no currency conversion.

## Data Storage

The application automatically creates:

```text
data/
├── bank.properties
└── bank.lock
```

`bank.properties` stores account details, salted password hashes, balances, and transaction records.

`bank.lock` prevents multiple application processes from opening the same data directory simultaneously.

Each change is written to a temporary file and committed through atomic file replacement. Both sides of a transfer and its transaction record are saved together. If saving fails, the in-memory change is not committed.

To back up data, stop the application and copy `bank.properties`. Restore backups while the application is stopped.

## Simulation

Run the predefined scenario:

```sh
./bank.sh simulate
```

The simulation:

1. Creates Alice and Bob with `69.00` each.
2. Authenticates Alice.
3. Transfers `20.50` from Alice to Bob.
4. Rejects an overdraft attempt.
5. Transfers Alice’s remaining `48.50` to Bob.
6. Closes Alice’s account.
7. Reopens storage and verifies that Bob has `138.00` and Alice remains closed.

The simulation uses temporary storage, removes it afterward, and leaves normal application data untouched.

## Tests

Run:

```sh
./bank.sh test
```

The test suite covers authentication, transfer validation, balance calculations, statements, account closure, exclusive locking, failed-save behavior, text persistence, and reopening saved data.

Tests use temporary storage. No external test framework is required.

## Project Structure

```text
bank-management/
├── config/
│   └── bank.properties
├── src/bank/
│   ├── Bank.java
│   ├── Main.java
│   └── Simulation.java
├── test/bank/
│   └── BankTest.java
├── bank.sh
├── README.md
└── LICENSE.txt
```

| File | Responsibility |
|---|---|
| `Bank.java` | Account operations, validation, password hashing, and persistence |
| `Main.java` | Console menus and user input |
| `Simulation.java` | Automated demonstration |
| `BankTest.java` | Functional and persistence checks |
| `bank.sh` | Compilation and execution |

## Development Workflow

1. Update business rules or storage behavior in `Bank.java`.
2. Update console interactions in `Main.java`.
3. Adjust the simulation or tests when behavior changes.
4. Run verification:

```sh
./bank.sh test
./bank.sh simulate
./bank.sh
```

## Tradeoffs and Limitations

- **Simple storage:** Every change rewrites the complete data file, and statements scan transaction history. This suits small local datasets.
- **Single-process access:** Only one process can use a data directory at a time. The service is not thread safe.
- **Password protection:** Passwords use salted PBKDF2-HMAC-SHA256 with 210,000 iterations. Personal details remain plaintext.
- **Limited security controls:** Login throttling, password recovery, identity verification, and tamper-proof audit logs are not implemented.
- **Demo funding:** Opening balances create simulated money whenever accounts are created. Deposits and withdrawals are not supported.
- **Basic durability:** Atomic replacement prevents partial application-level saves, but there is no database recovery system or guaranteed power-loss durability.
- **Trusted local data:** Corruption repair, schema migrations, and protection against manual file modification are not implemented.

This application is intended for educational use with simulated funds.

## License

MIT. See `LICENSE.txt`.
