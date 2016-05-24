import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;

public class WageCalculator {
	public static void main(String[] args) {
		Employee.loadEmployeeFromLog("1.working_time.log");
		System.out.println("=== GROUP BY Date ===");
		for (Calendar c : Timestamp.getAllDates()) {
			System.out.println("  >> " + Timestamp.getFormatedDate(c) + " <<");
			double totalHours = 0.0;
			double totalWage = 0.0;
			for (Timestamp ts : Timestamp.getTimestampsOf(c)) {
				System.out
						.println("    Name: " + ts.employee.name + " | Work: " + String.format("%.2f", ts.getNumHours())
								+ " hours | Wage: " + String.format("%.2f", ts.getWage()) + " Baht");
				totalHours += ts.getNumHours();
				totalWage += ts.getWage();
			}
			System.out.println("    (Total hours: " + String.format("%.2f", totalHours) + " hours | Total Wage: "
					+ String.format("%.2f", totalWage) + " Baht)");
		}
		System.out.println();
		System.out.println("=== GROUP BY Employee ===");
		for (Employee e : Employee.getAllEmployees()) {
			System.out.println("  >> " + e.name + " <<");
			double totalHours = 0.0;
			double totalWage = 0.0;
			for (Timestamp ts : e.getTimestamps()) {
				System.out.println("    Date: " + Timestamp.getFormatedDate(ts.getStart()) + " | Work: "
						+ String.format("%.2f", ts.getNumHours()) + " hours | Wage: "
						+ String.format("%.2f", ts.getWage()) + " Baht");
				totalHours += ts.getNumHours();
				totalWage += ts.getWage();
			}
			System.out.println("    (Total hours: " + String.format("%.2f", totalHours) + " hours | Total Wage: "
					+ String.format("%.2f", totalWage) + " Baht)");
		}
	}
}

class Employee {

	private static HashMap<String, Employee> employees = new HashMap<String, Employee>();
	protected String name;
	private TreeSet<Timestamp> timestamps = new TreeSet<Timestamp>();

	private Employee(String name) {
		this.name = name;
	}

	protected static Employee getOrCreateEmployee(String name) {
		if (employees.containsKey(name)) {
			return employees.get(name);
		}
		Employee emp = new Employee(name);
		employees.put(name, emp);
		return emp;
	}

	protected static Employee[] getAllEmployees() {
		return employees.values().toArray(new Employee[0]);
	}

