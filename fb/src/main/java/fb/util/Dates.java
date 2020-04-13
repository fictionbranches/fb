package fb.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Dates {
	
	private Dates() {}
	
	private static final ThreadLocal<DateFormat> outputDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
	/**
	 * "yyyy-MM-dd HH:mm:ss"
	 * @param date
	 * @return
	 */
	public static String outputDateFormat2(Date date) {
		return "<span class=\"output-timestamp\" data-unixtimemillis=" + date.getTime() + ">" + outputDate.get().format(date) + "</span>";
	}
	
	private static final ThreadLocal<DateFormat> simpleDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd"));
	/**
	 * "yyyy-MM-dd"
	 * @param date
	 * @return
	 */
	public static String simpleDateFormat2(Date date) {
		if (date == null)
			return "since the beforefore times";
		return "<span class=\"simple-timestamp\" data-unixtimemillis=" + date.getTime() + ">" + simpleDate.get().format(date) + "</span>";

	}
	
	/**
	 * "yyyy-MM-dd"
	 * @param date
	 * @return
	 */
	public static String plainDate(Date date) {
		if (date == null)
			return "since the beforefore times";
		return simpleDate.get().format(date);

	}
	
	private static final ThreadLocal<DateFormat> completeSimpleDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyyMMddHHmmss"));
	/**
	 * "yyyyMMddHHmmss" WITHOUT JAVASCRIPT HELPER
	 * @param date
	 * @return
	 */
	public static String completeSimpleDateFormat2(Date date) {
		return completeSimpleDate.get().format(date);
	}
}
