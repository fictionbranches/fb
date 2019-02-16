package fb.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Dates {
	
	private Dates() {}
	
	private static final ThreadLocal<DateFormat> outputDate = ThreadLocal.withInitial(()->new SimpleDateFormat("EEE, MMM d yyyy HH:mm:ss"));

	/**
	 * "EEE, MMM d yyyy HH:mm:ss"
	 * @param date
	 * @return
	 */
	public static String outputDateFormat(Date date) {
		return outputDate.get().format(date);
	}

	private static final ThreadLocal<DateFormat> simpleDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd"));

	/**
	 * "yyyy-MM-dd"
	 * @param date
	 * @return
	 */
	public static String simpleDateFormat(Date date) {
		if (date == null)
			return "since the beforefore times";
		return simpleDate.get().format(date);
	}
	
	private static final ThreadLocal<DateFormat> simpleDateTime = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"));
	
	/**
	 * "yyyy-MM-dd hh:mm:ss"
	 * @param date
	 * @return
	 */
	public static String simpleDateTimeFormat(Date date) {
		if (date == null)
			return "since the beforefore times";
		return simpleDateTime.get().format(date);
	}

	private static final ThreadLocal<DateFormat> completeSimpleDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyyMMddHHmmss"));

	/**
	 * "yyyyMMddHHmmss"
	 * @param date
	 * @return
	 */
	public static String completeSimpleDateFormat(Date date) {
		return completeSimpleDate.get().format(date);
	}
}
