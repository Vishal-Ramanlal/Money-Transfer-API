# Money Transfer API

This project is a Spring Boot application that provides an API for transferring money between accounts.

## Features

- Create and manage accounts with different currencies.
- Transfer money between accounts.
- Currency conversion between USD and AUD (rate: 0.50 USD to 1 AUD).
- 1% transaction fee applied to the initiator of the transfer.
- Concurrent transaction handling using optimistic locking.

## Technologies Used

- Java 17
- Spring Boot 3
- Spring Data JPA
- H2 Database (in-memory)
- Maven
- Lombok

## Prerequisites

- JDK 17 or later
- Maven 3.2+

## How to Run


1.  **Build the project using Maven:**
    ```bash
    mvn clean install
    ```
2.  **Run the application:**
    ```bash
    mvn spring-boot:run
    ```
    Alternatively, you can run the packaged JAR file:
    ```bash
    java -jar target/demo-0.0.1-SNAPSHOT.jar
    ```

## API Endpoints

-   **GET /api/accounts/{id}**
    -   Retrieves account details for the given ID.
    -   Example: `curl http://localhost:8080/api/accounts/1`

-   **POST /api/accounts/transfer**
    -   Transfers money between accounts.
    -   Request Body (JSON):
        ```json
        {
          "fromAccountId": 1,
          "toAccountId": 2,
          "amount": 50,
          "currency": "USD"
        }
        ```
    -   Example: 
        ```bash
        curl -X POST -H "Content-Type: application/json" -d '{"fromAccountId":1,"toAccountId":2,"amount":50,"currency":"USD"}' http://localhost:8080/api/accounts/transfer
        ```

## H2 Console

-   The H2 database console is available at `http://localhost:8080/h2-console` when the application is running.
-   JDBC URL: `jdbc:h2:mem:testdb`
-   User Name: `sa`
-   Password: (leave blank)

## Initial Data

The application is pre-populated with two accounts (see `src/main/resources/data.sql`):

-   **Account 1 (Alice):**
    -   ID: 1
    -   Name: Alice
    -   Initial Amount: 1000
    -   Currency: USD
-   **Account 2 (Bob):**
    -   ID: 2
    -   Name: Bob
    -   Initial Amount: 500
    -   Currency: JPN

## Assumptions Made

1.  **FX Rates:** 
    - The following FX rates are assumed and hardcoded in the service:
        - 1 USD = 2.00 AUD (meaning 1 AUD = 0.50 USD)
        - 1 USD = 110.00 JPN
        - 1 USD = 7.00 CNY
    - Conversions between any of these currencies (USD, AUD, JPN, CNY) are supported, using USD as an intermediary base for cross-currency conversions (e.g., AUD to JPN goes via AUD -> USD -> JPN).
    - Transfers involving currencies not in this set are not supported.
2.  **Transaction Fee:** A flat 1% transaction fee is charged to the initiator (sender) of the transfer. The fee is calculated on the transfer amount in the sender's currency.
3.  **Currency for Transfer:** Money can only be transferred *from* an account in its base currency. The `currency` field in the transfer request must match the `fromAccount`'s base currency.
4.  **Currency for Receiving:** Money can only be transferred *to* an account in its base currency. If a currency conversion occurs (e.g., USD to AUD), the final amount must be in the `toAccount`'s base currency (AUD in this case).
5.  **Concurrency Handling:** Optimistic locking (`@Version` annotation on the `Account` entity) is used to handle concurrent transactions. If a concurrent update is detected, Spring will throw an `ObjectOptimisticLockingFailureException`.
6.  **Error Handling:** Specific exceptions are thrown for scenarios like insufficient funds, account not found, and invalid currency operations. A global exception handler returns appropriate HTTP status codes.
7.  **Database:** An in-memory H2 database is used. Data will be lost when the application stops.
8.  **CNY and JPN Transfers:** The problem statement implies transfers involving CNY and JPN. With the new assumed FX rates, these are now supported based on the defined rates against USD. The service will attempt to convert to the target account's base currency.

## Proposed Enhancements / Future Considerations

1.  **Expanded FX Service:** 
    -   Implement a more robust FX service that can fetch rates from an external API or a configurable internal source.
    -   Support a wider range of currency conversions.
    -   Handle FX rate markups or different rates for buying/selling.
2.  **Transaction Retry Mechanism:** For `ObjectOptimisticLockingFailureException`, implement a retry mechanism with a backoff strategy for a better user experience in high-concurrency scenarios.
3.  **Transaction History/Ledger:** Add a separate entity/table to store a detailed history of all transactions for auditing and reporting purposes.
4.  **Asynchronous Processing:** For transfers, especially those involving external FX services, consider asynchronous processing using message queues (e.g., Kafka, RabbitMQ) to improve responsiveness and resilience.
5.  **User Authentication & Authorization:** Implement security measures to ensure only authorized users can access account information and perform transfers.
6.  **More Sophisticated Fee Structure:** Allow for more complex fee calculations (e.g., tiered fees, different fees for different currency pairs or transfer amounts).
7.  **Input Validation:** Add more comprehensive validation for request DTOs (e.g., positive amounts, valid account IDs format) using Bean Validation (`@Valid`, `@NotNull`, etc.).
8.  **Internationalization (i18n):** For error messages and API responses if the application needs to support multiple languages.
9.  **Persistent Database:** Switch from H2 in-memory to a persistent database like PostgreSQL, MySQL, or a cloud-based DB for production use.
10. **Monitoring and Logging:** Enhance logging with structured logging and integrate monitoring tools (e.g., Prometheus, Grafana) for production environments.
11. **API Versioning:** Implement a strategy for API versioning if breaking changes are anticipated in the future.
12. **CNY and JPN Transfers:** The problem statement implies transfers involving CNY and JPN. With the new assumed FX rates, these are now supported based on the defined rates against USD. The service will attempt to convert to the target account's base currency.

## Running Tests

Unit tests for the service layer can be found in `src/test/java/com/jpmorgan/demo/service/AccountServiceTest.java`.
To run tests via Maven:
```bash
mvn test
``` 


## Use of AI

In order to complete this project, AI was used in only as a consultating agent. Which means the setup instructions, readme instructions, and tests instructions were created with the help of AI. 