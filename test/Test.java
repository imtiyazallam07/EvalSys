import java.sql.*;
 
/**
 * This program demonstrates how to use UCanAccess JDBC driver to read/write
 * a Microsoft Access database.
 * @author www.codejava.net
 *
 */
public class Test {
 
    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println("1");
        String databaseURL = "jdbc:ucanaccess://d:/Programs/Computer Based Test/Test.accdb";
            //Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
         
        try (Connection connection = DriverManager.getConnection(databaseURL)) {
            
            String sql = "SELECT * FROM Classroom";
             
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql);
            while (result.next()) {
                int roll = result.getInt("Roll");
                String fullname = result.getString("Nam");
                String cls = result.getString("Class");
                String sec = result.getString("Section");
                 
                System.out.println(roll + ", " + fullname + ", " + cls + ", " + sec);
            }
             
        } catch (SQLException ex) {
            System.out.println(ex);
        }
    }
}