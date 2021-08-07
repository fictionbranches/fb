package fb.api;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import fb.util.Strings;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

	public Response toResponse(NotFoundException e) {
		return Response
				.status(Response.Status.NOT_FOUND)
				.entity(Strings.getFile("emptygeneric.html", null)
						.replace("$EXTRA", "<h1>404 not found</h1><p><a href=/>Go home</a></p>"))
				.build();
	}
}
