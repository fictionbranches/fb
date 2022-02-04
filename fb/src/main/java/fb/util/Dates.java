package fb.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Dates {
	
	private Dates() {}
	
	private static final ThreadLocal<DateFormat> outputDate = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
	/**
	 * "yyyy-MM-dd HH:mm:ss"
	 * @param date
	 * @return
	 */
	public static String outputDateFormat2(Date date) {
		return "<time class=\"output-timestamp\" datetime=\"" + outputDate.get().format(date) + "\" data-unixtimemillis=" + date.getTime() + ">" + outputDate.get().format(date) + "</time>";
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
		return "<time class=\"simple-timestamp\" datetime=\"" + outputDate.get().format(date) + "\" data-unixtimemillis=" + date.getTime() + ">" + simpleDate.get().format(date) + "</time>";

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
