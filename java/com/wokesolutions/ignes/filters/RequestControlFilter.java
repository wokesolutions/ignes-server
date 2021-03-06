package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Priority;
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
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.Log;

@Priority(2)
public class RequestControlFilter implements Filter {

	public static final Logger LOG = Logger.getLogger(RequestControlFilter.class.getName());
	MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final String DELETE = "DELETE";

	public static final String QUEUENAME = "X-AppEngine-QueueName";
	public static final String CRON = "__cron";

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Log.FILTER_VERIFYING);

		if(!(req instanceof HttpServletRequest)) {
			changeResp(resp, Log.NOT_HTTP_REQUEST);
			return;
		}

		HttpServletRequest newreq = (HttpServletRequest) req;

		Object cron = newreq.getHeader(QUEUENAME);

		if(cron != null && cron.toString().equals(CRON)) {
			req.setAttribute(CustomHeader.CRON, true);
			chain.doFilter(req, resp);
			LOG.info(Log.CRON_REQUEST);
			return;
		}

		String deviceid = newreq.getHeader(CustomHeader.DEVICE_ID);
		String deviceapp = newreq.getHeader(CustomHeader.DEVICE_APP);
		String deviceinfo = newreq.getHeader(CustomHeader.DEVICE_INFO);

		if(deviceid == null || deviceapp == null || deviceinfo == null) {
			changeResp(resp, Log.MISSING_DEVICE_HEADER);
			return;
		}

		String url = newreq.getRequestURL().toString();
		String method = newreq.getMethod();

		if(!(method.equals(DELETE) || method.equals(POST) || method.equals(GET))) {
			chain.doFilter(req, resp);
			return;
		}

		String query = newreq.getQueryString();
		if(query != null)
			url += "?" + query;

		String id = deviceid + " " + url;

		if(cache.get(deviceid) == null)
			cache.put(deviceid, 1L, Expiration.byDeltaSeconds(15));
		else
			cache.increment(deviceid, 1L);

		if((long) cache.get(deviceid) > 100L) {
			changeResp(resp, Log.TOO_MANY_REQUESTS);
			return;
		}

		if(cache.get(id) == null)
			cache.put(id, 1L, Expiration.byDeltaSeconds(15));
		else
			cache.increment(id, 1L);

		if((long) cache.get(id) > 10L) {
			changeResp(resp, Log.TOO_MANY_REQUESTS);
			return;
		}

		req.setAttribute(CustomHeader.DEVICE_ID_ATT, deviceid);
		req.setAttribute(CustomHeader.DEVICE_APP_ATT, deviceapp);
		req.setAttribute(CustomHeader.DEVICE_INFO_ATT, deviceinfo);

		LOG.info(deviceid);

		LOG.info(Log.REQUEST_IS_GOOD);
		chain.doFilter(req, resp);
	}

	public void destroy() {}

	private void changeResp(ServletResponse resp, String toLog) throws IOException {
		LOG.info(toLog);
		((HttpServletResponse) resp).setHeader("Content-Type",
				CustomHeader.JSON_CHARSET_UTF8);
		((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
		resp.getWriter().println(toLog);
		return;
	}
}
