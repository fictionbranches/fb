package fb.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.DB.DBException;
import fb.util.Strings;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

public class ExceptionMappers {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	@Provider
	public static class WebGeneric implements ExceptionMapper<WebApplicationException> {
		
		public Response toResponse(WebApplicationException e) {
			final int status = e.getResponse().getStatus();
			final String message = e.getMessage();
			
			LOGGER.warn("***** Web error with status " + status + " :: " + message + " *********", e);
			
			return switch(status) {
				case 404 -> Response
					.status(Response.Status.NOT_FOUND)
					.entity(Strings.getFile("emptygeneric.html", null)
							.replace("$EXTRA", "<h3>404 not found</h3><p><a href=/>Go home</a></p>"))
					.build();
				default -> Response
					.status(status)
					.entity(Strings.getFile("emptygeneric.html", null)
							.replace("$EXTRA", String.format("<h3>%d: %s</h3><p><a href=/>Go home</a></p>", status, message)))
					.build();
			};
		}
	}
	
	@Provider
	public static class DB implements ExceptionMapper<DBException> {

		public Response toResponse(DBException e) {
			final int status = 500;
			final String message = e.getMessage();
			
			LOGGER.error("***** DBException ::" + e, e);
			
			return Response
					.status(status)
					.entity(Strings.getFile("emptygeneric.html", null)
							.replace("$EXTRA", String.format("<h3>%d: %s</h3><p><a href=/>Go home</a></p>", status, message)))
					.build();
		}
	}
	
	@Provider
	public static class Generic implements ExceptionMapper<Exception> {

		public Response toResponse(Exception e) {
			final int status = 500;
			final String message = e.getMessage();
			
			LOGGER.warn("***** Generic error with status " + status + " :: " + message + " *********", e);
			
			return switch(status) {
				case 404 -> Response
					.status(Response.Status.NOT_FOUND)
					.entity(Strings.getFile("emptygeneric.html", null)
							.replace("$EXTRA", "<h3>404 not found</h3><p><a href=/>Go home</a></p>"))
					.build();
				default -> Response
					.status(status)
					.entity(Strings.getFile("emptygeneric.html", null)
							.replace("$EXTRA", String.format("<h3>%d: %s</h3><p><a href=/>Go home</a></p>", status, message)))
					.build();
			};
		}
	}
}
