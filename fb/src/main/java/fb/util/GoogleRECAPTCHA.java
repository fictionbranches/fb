package fb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

public class GoogleRECAPTCHA {
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
	 * Check a recaptcha response token
	 * @return "true" if user passed, "false" if user failed, anything else indicated an error
	 */
	public static boolean checkGoogle(String google) throws GoogleCheckException {
		URL url;
		try {
			url = new URL("https://www.google.com/recaptcha/api/siteverify");
		} catch (MalformedURLException e1) {
			Strings.log("MalformedURLException? Really? wtf " + e1.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha MalformedURLException");
		}
		Map<String, String> params = new LinkedHashMap<>();
		params.put("secret", Strings.getRECAPTCHA_SECRET());
		params.put("response", google);
		StringBuilder postData = new StringBuilder();
		byte[] postDataBytes;
		try {
			for (Map.Entry<String, String> param : params.entrySet()) {
				if (postData.length() != 0)
					postData.append('&');
				postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
				postData.append('=');
				postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
			}
			postDataBytes = postData.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			Strings.log("UnsupportedEncodingException? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha UnsupportedEncodingException");
		}
		HttpURLConnection conn;
		try {
			conn = (HttpURLConnection) url.openConnection();
		} catch (IOException e) {
			Strings.log("IOException1? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException1");
		}
		try {
			conn.setRequestMethod("POST");
		} catch (ProtocolException e) {
			Strings.log("ProtocolException? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha ProtocolException");
		}
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
		conn.setDoOutput(true);
		try {
			conn.getOutputStream().write(postDataBytes);
		} catch (IOException e) {
			Strings.log("IOException2? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException2");
		}
		Reader in;
		try {
			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			Strings.log("UnsupportedEncodingException2? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha UnsupportedEncodingException2");
		} catch (IOException e) {
			Strings.log("IOException3? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got recaptcha IOException3");
		}

		StringBuilder json = new StringBuilder();
		try {
			for (int c; (c = in.read()) >= 0;)
				json.append((char) c);
		} catch (IOException e) {
			Strings.log("IOException3? Really? wtf " + e.getMessage());
			throw new GoogleCheckException("Tell Phoenix you got a IOException4 when adding an episode");
		}
		JsonCaptchaResponse response = new Gson().fromJson(json.toString(), JsonCaptchaResponse.class);
		return response.getSuccess();
	}

	
}
