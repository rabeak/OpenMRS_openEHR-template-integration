package mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySqlAccess {
	
	  private Connection conn = null;
	
	public Connection getDatabaseConnection() throws Exception, SQLException {	
		try {
			// loading MySql Driver
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (Exception ex) {
			System.out.println("SQLException: MySql-Driver couln't be loaded: "+ex.getMessage());
		}
	
		try {
			conn = DriverManager.getConnection("jdbc:mysql://localhost/openmrs?user=root&password=test");
		} catch (SQLException e) {
			System.out.println("SQLException: " + e.getMessage());
			System.out.println("SQLState: " + e.getSQLState());
			System.out.println("VendorError: " + e.getErrorCode());
		}
		return conn;		
	}
}
