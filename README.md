# java-jdbc-shopping-application-769
Java JDBC Shopping Application

Objective

Develop a basic shopping application using Java and Oracle Database through JDBC.

Features

- Display products and prices.
- Select products and quantities.
- Calculate the order total.
- Save orders and order items in Oracle Database.
- Reduce product stock using database transactions.
- Roll back changes if an order fails.

Technologies Used

- Java
- Oracle Database Free
- JDBC
- SQL

Database Connection

The application uses the Oracle service "FREEPDB1".

Example connection URL:

"jdbc:oracle:thin:@localhost:1521/FREEPDB1"

Update the connection URL, username, and password according to your local Oracle installation. Do not publish database credentials.

How to Run

1. Install Java and Oracle Database.

2. Obtain the Oracle JDBC driver JAR.

3. Configure the database credentials in "App.java".

4. Open a terminal in the project folder.

5. Compile:
   
   "javac App.java"

6. Run:
   
   "java -cp ".;ojdbc17.jar" App"

Transaction Management

The application uses "commit()" to save successful orders and "rollback()" to undo changes when an order fails.

Output

See the uploaded screenshot of the successful execution.
