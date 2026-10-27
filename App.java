import java.sql.*;
import java.util.*;

public class App {

    static final String URL =
            "jdbc:oracle:thin:@10.9.64.20:1521/FREEPDB1";

    // Change these to your Oracle username and password
    static final String USER = "system";
    static final String PASSWORD = "padmesh";

    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection con =
                     DriverManager.getConnection(URL, USER, PASSWORD)) {

            System.out.println("Connected to Oracle Database!");

            setupDatabase(con);
            displayProducts(con);
            purchaseProducts(con);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void setupDatabase(Connection con) throws SQLException {
        createTable(con,
            "CREATE TABLE SHOP_PRODUCTS (" +
            "ID NUMBER PRIMARY KEY, " +
            "NAME VARCHAR2(50), " +
            "PRICE NUMBER(10,2), " +
            "STOCK NUMBER)");

        createTable(con,
            "CREATE TABLE SHOP_ORDERS (" +
            "ORDER_ID NUMBER PRIMARY KEY, " +
            "ORDER_TOTAL NUMBER(10,2), " +
            "ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        createTable(con,
            "CREATE TABLE SHOP_ORDER_ITEMS (" +
            "ORDER_ID NUMBER, " +
            "PRODUCT_ID NUMBER, " +
            "QUANTITY NUMBER, " +
            "PRICE NUMBER(10,2), " +
            "FOREIGN KEY (ORDER_ID) REFERENCES SHOP_ORDERS(ORDER_ID), " +
            "FOREIGN KEY (PRODUCT_ID) REFERENCES SHOP_PRODUCTS(ID))");

        try (Statement st = con.createStatement()) {
            st.executeUpdate(
                "CREATE SEQUENCE SHOP_ORDER_SEQ START WITH 1 INCREMENT BY 1");
        } catch (SQLException e) {
            if (e.getErrorCode() != 955) throw e;
        }

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM SHOP_PRODUCTS")) {
            rs.next();

            if (rs.getInt(1) == 0) {
                try (Statement insert = con.createStatement()) {
                    insert.executeUpdate(
                        "INSERT INTO SHOP_PRODUCTS VALUES (1,'Laptop',50000,10)");
                    insert.executeUpdate(
                        "INSERT INTO SHOP_PRODUCTS VALUES (2,'Mouse',500,20)");
                    insert.executeUpdate(
                        "INSERT INTO SHOP_PRODUCTS VALUES (3,'Keyboard',1200,15)");
                    insert.executeUpdate(
                        "INSERT INTO SHOP_PRODUCTS VALUES (4,'Headphones',2000,8)");
                }
            }
        }

        System.out.println("Database tables are ready.");
    }

    static void createTable(Connection con, String sql)
            throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            if (e.getErrorCode() != 955) throw e;
        }
    }

    static void displayProducts(Connection con) throws SQLException {
        System.out.println("\n----- AVAILABLE PRODUCTS -----");
        System.out.printf("%-5s %-15s %-12s %-8s%n",
                "ID", "Product", "Price (Rs.)", "Stock");

        String sql = "SELECT ID, NAME, PRICE, STOCK " +
                     "FROM SHOP_PRODUCTS ORDER BY ID";

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%-5d %-15s %-12.2f %-8d%n",
                        rs.getInt("ID"),
                        rs.getString("NAME"),
                        rs.getDouble("PRICE"),
                        rs.getInt("STOCK"));
            }
        }
    }

    static void purchaseProducts(Connection con) throws SQLException {
        Map<Integer, Integer> cart = new LinkedHashMap<>();

        System.out.println("\nEnter product ID and quantity.");
        System.out.println("Enter product ID 0 to finish selection.");

        while (true) {
            System.out.print("Product ID: ");
            int id = sc.nextInt();

            if (id == 0) break;

            System.out.print("Quantity: ");
            int qty = sc.nextInt();

            if (qty <= 0) {
                System.out.println("Quantity must be greater than zero.");
                continue;
            }

            cart.merge(id, qty, Integer::sum);
        }

        if (cart.isEmpty()) {
            System.out.println("No products selected.");
            return;
        }

        con.setAutoCommit(false);

        try {
            double total = 0;

            // Lock selected products and validate available stock.
            for (Map.Entry<Integer, Integer> item : cart.entrySet()) {
                String sql =
                    "SELECT NAME, PRICE, STOCK FROM SHOP_PRODUCTS " +
                    "WHERE ID = ? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, item.getKey());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Invalid product ID: " + item.getKey());
                        }

                        int qty = item.getValue();
                        int stock = rs.getInt("STOCK");
                        double price = rs.getDouble("PRICE");

                        if (qty > stock) {
                            throw new SQLException(
                                "Insufficient stock for " + rs.getString("NAME"));
                        }

                        total += price * qty;
                    }
                }
            }

            // Create the order inside the same transaction.
            int orderId;

            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT SHOP_ORDER_SEQ.NEXTVAL FROM DUAL")) {
                rs.next();
                orderId = rs.getInt(1);
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO SHOP_ORDERS (ORDER_ID, ORDER_TOTAL) " +
                    "VALUES (?, ?)")) {
                ps.setInt(1, orderId);
                ps.setDouble(2, total);
                ps.executeUpdate();
            }

            for (Map.Entry<Integer, Integer> item : cart.entrySet()) {
                int productId = item.getKey();
                int qty = item.getValue();
                double price;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT PRICE FROM SHOP_PRODUCTS WHERE ID = ?")) {
                    ps.setInt(1, productId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        price = rs.getDouble(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO SHOP_ORDER_ITEMS " +
                        "(ORDER_ID, PRODUCT_ID, QUANTITY, PRICE) " +
                        "VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, productId);
                    ps.setInt(3, qty);
                    ps.setDouble(4, price);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE SHOP_PRODUCTS SET STOCK = STOCK - ? " +
                        "WHERE ID = ? AND STOCK >= ?")) {
                    ps.setInt(1, qty);
                    ps.setInt(2, productId);
                    ps.setInt(3, qty);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Stock update failed.");
                    }
                }
            }

            con.commit();

            System.out.println("\n----- ORDER SUCCESSFUL -----");
            System.out.println("Order ID: " + orderId);
            System.out.printf("Total Amount: Rs. %.2f%n", total);
            System.out.println("Order saved and stock updated.");

        } catch (Exception e) {
            con.rollback();
            System.out.println("Order failed. Transaction rolled back.");
            System.out.println("Reason: " + e.getMessage());

        } finally {
            con.setAutoCommit(true);
        }

        displayProducts(con);
    }
}