	public static void loadEmployeeFromLog(String logFile) {
		try {
			Scanner s = new Scanner(new File(logFile), "TIS-620");
			while (s.hasNext()) {
				Employee.readLogRow(s.nextLine());
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static void readLogRow(String logRow) {
		String[] columns = new String[5];
		String[] splittedRow = logRow.split("\\|");
		for (int i = 0; i < columns.length; i++)
			if (i < splittedRow.length)
				columns[i] = splittedRow[i];
			else
				columns[i] = "";
		Employee emp = Employee.getOrCreateEmployee(columns[0]);
		emp.timestamps.add(new Timestamp(columns, emp));
	}

	protected Timestamp[] getTimestamps() {
		return timestamps.toArray(new Timestamp[0]);
	}

}

class Timestamp implements Comparable<Timestamp> {

	private static TreeMap<Calendar, TreeSet<Timestamp>> timestamps = new TreeMap<Calendar, TreeSet<Timestamp>>();

	private static final String DEFAULT_START_DATE = "1/1/2000", DEFAULT_START_TIME = "8:00",
			DEFAULT_END_DATE = "1/1/2000", DEFAULT_END_TIME = "17:00";

	private static final double WAGE_PER_MINUTE = 295.00 / 8 / 60;

	private static final double NORMAL_WAGE_DAY_FACTORS[] = new double[] { 1.5, 1, 1, 1, 1, 1, 1.5 };// 0:SUNDAY,
																										// 1:MONDAY,
																										// ...,
																										// 6:SATURDAY
	private static final double OT_WAGE_DAY_FACTORS[] = new double[] { 2, 1.5, 1.5, 1.5, 1.5, 1.5, 2 };// 0:SUNDAY,
																										// 1:MONDAY,
																										// ...,
																										// 6:SATURDAY

	private static final int NORMAL_START_HOUR = 8, NORMAL_START_MINUTE = 0;
	private static final int NORMAL_END_HOUR = 17, NORMAL_END_MINUTE = 0;
	private static final int OT_START_HOUR = 17, OT_START_MINUTE = 30;
	private static final int LATE_PERMIT_MINUTE = 5;
	private static final int LUNCH_LENGTH_IN_MINUTE = 60;

	private Calendar start, end;

	protected Employee employee;

	private double wage;
	private double numHours;

	protected Timestamp(String[] logColumns, Employee emp) {
		employee = emp;
		start = Calendar.getInstance();
		end = Calendar.getInstance();
		start.clear();
		end.clear();
		String[] startDate = (logColumns[1].length() > 0 ? logColumns[1] : DEFAULT_START_DATE).split("/");
		String[] startTime = (logColumns[2].length() > 0 ? logColumns[2] : DEFAULT_START_TIME).split(":");
		String[] endDate = (logColumns[3].length() > 0 ? logColumns[3] : DEFAULT_END_DATE).split("/");
		String[] endTime = (logColumns[4].length() > 0 ? logColumns[4] : DEFAULT_END_TIME).split(":");
		start.set(Integer.parseInt(startDate[2]), Integer.parseInt(startDate[1]) - 1, Integer.parseInt(startDate[0]),
				Integer.parseInt(startTime[0]), Integer.parseInt(startTime[1]));
		end.set(Integer.parseInt(endDate[2]), Integer.parseInt(endDate[1]) - 1, Integer.parseInt(endDate[0]),
				Integer.parseInt(endTime[0]), Integer.parseInt(endTime[1]));
		calculateWage();
		// System.out.println(
		// logColumns[2] + " - " + logColumns[4] + " = " + ((int) (wage * 100))
		// / 100.0 + " : " + numHours);
		Calendar key = Calendar.getInstance();
		key.clear();
		key.set(Integer.parseInt(startDate[2]), Integer.parseInt(startDate[1]) - 1, Integer.parseInt(startDate[0]));
		if (!timestamps.containsKey(key)) {
			timestamps.put(key, new TreeSet<Timestamp>());
		}
		timestamps.get(key).add(this);
	}

	private void calculateWage() {
		wage = 0.0;
		numHours = 0.0;
		// temp variable for calculation
		Calendar time1 = Calendar.getInstance();
		Calendar time2 = Calendar.getInstance();
		Calendar time3 = Calendar.getInstance();
		time1.clear();
		time2.clear();
		time3.clear();
		// employee start time --> employee with late permit start time
		time1.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DATE),
				start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE));
		// employee end time
		time2.set(end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DATE),
				end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE));
		// rule normal start time
		time3.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DATE), NORMAL_START_HOUR,
				NORMAL_START_MINUTE);
		if (time1.compareTo(time3) < 0
				|| (time1.getTimeInMillis() - time3.getTimeInMillis()) / 1000 / 60 <= LATE_PERMIT_MINUTE) {
			time1 = (Calendar) time3.clone(); // Adjust EMPLOYEE start time to
												// RULE start time if
			// he is not too late.
		}
		// rule normal end time --> employee normal end time
		time3.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DATE), NORMAL_END_HOUR,
				NORMAL_END_MINUTE);
		if (time2.compareTo(time3) < 0) {
			time3 = (Calendar) time2.clone(); // Adjust time3 to EMPLOYEE end
												// time if
			// he leaves before RULE end time
		}
		double hour = Math.max(0,
				(time3.getTimeInMillis() - time1.getTimeInMillis()) / 1000 / 60 - LUNCH_LENGTH_IN_MINUTE);
		wage += hour * WAGE_PER_MINUTE * NORMAL_WAGE_DAY_FACTORS[time1.get(Calendar.DAY_OF_WEEK) - 1];
		numHours += hour;
		// employee start time --> employee OT start time
		time1.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DATE),
				start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE));
		// rule OT start time
		time3.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DATE), OT_START_HOUR,
				OT_START_MINUTE);
		if (time1.compareTo(time3) < 0) {
			time1 = (Calendar) time3.clone(); // Adjust EMPLOYEE OT start time
			// to RULE OT start time if he starts OT too early
		}
		hour = Math.max(0, (time2.getTimeInMillis() - time1.getTimeInMillis()) / 1000 / 60);
		wage += hour * WAGE_PER_MINUTE * OT_WAGE_DAY_FACTORS[time1.get(Calendar.DAY_OF_WEEK) - 1];
		numHours += hour;
		numHours /= 60;
	}

	@Override
	public int compareTo(Timestamp that) {
		if (this.employee.name.compareTo(that.employee.name) != 0)
			return this.employee.name.compareTo(that.employee.name);
		return this.start.compareTo(that.start);
	}

	protected static Calendar[] getAllDates() {
		return timestamps.keySet().toArray(new Calendar[0]);
	}

	protected static Timestamp[] getTimestampsOf(Calendar date) {
		return timestamps.get(date).toArray(new Timestamp[0]);
	}

	public static String getFormatedDate(Calendar c) {
		return c.get(Calendar.DATE) + "/" + (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.YEAR);
	}

	public double getWage() {
		return wage;
	}

	public double getNumHours() {
		return numHours;
	}

	public Calendar getStart() {
		return start;
	}

	public Calendar getEnd() {
		return end;
	}
}