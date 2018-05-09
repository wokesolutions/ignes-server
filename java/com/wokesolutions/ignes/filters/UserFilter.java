package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.*;
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

public class UserFilter implements Filter {

	public static final Logger LOG = Logger.getLogger(UserFilter.class.getName());

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {
		
		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());
		
		try {
			Algorithm algorithm = Algorithm.HMAC256(JWTUtils.SECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.build();

			String token = ((HttpServletRequest) req).getHeader(JWTUtils.AUTHORIZATION);
			
			if(token == null)
				throw new Exception();
			
			verifier.verify(token);
			
			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			req.setAttribute(CustomHeader.USERNAME, username);
			req.setAttribute(CustomHeader.LEVEL, JWTUtils.LEVEL1);

			chain.doFilter(req, resp);
		} catch (Exception e){
			byte[] responseToSend = Message.INVALID_TOKEN.getBytes();
			((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getOutputStream().write(responseToSend);
			return;
		}
	}

	public void destroy() {}  

}  