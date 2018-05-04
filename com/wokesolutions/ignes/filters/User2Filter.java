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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.UserLevel;

public class User2Filter implements Filter {

	public static final Logger LOG = Logger.getLogger(User2Filter.class.getName());

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());

		try {
			Algorithm algorithm = Algorithm.HMAC256(JWTUtils.SECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.build();

			String token = ((HttpServletRequest) req).getHeader(JWTUtils.AUTHORIZATION);
			verifier.verify(token);
			
			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			req.setAttribute(CustomHeader.USERNAME, username);
			req.setAttribute(CustomHeader.LEVEL, JWTUtils.LEVEL3);

			chain.doFilter(req, resp);
		} catch (JWTVerificationException e){
			byte[] responseToSend = Message.INVALID_TOKEN.getBytes();
			((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getOutputStream().write(responseToSend);
			return;
		}
	}

	public void destroy() {}  
}
