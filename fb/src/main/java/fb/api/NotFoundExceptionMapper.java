package fb.api;

import org.glassfish.jersey.server.ParamException;

import fb.util.Strings;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<Throwable> {

	public Response toResponse(Throwable e) {
		if (e instanceof NotFoundException || e instanceof ParamException) {
			return Response
				.status(Response.Status.NOT_FOUND)
				.entity(Strings.getFile("emptygeneric.html", null)
					.replace("$EXTRA", "<h1>404 not found</h1><p><a href=/>Go home</a></p>"))
				.build();
		}
		
		return Response
			.status(Response.Status.INTERNAL_SERVER_ERROR)
			.entity(MyErrorPageGenerator.generateErrorPage(e, null, null, null, null))
			.build();
	}
}
