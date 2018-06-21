package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.wokesolutions.ignes.util.Cache;
import com.wokesolutions.ignes.util.Message;

public class RequestControlFilter implements Filter {
	public static final Logger LOG = Logger.getLogger(RequestControlFilter.class.getName());
	MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final String DELETE = "DELETE";

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		HttpServletRequest newreq = ((HttpServletRequest)req);

		if(req instanceof HttpServletRequest) {
			String url = newreq.getRequestURL().toString();
			String compUrl;
			String id = null;

			LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + " " + url);
			
			String method = newreq.getMethod();

			if(method.equalsIgnoreCase(GET) || method.equalsIgnoreCase(DELETE)) {
				String queryString = newreq.getQueryString();
				if(queryString == null)
					compUrl = url + "?" + queryString;
				else
					compUrl = url;

				id = Cache.GETId(compUrl, newreq);
			} else if(method.equals(POST)) {
				compUrl = url;
				id = Cache.POSTId(compUrl, newreq);
			} else
				chain.doFilter(req, resp);

			if(id == null) {
				LOG.info(Message.REQUEST_ID_ERROR);
				((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
				((HttpServletResponse) resp).setStatus(Status.INTERNAL_SERVER_ERROR.getStatusCode());
				resp.getWriter().println(Message.REQUEST_ID_ERROR);
				return;
			}
			
			LOG.info("-------ID------> " + id);
			
			String ip = newreq.getRemoteAddr();
			
			if(cache.get(ip) == null)
				cache.put(ip, 1L, Expiration.byDeltaSeconds(120));
			else
				cache.increment(ip, 1L);
			
			if((long) cache.get(ip) > 5L) {
				LOG.info(Message.TOO_MANY_REQUESTS);
				((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
				((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
				resp.getWriter().println(Message.TOO_MANY_REQUESTS);
				return;
			}
			
			if(cache.get(id) == null)
				cache.put(id, 1L, Expiration.byDeltaSeconds(15));
			else
				cache.increment(id, 1L);
			
			if((long) cache.get(id) > 5L) {
				LOG.info(Message.TOO_MANY_REQUESTS);
				((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
				((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
				resp.getWriter().println(Message.TOO_MANY_REQUESTS);
				return;
			} else {
				LOG.info(Message.REQUEST_IS_GOOD);
				chain.doFilter(req, resp);
			}
			
			cache.increment(newreq.getRemoteAddr(), 1L, 0L); //TODO
		} else {
			LOG.info(Message.NOT_HTTP_REQUEST);
			((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getWriter().println(Message.NOT_HTTP_REQUEST);
			return;
		}
	}

	public void destroy() {}
}
