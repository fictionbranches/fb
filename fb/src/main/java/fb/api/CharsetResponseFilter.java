package fb.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;

/**
 * This filter ensures that all responses (which don't already have a defined charset (none of ours do)) use charset=utf-8
 */
public class CharsetResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        MediaType type = response.getMediaType();
        if (type != null) {
            String contentType = type.toString();
            if (!contentType.contains("charset")) {
                contentType = contentType + ";charset=utf-8";
                response.getHeaders().putSingle("Content-Type", contentType);
            }
        }
    }
}


