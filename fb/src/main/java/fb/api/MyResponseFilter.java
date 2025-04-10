package fb.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.Accounts;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MyResponseFilter implements ContainerResponseFilter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
		final var name = "fbtoken";
		var oldCookie = requestContext.getCookies().get(name);
		var session = Accounts.getUserSessionFromCookie(oldCookie);
		if (session != null && System.currentTimeMillis() - session.firstIssued > Duration.ofDays(5).toMillis()) {
			var c = Calendar.getInstance();
			c.setTimeZone(TimeZone.getTimeZone("UTC"));
			c.set(1970, 1, 1);
			var expire = Date.from(c.toInstant());
			responseContext.getHeaders().add("Set-Cookie", GetStuff.newCookie(oldCookie.getName(), oldCookie.getValue(), requestContext.getUriInfo(), expire, true));
			
			var newToken = Accounts.newTokenForUser(session.userID);
			responseContext.getHeaders().add("Set-Cookie", GetStuff.newCookie(name, newToken, requestContext.getUriInfo(), Duration.ofDays(14), true));
			
			LOGGER.info("Rotating token for %s".formatted(session.userID));
		}
	}
}
