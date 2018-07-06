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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.wokesolutions.ignes.api.Profile;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.PermissionMapper;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

@Priority(3)
public class PermissionControlFilter implements Filter {

	private final static Logger LOG = Logger.getLogger(PermissionControlFilter.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Log.FILTER_VERIFYING);

		if(!(req instanceof HttpServletRequest)) {
			changeResp(resp, Log.NOT_HTTP_REQUEST);
			return;
		}

		HttpServletRequest request = (HttpServletRequest) req;

		String url = request.getRequestURL().toString();

		LOG.info(url);
		
		List<String> permissions = PermissionMapper.getPermissions(url);

		if(permissions.get(0).equals(UserLevel.GUEST)) {
			LOG.info(Log.GUEST_REQUEST);
			chain.doFilter(req, resp);
			return;
		}

		String token = ((HttpServletRequest) req).getHeader(CustomHeader.AUTHORIZATION);

		String username;
		try {
			username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
		} catch(Exception e) {
			changeResp(resp, Log.INVALID_TOKEN);
			return;
		}

		Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);

		if(token == null) {
			changeResp(resp, Log.INVALID_TOKEN);
			return;
		}

		Key userKey = KeyFactory.createKey(DSUtils.USER, username);
		Entity user;
		try {
			user = datastore.get(userKey);
		} catch (EntityNotFoundException e) {
			changeResp(resp, Log.USER_NOT_FOUND);
			return;
		}

		String userlevel = user.getProperty(DSUtils.USER_LEVEL).toString();
		
		if(permissions.get(0).equals(PermissionMapper.FORBIDDEN))
			for(int i = 1; i < permissions.size(); i++) {
				String permission = permissions.get(i);
				LOG.info(Integer.toString(i) + " " + permission);
				if(permission.equals(UserLevel.GUEST)) {
					LOG.info(Log.GUEST_REQUEST);
					chain.doFilter(req, resp);
					return;
				}
				
				if(userlevel.equals(permission)) {
					changeResp(resp, Log.INVALID_TOKEN);
					return;
				}
			}

		LOG.info(Log.NOT_GUEST_REQUEST);

		try {
			verifyWith(token, algorithm, username);
		} catch (Exception e) {
			changeResp(resp, Log.INVALID_TOKEN_ITSELF);
			return;
		}
		
		if(!permissions.contains(userlevel)) {
			changeResp(resp, Log.INVALID_TOKEN);
			return;
		}

		if(userlevel.equals(UserLevel.ORG))
			if(!user.getProperty(DSUtils.USER_ACTIVATION)
					.toString().equals(Profile.ACTIVATED)) {
				changeResp(resp, Log.ORG_NOT_CONFIRMED);
				return;
			}

		Query query = new Query(DSUtils.TOKEN);
		Query.Filter filter = new Query.FilterPredicate(DSUtils.TOKEN_USER,
				FilterOperator.EQUAL, userKey);
		query.setFilter(filter);

		query.addProjection(new PropertyProjection(DSUtils.TOKEN_DEVICE, String.class))
		.addProjection(new PropertyProjection(DSUtils.TOKEN_STRING, String.class));

		List<Entity> allTokens = datastore.prepare(query)
				.asList(FetchOptions.Builder.withDefaults());

		String deviceid = request.getAttribute(CustomHeader.DEVICE_ID_ATT).toString();

		for(Entity tokenE : allTokens) {
			if(tokenE.getProperty(DSUtils.TOKEN_STRING).equals(token) &&
					tokenE.getProperty(DSUtils.TOKEN_DEVICE).equals(deviceid)) {

				LOG.info(Log.PERMISSION_GRANTED);
				
				req.setAttribute(CustomHeader.USERNAME_ATT, username);
				chain.doFilter(req, resp);
				return;
			}
		}

		changeResp(resp, Log.INVALID_TOKEN);
		return;
	}

	@Override
	public void destroy() {}

	private void verifyWith(String token, Algorithm algorithm, String username)
			throws Exception {
		JWTVerifier verifier = JWT.require(algorithm)
				.withIssuer(JWTUtils.ISSUER)
				.withClaim(JWTUtils.USERNAME, username)
				.build();

		verifier.verify(token);
	}

	private void changeResp(ServletResponse resp, String toLog) throws IOException {
		LOG.info(toLog);
		((HttpServletResponse) resp).setHeader("Content-Type",
				CustomHeader.JSON_CHARSET_UTF8);
		((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
		resp.getWriter().println(toLog);
		return;
	}
}
