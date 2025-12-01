import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:derby:CarWashDB;create=true";
    private Connection conn;
    
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());

    public DatabaseManager() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // تحميل درايفر Derby
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            conn = DriverManager.getConnection(DB_URL);
            createTables();
            logger.info("تم الاتصال بقاعدة البيانات بنجاح");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "لم يتم العثور على درايفر Derby", e);
            JOptionPane.showMessageDialog(null, "خطأ: لم يتم العثور على درايفر Derby. تأكد من إضافة derby.jar إلى المشروع.", "خطأ", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "خطأ في الاتصال بقاعدة البيانات", e);
            JOptionPane.showMessageDialog(null, "خطأ في الاتصال بقاعدة البيانات: " + e.getMessage(), "خطأ", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createTables() {
        // إنشاء جدول العملاء
        String createCustomersTable = 
            "CREATE TABLE customers (" +
            "id INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
            "name VARCHAR(100) NOT NULL, " +
            "phone VARCHAR(20) NOT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        // إنشاء جدول الخدمات
        String createServicesTable = 
            "CREATE TABLE services (" +
            "id INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
            "service_name VARCHAR(100) NOT NULL, " +
            "price DECIMAL(10,2), " +
            "duration_minutes INTEGER" +
            ")";

        // إنشاء جدول الحجوزات
        String createBookingsTable = 
            "CREATE TABLE bookings (" +
            "id INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
            "customer_id INTEGER, " +
            "service_id INTEGER, " +
            "booking_date VARCHAR(50) NOT NULL, " +
            "booking_time VARCHAR(50) NOT NULL, " +
            "status VARCHAR(20) DEFAULT 'معلق', " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (customer_id) REFERENCES customers(id), " +
            "FOREIGN KEY (service_id) REFERENCES services(id)" +
            ")";

        try (Statement stmt = conn.createStatement()) {
            
            // إنشاء جدول العملاء
            try {
                stmt.execute(createCustomersTable);
                logger.info("تم إنشاء جدول العملاء");
            } catch (SQLException e) {
                if (e.getSQLState().equals("X0Y32")) {
                    logger.info("جدول العملاء موجود بالفعل");
                } else {
                    throw e;
                }
            }

            // إنشاء جدول الخدمات
            try {
                stmt.execute(createServicesTable);
                logger.info("تم إنشاء جدول الخدمات");
                
                // إضافة البيانات الأساسية للخدمات
                insertInitialServices();
                
            } catch (SQLException e) {
                if (e.getSQLState().equals("X0Y32")) {
                    logger.info("جدول الخدمات موجود بالفعل");
                } else {
                    throw e;
                }
            }

            // إنشاء جدول الحجوزات
            try {
                stmt.execute(createBookingsTable);
                logger.info("تم إنشاء جدول الحجوزات");
            } catch (SQLException e) {
                if (e.getSQLState().equals("X0Y32")) {
                    logger.info("جدول الحجوزات موجود بالفعل");
                } else {
                    throw e;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "خطأ في إنشاء الجداول: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(null, "خطأ في إنشاء الجداول: " + e.getMessage(), "خطأ في قاعدة البيانات", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertInitialServices() {
        String[] services = {
            "INSERT INTO services (service_name, price, duration_minutes) VALUES ('غسيل خارجي', 50.00, 30)",
            "INSERT INTO services (service_name, price, duration_minutes) VALUES ('غسيل داخلي', 70.00, 45)", 
            "INSERT INTO services (service_name, price, duration_minutes) VALUES ('غسيل شامل', 100.00, 60)"
        };

        try (Statement stmt = conn.createStatement()) {
            // التحقق مما إذا كانت الخدمات موجودة مسبقاً
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM services");
            if (rs.next() && rs.getInt(1) == 0) {
                for (String service : services) {
                    stmt.execute(service);
                }
                logger.info("تم إضافة البيانات الأساسية للخدمات");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "خطأ في إضافة البيانات الأساسية للخدمات: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("تم إغلاق الاتصال بقاعدة البيانات");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "خطأ في إغلاق الاتصال", e);
        }
    }

    // إضافة عميل جديد
    public int addCustomer(String name, String phone) throws SQLException {
        // أولاً التحقق إذا كان العميل موجوداً بالفعل
        int existingCustomerId = findCustomerIdByPhone(phone);
        if (existingCustomerId != -1) {
            return existingCustomerId;
        }

        String sql = "INSERT INTO customers (name, phone) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, phone);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return -1;
        }
    }

    // البحث عن عميل برقم الهاتف
    public int findCustomerIdByPhone(String phone) throws SQLException {
        String sql = "SELECT id FROM customers WHERE phone = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phone);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            return -1;
        }
    }

    // البحث عن خدمة بالاسم
    public int findServiceIdByName(String serviceName) throws SQLException {
        String sql = "SELECT id FROM services WHERE service_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serviceName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            return -1;
        }
    }

    public void checkAllData() {
    try {
        System.out.println("=== التحقق من جميع البيانات ===");
        
        // عرض العملاء
        System.out.println("--- العملاء ---");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM customers")) {
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + 
                                 " | الاسم: " + rs.getString("name") + 
                                 " | الهاتف: " + rs.getString("phone"));
            }
        }
        
        // عرض الحجوزات
        System.out.println("--- الحجوزات ---");
        String sql = "SELECT b.id, c.name, c.phone, s.service_name, b.booking_date, b.booking_time " +
                    "FROM bookings b " +
                    "JOIN customers c ON b.customer_id = c.id " +
                    "JOIN services s ON b.service_id = s.id";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("حجز #" + rs.getInt("id") + 
                                 " | العميل: " + rs.getString("name") +
                                 " | الهاتف: " + rs.getString("phone") +
                                 " | الخدمة: " + rs.getString("service_name") +
                                 " | التاريخ: " + rs.getString("booking_date") +
                                 " | الوقت: " + rs.getString("booking_time"));
            }
        }
        
    } catch (SQLException e) {
        System.out.println("✗ خطأ في عرض البيانات: " + e.getMessage());
    }
}
    // إضافة حجز جديد
    public boolean addBooking(int customerId, int serviceId, String date, String time) throws SQLException {
        String sql = "INSERT INTO bookings (customer_id, service_id, booking_date, booking_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, customerId);
            pstmt.setInt(2, serviceId);
            pstmt.setString(3, date);
            pstmt.setString(4, time);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        }
    }

    // حذف حجز
    public boolean deleteBooking(String customerName, String phone, String serviceType) throws SQLException {
        String sql = "DELETE FROM bookings WHERE id IN (" +
                    "SELECT b.id FROM bookings b " +
                    "JOIN customers c ON b.customer_id = c.id " +
                    "JOIN services s ON b.service_id = s.id " +
                    "WHERE c.name = ? AND c.phone = ? AND s.service_name = ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerName);
            pstmt.setString(2, phone);
            pstmt.setString(3, serviceType);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        }
    }

    // التحقق من وجود حجوزات قبل الحذف
    public boolean bookingExists(String customerName, String phone, String serviceType) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM bookings b " +
                    "JOIN customers c ON b.customer_id = c.id " +
                    "JOIN services s ON b.service_id = s.id " +
                    "WHERE c.name = ? AND c.phone = ? AND s.service_name = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerName);
            pstmt.setString(2, phone);
            pstmt.setString(3, serviceType);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
            return false;
        }
    }
}