package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

public class UserFilter implements Filter {

	public static final Logger LOG = Logger.getLogger(UserFilter.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());

		Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);

		JWTVerifier verifier;
		String token = ((HttpServletRequest) req).getHeader(CustomHeader.AUTHORIZATION);

		try {
			verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.build();

			if(token == null)
				throw new Exception();

			verifier.verify(token);
		} catch(Exception e) {
			try {
				verifier = JWT.require(algorithm)
						.withIssuer(JWTUtils.ISSUER)
						.withClaim(JWTUtils.ORG, UserLevel.ORG)
						.build();

				if(token == null)
					throw new Exception();

				LOG.info(token);

				verifier.verify(token);
			} catch (Exception e2) {
				LOG.info(Message.INVALID_TOKEN);
				((HttpServletResponse) resp).setHeader("Content-Type", CustomHeader.JSON_CHARSET_UTF8);
				((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
				resp.getWriter().println(Message.INVALID_TOKEN);
				return;
			}
		}

		String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

		Query query = new Query(DSUtils.TOKEN)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, username));

		List<Entity> allTokens = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		boolean tokenExists = false;
		for(Entity oneToken : allTokens)
			if(oneToken.getProperty(DSUtils.TOKEN_STRING).equals(token)) {
				tokenExists = true;
				break;
			}

		if(!tokenExists) {
			query = new Query(DSUtils.TOKEN)
					.setAncestor(KeyFactory.createKey(DSUtils.ORG, username));

			allTokens = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

			tokenExists = false;
			for(Entity oneToken : allTokens)
				if(oneToken.getProperty(DSUtils.TOKEN_STRING).equals(token)) {
					tokenExists = true;
					break;
				}

			if(!tokenExists) {
				LOG.info(Message.INVALID_TOKEN);
				((HttpServletResponse) resp).setHeader("Content-Type", CustomHeader.JSON_CHARSET_UTF8);
				((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
				resp.getWriter().println(Message.INVALID_TOKEN);
				return;
			}

			req.setAttribute(CustomHeader.LEVEL_ATT, UserLevel.ORG);
			req.setAttribute(CustomHeader.USERNAME_ATT, username);
			
			chain.doFilter(req, resp);
		}

		req.setAttribute(CustomHeader.LEVEL_ATT, UserLevel.LEVEL1);
		req.setAttribute(CustomHeader.USERNAME_ATT, username);

		chain.doFilter(req, resp);
	}

	public void destroy() {}  

}  