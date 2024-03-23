package fb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class GoogleRECAPTCHA {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	private GoogleRECAPTCHA() {}
	
	public static class GoogleCheckException extends Exception {
		/****/ static final long serialVersionUID = -543548533804756724L;
		public GoogleCheckException(String message) {
			super(message);
		}
		public GoogleCheckException(Exception e) {
			super(e);
		}
	}
	
	public static class JsonCaptchaResponse {
		public boolean getSuccess() {
			return success;
		}
		public void setSuccess(boolean success) {
			this.success = success;
		}
		public Date getChannelge_ts() {
			return channelge_ts;
		}
		public void setChannelge_ts(Date channelge_ts) {
			this.channelge_ts = channelge_ts;
		}
		public String getHostname() {
			return hostname;
		}
		public void setHostname(String hostname) {
			this.hostname = hostname;
		}
		private boolean success;
		private Date channelge_ts;
		private String hostname;
	}
	
	/**
	 * Wraps URLEncoder.encode(String, StandardCharsets.UTF_8)
	 * @param s
	 * @return
	 */
	private static String encodeURLComponent(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
	
	/**
	 * Wraps String.getBytes("UTF-8") to avoid the UnsupportedEncodingException
	 * UTF-8 will never cause UnsupportedEncodingException
	 * @param s
	 * @return
	 */
	private static byte[] getBytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
	
	/**
	 * Check a recaptcha response token
	 * @return "true" if user passed, "false" if user failed, anything else indicated an error
	 */
	public static boolean checkGoogle(String google) throws GoogleCheckException {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("secret", Strings.getRECAPTCHA_SECRET());
		params.put("response", google);
		byte[] postDataBytes = getBytes(params.entrySet().stream()
				.map(param->encodeURLComponent(param.getKey()) + '=' + encodeURLComponent(String.valueOf(param.getValue())))
				.collect(Collectors.joining("&")));
		HttpURLConnection conn;
		try {
			conn = (HttpURLConnection) URI.create("https://www.google.com/recaptcha/api/siteverify").toURL().openConnection();
		} catch (IOException e) {
			LOGGER.error("IOException1? Really? wtf ", e);
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException1");
		}
		try {
			conn.setRequestMethod("POST");
		} catch (ProtocolException e) {
			LOGGER.error("ProtocolException? Really? wtf ", e);
			throw new GoogleCheckException("Tell Phoenix you got recaptcha ProtocolException");
		}
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
		conn.setDoOutput(true);
		try {
			conn.getOutputStream().write(postDataBytes);
		} catch (IOException e) {
			LOGGER.error("IOException2? Really? wtf ", e);
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException2");
		}
		Reader in;
		try {
			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOGGER.error("IOException3? Really? wtf ", e);
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException3");
		}

		StringBuilder json = new StringBuilder();
		try {
			for (int c; (c = in.read()) >= 0;)
				json.append((char) c);
		} catch (IOException e) {
			LOGGER.error("IOException3? Really? wtf ", e);
			throw new GoogleCheckException("Tell Phoenix you got a IOException4 when adding an episode");
		}
		JsonCaptchaResponse response = new Gson().fromJson(json.toString(), JsonCaptchaResponse.class);
		return response.getSuccess();
	}

	
}
