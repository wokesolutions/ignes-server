package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.wokesolutions.ignes.api.Email;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Log;

@Priority(4)
public class DeviceControlFilter implements Filter {

	private static final Logger LOG = Logger.getLogger(DeviceControlFilter.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String deviceid = request.getAttribute(CustomHeader.DEVICE_ID_ATT).toString();
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Query deviceQuery = new Query(DSUtils.DEVICE);
		Query.Filter userFilter = new Query.FilterPredicate(DSUtils.DEVICE_USER,
				FilterOperator.EQUAL, username);
		deviceQuery.setFilter(userFilter);

		List<Entity> devices = datastore.prepare(deviceQuery)
				.asList(FetchOptions.Builder.withDefaults());

		Entity existingDevice = null;

		for(Entity device : devices) {
			if(device.getProperty(DSUtils.DEVICE_ID).toString().equals(deviceid)) {
				existingDevice = device;
				break;
			}
		}
		
		Entity user;
		try {
			user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));
		} catch(EntityNotFoundException e) {
			changeResp(response, Log.USER_NOT_FOUND);
			return;
		}
		
		String email = user.getProperty(DSUtils.USER_EMAIL).toString();

		if(existingDevice == null) {
			String app = request.getAttribute(CustomHeader.DEVICE_APP_ATT).toString();
			
			existingDevice = new Entity(DSUtils.DEVICE, deviceid);
			existingDevice.setUnindexedProperty(DSUtils.DEVICE_COUNT, 1L);
			existingDevice.setProperty(DSUtils.DEVICE_USER, username);
			existingDevice.setProperty(DSUtils.DEVICE_APP, app);
			
			Email.sendNewDeviceMessage(email,
					request.getAttribute(CustomHeader.DEVICE_INFO_ATT).toString());
		} else
			existingDevice.setProperty(DSUtils.DEVICE_COUNT,
				(long) existingDevice.getProperty(DSUtils.DEVICE_COUNT) + 1L);

		datastore.put(existingDevice);
		
		chain.doFilter(request, response);
	}

	@Override
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
